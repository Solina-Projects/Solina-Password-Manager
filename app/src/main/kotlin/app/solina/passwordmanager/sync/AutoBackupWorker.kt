package app.solina.passwordmanager.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.solina.passwordmanager.DiagLog
import app.solina.passwordmanager.SolinaApplication

/**
 * کارگر دوره‌ای بکاپ خودکار (WorkManager).
 *
 * محدودیت صادقانه: کلید خزانه فقط در حافظهٔ نشست است؛ پس این کارگر فقط وقتی
 * کاری می‌کند که خزانه در همین پردازه باز باشد (کاربر به‌تازگی باز کرده و
 * قفل خودکار هنوز عمل نکرده است). وقتی خزانه قفل است، کارگر هیچ کاری نمی‌کند
 * و با موفقیت برمی‌گردد تا در تیک دوره‌ای بعدی دوباره تلاش شود؛ بکاپ هرگز
 * با کلیدی که روی دیسک ذخیره شده باشد ساخته نمی‌شود و هیچ فایل متنی هم
 * نوشته نمی‌شود.
 */
class AutoBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SolinaApplication).container
        if (!container.session.unlocked.value) {
            DiagLog.log("AUTOBACKUP", "skip: vault locked")
            return Result.success()
        }
        return try {
            container.sync.runPeriodicBackup()
            Result.success()
        } catch (e: Exception) {
            DiagLog.logError("AUTOBACKUP", e, "worker")
            Result.success()
        }
    }
}
