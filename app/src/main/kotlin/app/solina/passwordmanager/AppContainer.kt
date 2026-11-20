package app.solina.passwordmanager

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.solina.passwordmanager.auth.ExternalAccountManager
import app.solina.passwordmanager.core.security.BiometricKeystore
import app.solina.passwordmanager.core.security.PinUnlockManager
import app.solina.passwordmanager.core.security.SecurityPrefs
import app.solina.passwordmanager.core.security.VaultFiles
import app.solina.passwordmanager.core.security.VaultSession
import app.solina.passwordmanager.core.storage.RoomItemStore
import app.solina.passwordmanager.core.storage.SolinaDatabase
import app.solina.passwordmanager.core.storage.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** ظرف وابستگی‌های سراسری Solina با جداسازی لایه‌های امنیتی، داده و همگام‌سازی. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val files = VaultFiles(File(appContext.filesDir, "solina"))
    val securityPrefs = SecurityPrefs(appContext)
    val pinUnlock = PinUnlockManager(appContext)
    val externalAccounts = ExternalAccountManager(appContext)
    val session = VaultSession(clock = SystemClock::elapsedRealtime)
    val database by lazy { SolinaDatabase.open(appContext) }
    val itemStore by lazy { RoomItemStore(database) }
    val repository by lazy { VaultRepository(itemStore, session) }
    val sync = app.solina.passwordmanager.sync.SyncManager(appContext, files, session, { itemStore }, { repository })

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

class SolinaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        ErrorNotifier.createChannel(this)
        CrashReporter.install(this)
        DiagLog.startWatchdog(this)
        DiagLog.log("APP", "start")
        container = AppContainer(this)
    }
}
