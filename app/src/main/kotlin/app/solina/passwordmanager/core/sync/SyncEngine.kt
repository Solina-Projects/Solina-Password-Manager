package app.solina.passwordmanager.core.sync

import app.solina.passwordmanager.core.crypto.wipe
import app.solina.passwordmanager.core.storage.ItemStore
import app.solina.passwordmanager.core.storage.StoredHistory
import app.solina.passwordmanager.core.storage.StoredItem
import app.solina.passwordmanager.core.storage.VaultRepository

/** محل ذخیرهٔ دوردست (پوشه، WebDAV، ...). فقط بایت‌های مات را جابه‌جا می‌کند. */
interface SyncRemote {
    suspend fun list(): List<String>
    suspend fun read(name: String): ByteArray?
    suspend fun write(name: String, bytes: ByteArray)
    suspend fun delete(name: String)
}

/** نگهداری آخرین شمارهٔ Epoch دیده‌شده در حافظهٔ محلی برای تشخیص بازگشت به نسخهٔ قدیمی (Rollback). */
interface SyncEpochStore {
    fun lastSeenEpoch(): Long
    fun saveLastSeenEpoch(epoch: Long)
}

class InMemorySyncEpochStore : SyncEpochStore {
    private var epoch = 0L
    override fun lastSeenEpoch(): Long = epoch
    override fun saveLastSeenEpoch(epoch: Long) {
        if (epoch > this.epoch) this.epoch = epoch
    }
}

class SyncReport(
    val pulled: Int,
    val pushed: Int,
    val conflicts: Int,
    val rejected: Int,
    val removed: Int,
    val rollbackDetected: Boolean = false,
) {
    val changedLocal get() = pulled > 0 || conflicts > 0
}

/**
 * همگام‌سازی چنددستگاهی بدون سرور و بدون از دست رفتن داده.
 *
 * هر نسخه از هر ورودی یک فایل تغییرناپذیر است (id+rev+hash)، پس ابزارهای هم‌گام‌سازی فایل (Syncthing،
 * Drive، ...) هرگز روی هم بازنویسی نمی‌کنند. قاعدهٔ برنده (در همهٔ دستگاه‌ها یکسان و قطعی):
 * ویرایش بالاتر ← در برابر ویرایش یکسان، «زنده» بر «حذف‌شده» ← زمان جدیدتر ← هش بزرگ‌تر.
 * نسخهٔ بازنده هرگز دور ریخته نمی‌شود؛ در تاریخچهٔ رمزشدهٔ ورودی می‌ماند.
 * علاوه بر آن، فایل `manifest.slnm` با شمارهٔ Epoch یکنواخت و HMAC امضا می‌شود تا بازگشت
 * پوشهٔ همگام‌سازی به حالت قدیمی (Rollback) شناسایی شود.
 */
class SyncEngine(
    private val store: ItemStore,
    private val remote: SyncRemote,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epochStore: SyncEpochStore = InMemorySyncEpochStore(),
) {
    companion object {
        /** نگهداری Tombstone به مدت ۹۰ روز برای انتشار حذف به دستگاه‌های آفلاین. */
        const val TOMBSTONE_RETENTION_MILLIS = 90L * 24 * 3600_000L
    }

    private class Key(val rev: Long, val live: Int, val updatedAt: Long, val hash: String) : Comparable<Key> {
        override fun compareTo(other: Key): Int {
            rev.compareTo(other.rev).let { if (it != 0) return it }
            live.compareTo(other.live).let { if (it != 0) return it }
            updatedAt.compareTo(other.updatedAt).let { if (it != 0) return it }
            return hash.compareTo(other.hash)
        }
    }

    private fun keyOf(item: StoredItem, macKey: ByteArray): Key =
        Key(item.rev, if (item.deletedAt == null) 1 else 0, item.updatedAt, SyncRecord.contentHash(SyncRecord.encode(item, macKey)))

    suspend fun sync(vaultKey: ByteArray): SyncReport {
        val macKey = SyncRecord.macKey(vaultKey)
        val manifestKey = SyncManifest.macKey(vaultKey)
        try {
            var pulled = 0
            var conflicts = 0
            var rejected = 0
            var pushed = 0
            var removed = 0
            var rollbackDetected = false

            // ───── ۰) بررسی مانیفست امضاشده برای کشف Rollback ─────
            val lastEpoch = epochStore.lastSeenEpoch()
            var remoteEpoch = 0L
            val remoteManifestBytes = runCatching {
                remote.read(SyncManifest.FILE_NAME) ?: remote.read("${SyncManifest.FILE_NAME}.bin")
            }.getOrNull()
            if (remoteManifestBytes != null) {
                val manifest = SyncManifest.decode(remoteManifestBytes, manifestKey)
                if (manifest == null) {
                    rejected++
                } else {
                    remoteEpoch = manifest.epoch
                    if (manifest.epoch < lastEpoch) {
                        rollbackDetected = true
                    }
                }
            }

            // ───── ۱) دریافت ─────
            val rawList = remote.list()
            val remoteFiles = rawList.mapNotNull { n -> SyncRecord.parseName(n)?.let { n to it } }
            val localAll = store.all().associateBy { it.id }
            for ((id, files) in remoteFiles.groupBy { it.second.id }) {
                val l = localAll[id]
                val localName = l?.let { SyncRecord.fileName(it.id, it.rev, SyncRecord.encode(it, macKey)) }
                val candidates = files.filter { (name, p) ->
                    l == null || p.rev > l.rev || (p.rev == l.rev && name.removeSuffix(".bin") != localName)
                }.sortedWith(compareBy({ it.second.rev }, { it.second.hash8 }))

                for ((name, parsed) in candidates) {
                    val bytes = try { remote.read(name) } catch (e: Exception) { null }
                    val rec = bytes?.let { SyncRecord.decode(it, macKey) }
                    if (rec == null || rec.id != id || rec.rev != parsed.rev ||
                        SyncRecord.contentHash(bytes).take(8) != parsed.hash8
                    ) {
                        rejected++
                        continue
                    }
                    when (apply(rec, macKey)) {
                        Outcome.Adopted -> pulled++
                        Outcome.Conflict -> conflicts++
                        Outcome.Ignored -> {}
                    }
                }
            }

            // ───── ۲) ارسال ─────
            val present = remote.list().toSet()
            val local = store.all()
            val now = clock()
            for (item in local) {
                // Tombstoneهای بسیار قدیمی (> ۹۰ روز) دیگر روی پوشهٔ جدید دوباره ارسال نمی‌شوند.
                if (item.deletedAt != null && now - item.deletedAt > TOMBSTONE_RETENTION_MILLIS) continue
                val bytes = SyncRecord.encode(item, macKey)
                val name = SyncRecord.fileName(item.id, item.rev, bytes)
                if (name !in present && "$name.bin" !in present) {
                    remote.write(name, bytes)
                    pushed++
                }
            }

            // ───── ۳) پاک‌سازی نسخه‌های قدیمی‌تر و Tombstoneهای منقضی ─────
            val localById = local.associateBy { it.id }
            for (name in present) {
                val p = SyncRecord.parseName(name) ?: continue
                val cur = localById[p.id] ?: continue
                val expiredTombstone = cur.deletedAt != null && now - cur.deletedAt > TOMBSTONE_RETENTION_MILLIS
                if (p.rev < cur.rev || expiredTombstone) {
                    runCatching { remote.delete(name) }.onSuccess { removed++ }
                }
            }

            // ───── ۴) به‌روزرسانی مانیفست امضاشده با Epoch افزایشی ─────
            val shouldWriteManifest = pushed > 0 || removed > 0 || remoteEpoch == 0L || rollbackDetected
            val nextEpoch = if (shouldWriteManifest) maxOf(lastEpoch, remoteEpoch) + 1L else maxOf(lastEpoch, remoteEpoch)
            if (shouldWriteManifest && nextEpoch > 0L) {
                val manifestData = SyncManifestData(
                    epoch = nextEpoch,
                    revisions = local.associate { it.id to it.rev },
                )
                runCatching { remote.write(SyncManifest.FILE_NAME, SyncManifest.encode(manifestData, manifestKey)) }
            }
            if (nextEpoch > lastEpoch) {
                epochStore.saveLastSeenEpoch(nextEpoch)
            }

            return SyncReport(pulled, pushed, conflicts, rejected, removed, rollbackDetected)
        } finally {
            macKey.wipe()
            manifestKey.wipe()
        }
    }

    private enum class Outcome { Adopted, Conflict, Ignored }

    private suspend fun apply(rec: StoredItem, macKey: ByteArray): Outcome {
        val cur = store.get(rec.id)
        if (cur == null) {
            adopt(rec, null)
            return Outcome.Adopted
        }

        val remoteWins = keyOf(rec, macKey) > keyOf(cur, macKey)
        val bothLive = cur.deletedAt == null && rec.deletedAt == null
        val concurrent = bothLive && cur.rev == rec.rev
        val now = clock()

        return if (remoteWins) {
            val archive = if (bothLive) StoredHistory(cur.id, cur.rev, now, cur.wrappedItemKey, cur.blob) else null
            adopt(rec, archive)
            if (concurrent) Outcome.Conflict else Outcome.Adopted
        } else if (concurrent) {
            // بازندهٔ هم‌زمان: محتوایش را در تاریخچه نگه می‌داریم (یک‌بار؛ تکراری نمی‌شود).
            val known = store.history(cur.id).any { it.rev == rec.rev && it.blob.contentEquals(rec.blob) }
            if (known) Outcome.Ignored else {
                store.save(cur, StoredHistory(rec.id, rec.rev, now, rec.wrappedItemKey, rec.blob), VaultRepository.MAX_HISTORY)
                Outcome.Conflict
            }
        } else Outcome.Ignored
    }

    private suspend fun adopt(rec: StoredItem, archive: StoredHistory?) {
        if (rec.deletedAt != null) store.delete(rec.id, rec.deletedAt, rec.rev)
        else store.save(rec, archive, VaultRepository.MAX_HISTORY)
    }
}
