package app.solina.passwordmanager.core.notes

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.solina.passwordmanager.DiagLog
import app.solina.passwordmanager.SolinaApplication
import java.util.concurrent.TimeUnit

/**
 * زمان‌بندی یادآوری کارها با WorkManager (فاز ۳؛ بدون اینترنت).
 * - برای هر کارِ دارای موعد، یک کار یک‌باره با تأخیر تا موعد ساخته می‌شود.
 * - یک جاروی دوره‌ای ۱۵ دقیقه‌ای هم هست تا اگر دستگاه در لحظهٔ موعد خواب بود، یادآوری جا نماند.
 * کارگر فقط جدول متن‌باز task_due را می‌خواند (شناسه + زمان) و فقط پیام کلی می‌فرستد؛
 * عنوان/متن کار هرگز از خزانهٔ قفل بیرون نمی‌آید.
 */
object TaskReminderScheduler {
    private const val SWEEP_NAME = "task-reminder-sweep"

    fun scheduleOneTime(context: Context, taskId: String, dueAt: Long) {
        val delay = (dueAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("task-reminder")
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("task-reminder-$taskId", ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancel(context: Context, taskId: String) {
        runCatching {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork("task-reminder-$taskId")
        }
    }

    fun scheduleSweep(context: Context) {
        val request = PeriodicWorkRequestBuilder<TaskReminderWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().build())
            .addTag("task-reminder")
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(SWEEP_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

class TaskReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SolinaApplication).container
        val now = System.currentTimeMillis()
        return try {
            val dues = container.repository.taskDues()
            var dueCount = 0
            var overdueCount = 0
            for (meta in dues) {
                if (meta.dueAt <= 0 || meta.dueAt > now) continue
                val overdue = meta.dueAt < now - OVERDUE_GRACE_MILLIS
                val neverNotified = meta.notifiedAt == 0L
                val staleReminder = meta.notifiedAt > 0 && now - meta.notifiedAt >= 24 * 60 * 60 * 1000L
                if (neverNotified || staleReminder) {
                    if (overdue) overdueCount++ else dueCount++
                    container.repository.markTaskNotified(meta.id, now)
                }
            }
            if (dueCount > 0 || overdueCount > 0) {
                TaskNotifier.notifyDue(applicationContext, dueCount, overdueCount)
                DiagLog.log("TASKREM", "notified due=$dueCount overdue=$overdueCount")
            }
            Result.success()
        } catch (e: Exception) {
            DiagLog.logError("TASKREM", e, "worker")
            Result.success()
        }
    }

    companion object {
        /** کاری که موعدش همین‌الان رسیده، تا یک ساعت بعد «سر موعد» حساب می‌شود، بعد «عقب‌افتاده». */
        const val OVERDUE_GRACE_MILLIS = 60 * 60 * 1000L
    }
}
