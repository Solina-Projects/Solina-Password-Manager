package app.solina.passwordmanager.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.solina.passwordmanager.core.sync.BackupSchedule
import java.util.concurrent.TimeUnit

/**
 * زمان‌بندی کار دوره‌ای بکاپ خودکار.
 *
 * فقط برای زمان‌بندی‌های روزانه/هفتگی/ماهانه کار ثبت می‌شود؛ «در هر تغییر»
 * با همان همگام‌سازیِ هنگام تغییر پوشش داده می‌شود و «دستی» اصلاً کار
 * پس‌زمینه نمی‌خواهد. با قطع اتصال همگام‌سازی یا تغییر زمان‌بندی به دستی،
 * کار لغو می‌شود. WorkManager خودش زمان‌بندی را بعد از ری‌استارت نگه می‌دارد.
 */
object AutoBackupScheduler {
    const val UNIQUE_NAME = "solina-auto-backup"

    fun reschedule(context: Context, schedule: BackupSchedule, configured: Boolean) {
        val wm = WorkManager.getInstance(context)
        val period = schedule.periodMillis
        if (!configured || schedule == BackupSchedule.Manual ||
            schedule == BackupSchedule.OnChange || period == null
        ) {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(period, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
