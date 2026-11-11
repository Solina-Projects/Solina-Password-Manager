package app.solina.passwordmanager

import android.app.Application
import android.content.Context
import app.solina.passwordmanager.core.security.VaultFiles
import app.solina.passwordmanager.core.security.VaultSession
import java.io.File

/** ظرف ساده برای وابستگی‌های سراسری (در M1 با Hilt جایگزین می‌شود). */
class AppContainer(context: Context) {
    val files = VaultFiles(File(context.filesDir, "solina"))
    val session = VaultSession()
}

class SolinaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
