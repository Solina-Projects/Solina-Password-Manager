package app.solina.refuge

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.solina.refuge.auth.ExternalAccountManager
import app.solina.refuge.core.passkey.PasskeyManager
import app.solina.refuge.core.security.BiometricKeystore
import app.solina.refuge.core.security.PinUnlockManager
import app.solina.refuge.core.security.SecurityPrefs
import app.solina.refuge.core.security.VaultFiles
import app.solina.refuge.core.security.VaultSession
import app.solina.refuge.core.storage.RoomItemStore
import app.solina.refuge.core.storage.RefugeDatabase
import app.solina.refuge.core.storage.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** ظرف وابستگی‌های سراسری Refuge با جداسازی لایه‌های امنیتی، داده و همگام‌سازی. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val files = VaultFiles(File(appContext.filesDir, "solina"))
    val securityPrefs = SecurityPrefs(appContext)
    val pinUnlock = PinUnlockManager(appContext)
    val passkeys = PasskeyManager(appContext)
    val externalAccounts = ExternalAccountManager(appContext)
    val session = VaultSession(clock = SystemClock::elapsedRealtime)
    val database by lazy { RefugeDatabase.open(appContext) }
    val itemStore by lazy { RoomItemStore(database) }
    val repository by lazy { VaultRepository(itemStore, session) }
    val sync = app.solina.refuge.sync.SyncManager(appContext, files, session, { itemStore }, { repository })

    private val handler = Handler(Looper.getMainLooper())
    private val lockCheck = Runnable { session.enforceTimeout(securityPrefs.autoLockMillis()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch { session.unlocked.collect { if (!it) repository.clear() } }
    }

    fun scheduleAutoLock() {
        handler.removeCallbacks(lockCheck)
        val timeout = securityPrefs.autoLockMillis()
        if (timeout <= 0L) {
            session.lock()
            return
        }
        session.onBackgrounded()
        handler.postDelayed(lockCheck, timeout + 500L)
    }

    fun cancelAutoLock() = handler.removeCallbacks(lockCheck)

    /**
     * خروج کامل و پاک‌سازی امن تمام اطلاعات محلی (دیتابیس با secure_delete + VACUUM،
     * فایل‌های هدر خزانه، کلید بیومتریک Keystore، اتصال همگام‌سازی و حساب‌های خارجی).
     */
    suspend fun wipeLocalVaultSecurely() {
        cancelAutoLock()
        session.lock()
        repository.clear()
        runCatching { itemStore.wipeAll() }
        runCatching { BiometricKeystore.deleteKey() }
        runCatching { pinUnlock.disablePin() }
        files.deleteAll()
        sync.disconnect()
        externalAccounts.clearAll()
        securityPrefs.clear()
        DiagLog.log("WIPE", "local vault securely wiped")
    }
}

class RefugeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        ErrorNotifier.createChannel(this)
        app.solina.refuge.core.notes.TaskNotifier.createChannel(this)
        app.solina.refuge.core.notes.TaskReminderScheduler.scheduleSweep(this)
        CrashReporter.install(this)
        DiagLog.startWatchdog(this)
        DiagLog.log("APP", "start")
        container = AppContainer(this)
    }
}
