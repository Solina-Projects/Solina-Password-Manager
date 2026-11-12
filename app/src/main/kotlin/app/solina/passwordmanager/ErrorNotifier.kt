package app.solina.passwordmanager

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * نوتیفیکیشن محلی برای خطاها: وقتی خطایی در گزارش عیب‌یابی ثبت شد، یک نوتیف می‌آید
 * که کاربر را به «گزارش عیب‌یابی» می‌برد. هیچ داده‌ای ارسال نمی‌شود و در متن نوتیف
 * هیچ جزئیاتی از خطا (نام فایل، پیام استثنا، ورودی کاربر) نمی‌آید — فقط اینکه
 * «خطایی ثبت شد، گزارش را ببینید». برای اینکه کاربر با هر خطای تکراری بمباران نشود،
 * حداکثر هر ۵ دقیقه یک نوتیف فرستاده می‌شود؛ نوتیف جدید جای قبلی را می‌گیرد.
 */
object ErrorNotifier {
    private const val CHANNEL_ID = "solina-errors"
    private const val NOTIFICATION_ID = 1001
    private const val MIN_INTERVAL_MILLIS = 5 * 60 * 1000L

    @Volatile
    private var lastNotifiedAt = 0L

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "خطاها و گزارش عیب‌یابی",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = "وقتی در Solina خطایی ثبت شد، خبر می‌دهد تا گزارش عیب‌یابی را ببینید." },
                )
            }
        }
    }

    /** اگر مجوز نوتیفیکیشن داده نشده باشد، بی‌صدا هیچ‌کاری نمی‌کند (اپ کرش نمی‌کند). */
    fun notifyError(context: Context) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastNotifiedAt < MIN_INTERVAL_MILLIS) return
            lastNotifiedAt = now
        }
        runCatching {
            createChannel(app)
            val intent = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_SHOW_DIAGNOSTICS, true)
            }
            val pending = PendingIntent.getActivity(
                app, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Solina: خطایی ثبت شد")
                .setContentText("مشکل را در «گزارش عیب‌یابی» ببینید.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }
    }
}
