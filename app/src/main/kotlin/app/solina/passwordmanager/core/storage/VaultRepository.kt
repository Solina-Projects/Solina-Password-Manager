package app.solina.passwordmanager.core.storage

import app.solina.passwordmanager.core.crypto.SealedItem
import app.solina.passwordmanager.core.crypto.VaultCrypto
import app.solina.passwordmanager.core.crypto.wipe
import app.solina.passwordmanager.core.security.VaultSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ImportStats(val added: Int, val skipped: Int)

class VaultLockedException : IllegalStateException("vault is locked")

/**
 * منطق خزانه: رمزگذاری/رمزگشایی ورودی‌ها با Vault Key نشست، نگه‌داشتن فهرست در حافظه و تاریخچه.
 * فهرست رمزگشایی‌شده فقط در حافظه است و با [clear] (هنگام قفل) پاک می‌شود.
 */
class VaultRepository(
    private val store: ItemStore,
    private val session: VaultSession,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    /** تغییرات محلی را با همگام‌سازی سریالی می‌کند (برای جلوگیری از «به‌روزرسانی گم‌شده»). */
    val lock = Mutex()

    private val _localChanges = MutableStateFlow(0L)
    /** با هر افزودن/ویرایش/حذف/ایمپورت محلی بالا می‌رود؛ برای راه‌اندازی همگام‌سازی خودکار. */
    val localChanges: StateFlow<Long> = _localChanges.asStateFlow()

    private val _unreadable = MutableStateFlow(0)
    /** تعداد ردیف‌هایی که رمزگشایی نشدند (خراب یا دستکاری‌شده). */
    val unreadable: StateFlow<Int> = _unreadable.asStateFlow()

    private inline fun <T> withKey(block: (ByteArray) -> T): T {
        val key = session.keyCopy() ?: throw VaultLockedException()
        try {
            return block(key)
        } finally {
            key.wipe()
        }
    }

    suspend fun load() {
        val rows = store.all().filter { it.deletedAt == null }
        var bad = 0
        val list = ArrayList<VaultItem>(rows.size)
        withKey { key ->
            for (r in rows) {
                try {
                    val plain = VaultCrypto.openItem(key, r.id, r.rev, ItemPayload.SCHEMA, SealedItem(r.wrappedItemKey, r.blob))
                    try {
                        list.add(VaultItem(r.id, r.rev, ItemPayload.fromBytes(plain)))
                    } finally {
                        plain.wipe()
                    }
                } catch (e: Exception) {
                    bad++
                }
            }
        }
        _unreadable.value = bad
        _items.value = list.sortedBy { it.payload.title.lowercase() }
    }

    suspend fun add(payload: ItemPayload): VaultItem = lock.withLock {
        val now = clock()
        val id = newId()
        val p = payload.copy(createdAt = now, editedAt = now)
        val item = VaultItem(id, 1, p)
        withKey { key -> store.save(seal(key, item, now), null, MAX_HISTORY) }
        publish(item)
        _localChanges.value++
        item
    }

    /** اگر چیزی تغییر نکرده باشد چیزی ذخیره نمی‌شود. */
    suspend fun update(id: String, payload: ItemPayload): VaultItem = lock.withLock {
        val current = _items.value.firstOrNull { it.id == id } ?: error("item not found")
        val now = clock()
        val next = payload.copy(createdAt = current.payload.createdAt, editedAt = current.payload.editedAt)
        if (next == current.payload) return@withLock current
        val item = VaultItem(id, current.rev + 1, next.copy(editedAt = now))
        withKey { key ->
            val old = store.get(id) ?: error("item not found")
            val archived = StoredHistory(id, old.rev, now, old.wrappedItemKey, old.blob)
            store.save(seal(key, item, now), archived, MAX_HISTORY)
        }
        publish(item)
        _localChanges.value++
        item
    }

    private fun fingerprint(p: ItemPayload) =
        listOf(p.kind.code, p.title, p.username, p.password, p.website, p.totpSecret, p.cardNumber, p.note).joinToString("\u0001")

    /**
     * افزودن دسته‌ای (ایمپورت/بازیابی). ورودی‌ای که عیناً مشابه موجود باشد رد می‌شود؛ تاریخ‌های اصلی حفظ می‌شوند.
     */
    suspend fun importItems(list: List<ItemPayload>): ImportStats = lock.withLock {
        val known = _items.value.map { fingerprint(it.payload) }.toHashSet()
        val now = clock()
        val created = ArrayList<VaultItem>()
        var skipped = 0
        withKey { key ->
            for (p in list) {
                if (!known.add(fingerprint(p))) { skipped++; continue }
                val c = if (p.createdAt > 0) p.createdAt else now
                val e = if (p.editedAt > 0) p.editedAt else c
                val item = VaultItem(newId(), 1, p.copy(createdAt = c, editedAt = e))
                store.save(seal(key, item, now), null, MAX_HISTORY)
                created += item
            }
        }
        _items.value = (_items.value + created).sortedBy { it.payload.title.lowercase() }
        if (created.isNotEmpty()) _localChanges.value++
        ImportStats(created.size, skipped)
    }

    suspend fun delete(id: String) = lock.withLock {
        val current = _items.value.firstOrNull { it.id == id } ?: return@withLock
        store.delete(id, clock(), current.rev + 1)
        _items.value = _items.value.filterNot { it.id == id }
        _localChanges.value++
    }

    suspend fun history(id: String): List<HistoryEntry> = withKey { key ->
        store.history(id).mapNotNull { h ->
            try {
                val plain = VaultCrypto.openItem(key, id, h.rev, ItemPayload.SCHEMA, SealedItem(h.wrappedItemKey, h.blob))
                try {
                    HistoryEntry(h.rev, h.savedAt, ItemPayload.fromBytes(plain))
                } finally {
                    plain.wipe()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** بازگردانی یک نسخهٔ قدیمی؛ خودش یک ویرایش جدید است (نسخهٔ فعلی هم در تاریخچه می‌ماند). */
    suspend fun restore(id: String, entry: HistoryEntry): VaultItem = update(id, entry.payload)

    /** پاک‌کردن فهرست رمزگشایی‌شده از حافظه (هنگام قفل). */
    fun clear() {
        _items.value = emptyList()
        _unreadable.value = 0
    }

    private fun seal(key: ByteArray, item: VaultItem, now: Long): StoredItem {
        val plain = item.payload.toBytes()
        try {
            val s = VaultCrypto.sealItem(key, item.id, item.rev, ItemPayload.SCHEMA, plain)
            return StoredItem(item.id, item.rev, now, null, s.wrappedItemKey, s.blob)
        } finally {
            plain.wipe()
        }
    }

    private fun publish(item: VaultItem) {
        _items.value = (_items.value.filterNot { it.id == item.id } + item).sortedBy { it.payload.title.lowercase() }
    }

    companion object {
        const val MAX_HISTORY = 10
    }
}
