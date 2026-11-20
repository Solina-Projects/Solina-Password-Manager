package app.solina.passwordmanager.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.solina.passwordmanager.DiagLog
import app.solina.passwordmanager.core.backup.AutoBackupKey
import app.solina.passwordmanager.core.backup.Backup
import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import app.solina.passwordmanager.core.crypto.CorruptVaultException
import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.crypto.VaultCrypto
import app.solina.passwordmanager.core.crypto.VaultHeader
import app.solina.passwordmanager.core.crypto.wipe
import app.solina.passwordmanager.core.security.KeystoreSecretStore
import app.solina.passwordmanager.core.security.VaultFiles
import app.solina.passwordmanager.core.security.VaultSession
import app.solina.passwordmanager.core.storage.ItemStore
import app.solina.passwordmanager.core.storage.VaultRepository
import app.solina.passwordmanager.core.sync.BackupRetention
import app.solina.passwordmanager.core.sync.BackupSchedule
import app.solina.passwordmanager.core.sync.BackupTrigger
import app.solina.passwordmanager.core.sync.SyncEngine
import app.solina.passwordmanager.core.sync.SyncEpochStore
import app.solina.passwordmanager.core.sync.SyncRemote
import app.solina.passwordmanager.core.sync.WebDavProtocol
import app.solina.passwordmanager.core.sync.shouldRunAutomatically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI

data class SyncStatus(
    val configured: Boolean = false,
    val folderName: String = "",
    val syncing: Boolean = false,
    val lastSyncAt: Long = 0,
    val schedule: BackupSchedule = BackupSchedule.OnChange,
    /** "cloud" یا "storage" یا "webdav" */
    val kind: String = "storage",
    val message: String? = null,
)

/** هدر خوانده‌شده از پوشه یا سرور WebDAV پیش از تایید رمز کاربر؛ هنوز روی دیسک ذخیره نشده است. */
class StagedJoin(
    val uri: Uri?,
    val kind: String,
    val header: VaultHeader,
    val folderName: String,
    val webDavUrl: String = "",
    val webDavUser: String = "",
    val webDavPass: String = "",
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
    data class BackupFilesFound(val fileNames: List<String>) : StageJoinResult
    data class Failed(val reason: String) : StageJoinResult
}

/**
 * پیکربندی و اجرای همگام‌سازی رمزگذاری‌شدهٔ سرتاسری (پوشه‌ای SAF یا سرور اختصاصی WebDAV HTTPS).
 * فقط وقتی خزانه باز است همگام‌سازی می‌کند (کلیدهای HMAC و مانیفست از VaultKey مشتق می‌شوند).
 */
class SyncManager(
    private val context: Context,
    private val files: VaultFiles,
    private val session: VaultSession,
    private val store: () -> ItemStore,
    private val repository: () -> VaultRepository,
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val secrets = KeystoreSecretStore(context, "sync_secrets")
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

    private fun webDavUrl(): String? = prefs.getString("webdav_url", null)?.takeIf { it.isNotBlank() }

    private fun activeRemote(): SyncRemote? {
        val kind = prefs.getString("kind", "storage") ?: "storage"
        if (kind == "webdav") {
            val url = webDavUrl() ?: return null
            val user = secrets.getString("webdav_user").orEmpty()
            val pass = secrets.getString("webdav_pass").orEmpty()
            return runCatching { WebDavRemote(url, user, pass) }.getOrNull()
        }
        val uri = treeUri() ?: return null
        return FolderRemote(context, uri)
    }

    private fun webDavDisplayName(url: String): String = runCatching {
        val u = URI(url)
        (u.host ?: url) + (u.path?.takeIf { it.length > 1 } ?: "")
    }.getOrDefault(url)

    private fun load(): SyncStatus {
        val kind = prefs.getString("kind", "storage") ?: "storage"
        if (kind == "webdav") {
            val url = webDavUrl() ?: return SyncStatus()
            return SyncStatus(
                configured = true,
                folderName = webDavDisplayName(url),
                syncing = false,
                lastSyncAt = prefs.getLong("last", 0),
                schedule = BackupSchedule.parse(prefs.getString("schedule", null)),
                kind = "webdav",
                message = null,
            )
        }
        val uri = treeUri() ?: return SyncStatus()
        return SyncStatus(
            true,
            FolderRemote(context, uri).displayName(),
            false,
            prefs.getLong("last", 0),
            BackupSchedule.parse(prefs.getString("schedule", null)),
            kind,
            null,
        )
    }

    private fun persistFolder(uri: Uri, kind: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        secrets.clearAll()
        prefs.edit()
            .remove("webdav_url")
            .putString("tree", uri.toString())
            .putString("kind", kind)
            .putLong("last", 0)
            .apply()
    }

    private fun persistWebDav(normalizedUrl: String, username: String, password: String) {
        secrets.putString("webdav_user", username)
        secrets.putString("webdav_pass", password)
        prefs.edit()
            .remove("tree")
            .putString("webdav_url", normalizedUrl)
            .putString("kind", "webdav")
            .putLong("last", 0)
            .apply()
    }

    /** اتصال یک دستگاهِ دارای خزانه به پوشه (SAF). */
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
                if (!other.wrappedVkByRecovery.contentEquals(local.wrappedVkByRecovery)) return ConnectResult.DifferentVault
            }
            persistFolder(uri, kind)
            _status.value = load()
            AutoBackupScheduler.reschedule(context, _status.value.schedule, true)
            ConnectResult.Ok
        } catch (e: Exception) {
            DiagLog.logError("CONNECT", e, "connect")
            ConnectResult.Failed("پوشه در دسترس نیست یا نوشتن ممکن نشد.")
        }
    }

    /** اتصال یک دستگاهِ دارای خزانه به سرور اختصاصی WebDAV (فقط HTTPS). */
    suspend fun connectWebDav(rawUrl: String, username: String, password: String): ConnectResult =
        withContext(Dispatchers.IO) {
            val normalized = try {
                WebDavProtocol.normalizeHttpsBaseUrl(rawUrl)
            } catch (e: Exception) {
                return@withContext ConnectResult.Failed(e.message ?: "نشانی HTTPS سرور معتبر نیست.")
            }
            try {
                val remote = WebDavRemote(normalized, username.trim(), password)
                remote.ensureReady()
                val local = files.readHeader()
                val remoteBytes = remote.readHeader()
                if (remoteBytes == null) {
                    remote.writeHeader(local.encode())
                } else {
                    val other = try {
                        VaultHeader.decode(remoteBytes)
                    } catch (e: CorruptVaultException) {
                        return@withContext ConnectResult.Failed("فایل vault.hdr روی سرور خراب است.")
                    }
                    if (!other.wrappedVkByRecovery.contentEquals(local.wrappedVkByRecovery)) {
                        return@withContext ConnectResult.DifferentVault
                    }
                }
                persistWebDav(normalized, username.trim(), password)
                _status.value = load()
                AutoBackupScheduler.reschedule(context, _status.value.schedule, true)
                ConnectResult.Ok
            } catch (e: Exception) {
                DiagLog.logError("CONNECT", e, "connectWebDav")
                ConnectResult.Failed("اتصال به سرور WebDAV ناموفق بود؛ نشانی HTTPS و اعتبارنامه را بررسی کنید.")
            }
        }

    /** مرحلهٔ ۱ پیوستن از پوشه: فقط هدر را در حافظه می‌خواند. */
    suspend fun stageJoinFromFolder(uri: Uri, kind: String = "storage"): StageJoinResult {
        DiagLog.log("JOIN", "stage folder selected")
        if (files.hasVault()) return StageJoinResult.Failed("روی این دستگاه از قبل خزانه وجود دارد.")
        return try {
            val remote = FolderRemote(context, uri)
            val bytes = remote.readHeader()
            if (bytes == null) {
                val backups = remote.findBackupFiles()
                return if (backups.isNotEmpty()) StageJoinResult.BackupFilesFound(backups)
                else StageJoinResult.NoVaultInFolder
            }
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

    /** مرحلهٔ ۱ پیوستن از سرور WebDAV (برای انتقال به گوشی جدید). */
    suspend fun stageJoinFromWebDav(rawUrl: String, username: String, password: String): StageJoinResult =
        withContext(Dispatchers.IO) {
            if (files.hasVault()) return@withContext StageJoinResult.Failed("روی این دستگاه از قبل خزانه وجود دارد.")
            val normalized = try {
                WebDavProtocol.normalizeHttpsBaseUrl(rawUrl)
            } catch (e: Exception) {
                return@withContext StageJoinResult.Failed(e.message ?: "نشانی HTTPS معتبر نیست.")
            }
            try {
                val remote = WebDavRemote(normalized, username.trim(), password)
                val bytes = remote.readHeader() ?: return@withContext StageJoinResult.NoVaultInFolder
                val header = try {
                    VaultHeader.decode(bytes)
                } catch (e: CorruptVaultException) {
                    return@withContext StageJoinResult.Failed("فایل vault.hdr روی سرور خراب است.")
                }
                StageJoinResult.Ready(
                    StagedJoin(
                        uri = null,
                        kind = "webdav",
                        header = header,
                        folderName = webDavDisplayName(normalized),
                        webDavUrl = normalized,
                        webDavUser = username.trim(),
                        webDavPass = password,
                    ),
                )
            } catch (e: Exception) {
                DiagLog.logError("JOIN", e, "stageJoinFromWebDav")
                StageJoinResult.Failed("خواندن از سرور WebDAV ناموفق بود.")
            }
        }

    /** مرحلهٔ ۲ پیوستن: رمز اصلی را مقابل هدر در حافظه بررسی و سپس روی دیسک ذخیره می‌کند. */
    suspend fun commitStagedJoin(staged: StagedJoin, password: CharArray): Boolean = withContext(Dispatchers.Default) {
        val vk = try {
            VaultCrypto.unlockWithPassword(staged.header, password)
        } catch (e: AuthenticationFailedException) {
            return@withContext false
        }
        files.writeHeader(staged.header)
        if (staged.kind == "webdav") {
            persistWebDav(staged.webDavUrl, staged.webDavUser, staged.webDavPass)
        } else {
            staged.uri?.let { persistFolder(it, staged.kind) }
        }
        session.unlock(vk)
        _status.value = load()
        DiagLog.log("JOIN", "staged join committed")
        true
    }

    /** دستگاه جدید بدون خزانه (سازگاری قبلی). */
    suspend fun joinFromFolder(uri: Uri, kind: String = "storage"): ConnectResult {
        return when (val r = stageJoinFromFolder(uri, kind)) {
            is StageJoinResult.Ready -> {
                files.writeHeader(r.staged.header)
                r.staged.uri?.let { persistFolder(it, kind) }
                _status.value = load()
                ConnectResult.Ok
            }
            StageJoinResult.NoVaultInFolder, is StageJoinResult.BackupFilesFound -> ConnectResult.NoVaultInFolder
            is StageJoinResult.Failed -> ConnectResult.Failed(r.reason)
        }
    }

    fun setSchedule(schedule: BackupSchedule) {
        prefs.edit().putString("schedule", schedule.name).apply()
        _status.value = _status.value.copy(schedule = schedule)
        AutoBackupScheduler.reschedule(context, schedule, _status.value.configured)
    }

    suspend fun syncIfDue(trigger: BackupTrigger): StatusMessage? {
        val st = _status.value
        if (!st.configured) return null
        if (!shouldRunAutomatically(st.schedule, st.lastSyncAt, System.currentTimeMillis(), trigger)) return null
        return syncNow()
    }

    /**
     * بکاپ زمان‌بندی‌شده (از WorkManager): همگام‌سازیِ سررسید + اسنپ‌شات نسخه‌دار
     * در پوشه/سرور. فقط وقتی خزانه باز است؛ در غیر این صورت false و بدون هیچ نوشتن‌ای.
     */
    suspend fun runPeriodicBackup(): Boolean {
        val st = _status.value
        if (!st.configured) return false
        if (!shouldRunAutomatically(st.schedule, st.lastSyncAt, System.currentTimeMillis(), BackupTrigger.Periodic)) return false
        val key = session.keyCopy() ?: return false
        try {
            if (syncNow() !is StatusMessage.Done) return false
            return writeVersionedSnapshot(key)
        } finally {
            key.wipe()
        }
    }

    /** اسنپ‌شات SLNB v1 با رمز مشتق از کلید خزانه + هرس نسخه‌های قدیمی (نگه‌داشتن ۵ نسخه). */
    private suspend fun writeVersionedSnapshot(vaultKey: ByteArray): Boolean {
        val remote = activeRemote() ?: return false
        val payloads = repository().items.value.map { it.payload }
        val password = AutoBackupKey.backupPasswordChars(vaultKey)
        return try {
            val header = runCatching { files.readHeader().encode() }.getOrNull()
            val bytes = Backup.encode(payloads, password, System.currentTimeMillis(), KdfParams.DEFAULT, header)
            val name = BackupRetention.snapshotName(System.currentTimeMillis())
            remote.write(name, bytes)
            val names = runCatching { remote.list() }.getOrDefault(emptyList())
            val old = BackupRetention.selectForDeletion(names)
            old.forEach { runCatching { remote.delete(it) } }
            DiagLog.log("AUTOBACKUP", "snapshot $name written, pruned ${old.size}")
            true
        } catch (e: Exception) {
            DiagLog.logError("AUTOBACKUP", e, "writeVersionedSnapshot")
            false
        } finally {
            password.fill('\u0000')
        }
    }

    fun disconnect() {
        secrets.clearAll()
        prefs.edit().clear().apply()
        _status.value = SyncStatus()
        AutoBackupScheduler.reschedule(context, BackupSchedule.Manual, false)
    }

    private suspend fun writeHeaderToRemote(remote: SyncRemote, bytes: ByteArray) {
        when (remote) {
            is FolderRemote -> remote.writeHeader(bytes)
            is WebDavRemote -> remote.writeHeader(bytes)
            else -> remote.write("vault.hdr", bytes)
        }
    }

    suspend fun pushHeader(): Boolean {
        prefs.edit().putBoolean("header_dirty", true).apply()
        val remote = activeRemote() ?: return false
        return runCatching {
            writeHeaderToRemote(remote, files.readHeader().encode())
            prefs.edit().putBoolean("header_dirty", false).apply()
            true
        }.onFailure { e ->
            DiagLog.logError("SYNC", e, "pushHeader")
        }.getOrDefault(false)
    }

    suspend fun syncNow(): StatusMessage {
        val remote = activeRemote() ?: return StatusMessage.NotConfigured
        val key = session.keyCopy() ?: return StatusMessage.Locked
        DiagLog.log("SYNC", "start")
        if (!mutex.tryLock()) {
            key.wipe()
            return StatusMessage.Busy
        }
        _status.value = _status.value.copy(syncing = true, message = null)
        try {
            val repo = repository()
            if (prefs.getBoolean("header_dirty", false)) {
                runCatching {
                    writeHeaderToRemote(remote, files.readHeader().encode())
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
                if (report.rollbackDetected) append("، هشدار: احتمال بازگشت پوشه/سرور به نسخهٔ قدیمی تشخیص داده شد")
            }
            _status.value = _status.value.copy(syncing = false, lastSyncAt = now, message = msg)
            return StatusMessage.Done(report.pulled, report.pushed, report.conflicts, report.rejected)
        } catch (e: Exception) {
            DiagLog.logError("SYNC", e, "syncNow")
            _status.value = _status.value.copy(syncing = false, message = "همگام‌سازی ناموفق بود؛ اتصال پوشه یا سرور را بررسی کنید.")
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
