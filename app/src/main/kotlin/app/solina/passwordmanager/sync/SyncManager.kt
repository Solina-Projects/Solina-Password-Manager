package app.solina.passwordmanager.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.solina.passwordmanager.DiagLog
import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import app.solina.passwordmanager.core.crypto.CorruptVaultException
import app.solina.passwordmanager.core.crypto.VaultCrypto
import app.solina.passwordmanager.core.crypto.VaultHeader
import app.solina.passwordmanager.core.crypto.wipe
import app.solina.passwordmanager.core.security.VaultFiles
import app.solina.passwordmanager.core.security.VaultSession
import app.solina.passwordmanager.core.storage.ItemStore
import app.solina.passwordmanager.core.storage.VaultRepository
import app.solina.passwordmanager.core.sync.BackupSchedule
import app.solina.passwordmanager.core.sync.BackupTrigger
import app.solina.passwordmanager.core.sync.SyncEngine
import app.solina.passwordmanager.core.sync.SyncEpochStore
import app.solina.passwordmanager.core.sync.shouldRunAutomatically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SyncStatus(
    val configured: Boolean = false,
    val folderName: String = "",
    val syncing: Boolean = false,
    val lastSyncAt: Long = 0,
    val schedule: BackupSchedule = BackupSchedule.OnChange,
    /** "cloud" یا "storage" — فقط برای متن واضح‌تر در تنظیمات. */
    val kind: String = "storage",
    val message: String? = null,
)

/** هدر خوانده‌شده از پوشه پیش از تایید رمز کاربر؛ هنوز روی دیسک ذخیره نشده است. */
class StagedJoin(
    val uri: Uri,
    val kind: String,
    val header: VaultHeader,
    val folderName: String,
)

sealed interface ConnectResult {
    data object Ok : ConnectResult
    data object NoVaultInFolder : ConnectResult
    data object DifferentVault : ConnectResult
    data class Failed(val reason: String) : ConnectResult
}

sealed interface StageJoinResult {
    data class Ready(val staged: StagedJoin) : StageJoinResult
    data object NoVaultInFolder : StageJoinResult
    data class Failed(val reason: String) : StageJoinResult
}

/** پیکربندی و اجرای همگام‌سازی پوشه‌ای. فقط وقتی خزانه باز است همگام‌سازی می‌کند (کلید MAC از کلید خزانه می‌آید). */
class SyncManager(
    private val context: Context,
    private val files: VaultFiles,
    private val session: VaultSession,
    private val store: () -> ItemStore,
    private val repository: () -> VaultRepository,
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _status = MutableStateFlow(load())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val epochStore = object : SyncEpochStore {
        override fun lastSeenEpoch(): Long = prefs.getLong("last_epoch", 0L)
        override fun saveLastSeenEpoch(epoch: Long) {
            if (epoch > lastSeenEpoch()) {
                prefs.edit().putLong("last_epoch", epoch).apply()
            }
        }
    }

    private fun treeUri(): Uri? = prefs.getString("tree", null)?.let(Uri::parse)

    private fun load(): SyncStatus {
        val uri = treeUri() ?: return SyncStatus()
        return SyncStatus(
            true,
            FolderRemote(context, uri).displayName(),
            false,
            prefs.getLong("last", 0),
            BackupSchedule.parse(prefs.getString("schedule", null)),
            prefs.getString("kind", "storage") ?: "storage",
            null,
        )
    }

    private fun persist(uri: Uri, kind: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.edit().putString("tree", uri.toString()).putString("kind", kind).putLong("last", 0).apply()
    }

    /** اتصال یک دستگاهِ دارای خزانه به پوشه (ایجاد یا تأیید vault.hdr). */
    suspend fun connect(uri: Uri, kind: String = "storage"): ConnectResult {
        return try {
            val remote = FolderRemote(context, uri)
            val local = files.readHeader()
            val remoteBytes = remote.readHeader()
            if (remoteBytes == null) {
                remote.writeHeader(local.encode())
            } else {
                val other = try {
                    VaultHeader.decode(remoteBytes)
                } catch (e: CorruptVaultException) {
                    return ConnectResult.Failed("فایل vault.hdr داخل پوشه خراب است.")
                }
                // هویت خزانه = بستهٔ کلید بازیابی؛ با تغییر رمز اصلی عوض نمی‌شود.
                if (!other.wrappedVkByRecovery.contentEquals(local.wrappedVkByRecovery)) return ConnectResult.DifferentVault
            }
            persist(uri, kind)
            _status.value = load()
            ConnectResult.Ok
        } catch (e: Exception) {
            DiagLog.logError("CONNECT", e, "connect")
            ConnectResult.Failed("پوشه در دسترس نیست یا نوشتن ممکن نشد.")
        }
    }

    /**
     * مرحلهٔ ۱ پیوستن به خزانه از پوشه: فقط هدر را در حافظه می‌خواند و اعتبارسنجی می‌کند.
     * هیچ فایلی روی دیسک نوشته نمی‌شود تا کاربر رمز اصلی را در [commitStagedJoin] تأیید کند.
     */
    suspend fun stageJoinFromFolder(uri: Uri, kind: String = "storage"): StageJoinResult {
        DiagLog.log("JOIN", "stage folder selected")
        if (files.hasVault()) return StageJoinResult.Failed("روی این دستگاه از قبل خزانه وجود دارد.")
        return try {
            val remote = FolderRemote(context, uri)
            val bytes = remote.readHeader()
            DiagLog.log("JOIN", "header read: ${if (bytes == null) "absent" else "${bytes.size} bytes"}")
            if (bytes == null) return StageJoinResult.NoVaultInFolder
            val header = try {
                VaultHeader.decode(bytes)
            } catch (e: CorruptVaultException) {
                return StageJoinResult.Failed("فایل vault.hdr داخل پوشه خراب است.")
            }
            StageJoinResult.Ready(StagedJoin(uri, kind, header, remote.displayName()))
        } catch (e: Exception) {
            DiagLog.logError("JOIN", e, "stageJoinFromFolder")
            StageJoinResult.Failed("خواندن پوشه ممکن نشد.")
        }
    }

    /**
     * مرحلهٔ ۲ پیوستن: رمز اصلی را مقابل هدر در حافظه بررسی می‌کند و تنها در صورت موفقیت،
     * هدر و مجوز پوشه را روی دیسک ذخیره و نشست را باز می‌کند.
     */
    suspend fun commitStagedJoin(staged: StagedJoin, password: CharArray): Boolean = withContext(Dispatchers.Default) {
        val vk = try {
            VaultCrypto.unlockWithPassword(staged.header, password)
        } catch (e: AuthenticationFailedException) {
            return@withContext false
        }
        files.writeHeader(staged.header)
        persist(staged.uri, staged.kind)
        session.unlock(vk)
        _status.value = load()
        DiagLog.log("JOIN", "staged join committed")
        true
    }

    /** دستگاه جدید بدون خزانه (سازگاری قبلی): هدر را از پوشه می‌گیرد. */
    suspend fun joinFromFolder(uri: Uri, kind: String = "storage"): ConnectResult {
        return when (val r = stageJoinFromFolder(uri, kind)) {
            is StageJoinResult.Ready -> {
                files.writeHeader(r.staged.header)
                persist(uri, kind)
                _status.value = load()
                ConnectResult.Ok
            }
            StageJoinResult.NoVaultInFolder -> ConnectResult.NoVaultInFolder
            is StageJoinResult.Failed -> ConnectResult.Failed(r.reason)
        }
    }

    fun setSchedule(schedule: BackupSchedule) {
        prefs.edit().putString("schedule", schedule.name).apply()
        _status.value = _status.value.copy(schedule = schedule)
    }

    /** اجرای خودکار بر اساس زمان‌بندی؛ برای حالت دستی هیچ کاری نمی‌کند. */
    suspend fun syncIfDue(trigger: BackupTrigger): StatusMessage? {
        val st = _status.value
        if (!st.configured) return null
        if (!shouldRunAutomatically(st.schedule, st.lastSyncAt, System.currentTimeMillis(), trigger)) return null
        return syncNow()
    }

    fun disconnect() {
        prefs.edit().clear().apply()
        _status.value = SyncStatus()
    }

    /**
     * پس از تغییر رمز اصلی، هدر جدید را در پوشه می‌نویسد تا دستگاه تازه با رمز جدید بتواند وصل شود.
     * در صورت شکست، فلگ header_dirty ثبت می‌شود تا در اولین همگام‌سازی بعدی دوباره ارسال شود.
     */
    suspend fun pushHeader(): Boolean {
        prefs.edit().putBoolean("header_dirty", true).apply()
        val uri = treeUri() ?: return false
        return runCatching {
            FolderRemote(context, uri).writeHeader(files.readHeader().encode())
            prefs.edit().putBoolean("header_dirty", false).apply()
            true
        }.onFailure { e ->
            DiagLog.logError("SYNC", e, "pushHeader")
        }.getOrDefault(false)
    }

    suspend fun syncNow(): StatusMessage {
        val uri = treeUri() ?: return StatusMessage.NotConfigured
        val key = session.keyCopy() ?: return StatusMessage.Locked
        DiagLog.log("SYNC", "start")
        if (!mutex.tryLock()) {
            key.wipe()
            return StatusMessage.Busy
        }
        _status.value = _status.value.copy(syncing = true, message = null)
        try {
            val repo = repository()
            val remote = FolderRemote(context, uri)
            if (prefs.getBoolean("header_dirty", false)) {
                runCatching {
                    remote.writeHeader(files.readHeader().encode())
                    prefs.edit().putBoolean("header_dirty", false).apply()
                }
            }
            val report = repo.lock.withLock {
                SyncEngine(store(), remote, epochStore = epochStore).sync(key)
            }
            if (report.changedLocal) repo.load()
            val now = System.currentTimeMillis()
            DiagLog.log("SYNC", "done pulled=${report.pulled} pushed=${report.pushed} conflicts=${report.conflicts} rejected=${report.rejected} rollback=${report.rollbackDetected}")
            prefs.edit().putLong("last", now).apply()
            val msg = buildString {
                append("دریافت ${report.pulled}، ارسال ${report.pushed}")
                if (report.conflicts > 0) append("، ${report.conflicts} تعارض (نسخهٔ بازنده در تاریخچه ماند)")
                if (report.rejected > 0) append("، ${report.rejected} فایل نامعتبر نادیده گرفته شد")
                if (report.rollbackDetected) append("، هشدار: احتمال بازگشت پوشه به نسخهٔ قدیمی تشخیص داده شد")
            }
            _status.value = _status.value.copy(syncing = false, lastSyncAt = now, message = msg)
            return StatusMessage.Done(report.pulled, report.pushed, report.conflicts, report.rejected)
        } catch (e: Exception) {
            DiagLog.logError("SYNC", e, "syncNow")
            _status.value = _status.value.copy(syncing = false, message = "همگام‌سازی ناموفق بود؛ پوشه در دسترس نیست؟")
            return StatusMessage.Failed
        } finally {
            key.wipe()
            mutex.unlock()
        }
    }

    sealed interface StatusMessage {
        data object NotConfigured : StatusMessage
        data object Locked : StatusMessage
        data object Busy : StatusMessage
        data object Failed : StatusMessage
        data class Done(val pulled: Int, val pushed: Int, val conflicts: Int, val rejected: Int) : StatusMessage
    }
}
