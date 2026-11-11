package app.solina.passwordmanager

import android.app.Application
import android.content.Context
import app.solina.passwordmanager.core.security.VaultFiles
import app.solina.passwordmanager.core.security.VaultSession
import app.solina.passwordmanager.core.storage.RoomItemStore
import app.solina.passwordmanager.core.storage.SolinaDatabase
import app.solina.passwordmanager.core.storage.VaultRepository
import java.io.File

/** ظرف ساده برای وابستگی‌های سراسری (در M1 با Hilt جایگزین می‌شود). */
class AppContainer(context: Context) {
    val files = VaultFiles(File(context.filesDir, "solina"))
    val session = VaultSession()
    val database by lazy { SolinaDatabase.open(context) }
    val repository by lazy { VaultRepository(RoomItemStore(database), session) }
}

class SolinaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
