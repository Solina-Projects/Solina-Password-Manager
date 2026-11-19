package app.solina.passwordmanager

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

/** ظرف ساده برای وابستگی‌های سراسری (در M1 با Hilt جایگزین می‌شود). */
class AppContainer(context: Context) {
    val files = VaultFiles(File(context.filesDir, "solina"))
    val securityPrefs = SecurityPrefs(context)
    val session = VaultSession(clock = SystemClock::elapsedRealtime)
    val database by lazy { SolinaDatabase.open(context) }
    val itemStore by lazy { RoomItemStore(database) }
    val repository by lazy { VaultRepository(itemStore, session) }
    val sync = app.solina.passwordmanager.sync.SyncManager(context.applicationContext, files, session, { itemStore }, { repository })

    private val handler = Handler(Looper.getMainLooper())
    private val lockCheck = Runnable { session.enforceTimeout(securityPrefs.autoLockMillis()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // هر وقت خزانه قفل شد (از هر مسیری)، فهرست رمزگشایی‌شده از حافظه پاک می‌شود.
        scope.launch { session.unlocked.collect { if (!it) repository.clear() } }
    }

    /** شروع مهلت قفل خودکار؛ در پس‌زمینه هم (بدون نیاز به بازگشت به اپ) قفل انجام می‌شود. */
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
