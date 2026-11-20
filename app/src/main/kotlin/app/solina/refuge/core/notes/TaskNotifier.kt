package app.solina.refuge.core.notes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.solina.refuge.MainActivity

/**
 * اعلان یادآوری کارها (فاز ۳).
 *
 * قانون امنیتی غیرقابل مذاکره: وقتی خزانه قفل است، اعلان فقط یک پیام کلی نشان می‌دهد
 * («موعد یک کار رسیده است» / «یک کار عقب‌افتاده داری») — هرگز عنوان یا متن کار.
 * عنوان/متن فقط بعد از بازشدن قفل، داخل برنامه دیده می‌شود. این اعلان هیچ اینترنتی نمی‌خواهد.
 */
object TaskNotifier {
    const val CHANNEL_ID = "refuge-tasks"
    private const val NOTIFICATION_ID = 1002

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "یادآوری کارها",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = "وقتی موعد کاری می‌رسد یا کاری عقب می‌افتد، خبر می‌دهد (بدون نمایش عنوان یا متن کار)." },
                )
            }
        }
    }

    /** پیام کلی یادآوری؛ هیچ‌وقت عنوان یا متن کار به اینجا نمی‌رسد. */
    fun notifyDue(context: Context, dueCount: Int, overdueCount: Int) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            createChannel(app)
            val text = when {
                overdueCount > 0 && dueCount > 0 -> "یک کار عقب‌افتاده داری و موعد یک کار دیگر هم رسیده است"
                overdueCount > 0 -> if (overdueCount == 1) "یک کار عقب‌افتاده داری" else "$overdueCount کار عقب‌افتاده داری"
                dueCount == 1 -> "موعد یک کار رسیده است"
                else -> "موعد $dueCount کار رسیده است"
            }.replaceDigitsFa()
            val intent = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_NOTES, true)
            }
            val pending = PendingIntent.getActivity(
                app, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Refuge")
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    private fun String.replaceDigitsFa(): String {
        val fa = "۰۱۲۳۴۵۶۷۸۹"
        val sb = StringBuilder(length)
        for (ch in this) sb.append(if (ch in '0'..'9') fa[ch - '0'] else ch)
        return sb.toString()
    }
}
