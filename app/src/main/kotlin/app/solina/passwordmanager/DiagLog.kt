package app.solina.passwordmanager

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * گزارش عیب‌یابی محلی (فقط روی دستگاه، بدون ارسال خودکار و بدون مجوز اضافه).
 * قاعده: فقط برچسب مرحله، نام کلاس خطا و خطوط stack ثبت می‌شود؛ هرگز رمز، متن ورودی، نام فایل یا پیام استثنا.
 * کاربر از «گزارش عیب‌یابی» آن را کپی یا اشتراک‌گذاری می‌کند.
 */
object DiagLog {
    private const val MAX_BYTES = 64 * 1024
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, "diag.log")
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) f.writeText(f.readText().takeLast(MAX_BYTES / 2))
            f.appendText("${fmt.format(Date())} [$tag] $message\n")
        }
    }

    fun logError(tag: String, error: Throwable, where: String = "") {
        val top = error.stackTrace.take(8).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        log(tag, "ERROR ${error.javaClass.name} $where @ $top")
    }

    @Synchronized
    fun read(): String = runCatching { file?.takeIf { it.isFile }?.readText().orEmpty() }.getOrDefault("")

    @Synchronized
    fun clear() {
        runCatching { file?.delete() }
    }

    /**
     * نگهبان قفل‌شدن رابط: اگر نخ اصلی بیش از ۵ ثانیه پاسخ ندهد، stack نخ اصلی (که نشان می‌دهد کجا گیر کرده) ثبت می‌شود.
     * اگر ۱۰ ثانیه شد، گزارش «hang» هم برای نمایش بعد از بازشدن دوباره ذخیره می‌شود.
     */
    fun startWatchdog(context: Context) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread({
            while (true) {
                val answered = java.util.concurrent.atomic.AtomicBoolean(false)
                main.post { answered.set(true) }
                var waited = 0
                var logged = false
                while (!answered.get()) {
                    try { Thread.sleep(500) } catch (e: InterruptedException) { return@Thread }
                    waited += 500
                    if (waited >= 5_000 && !logged) {
                        logged = true
                        val st = Looper.getMainLooper().thread.stackTrace.take(25).joinToString("\n") { "  at $it" }
                        log("HANG", "نخ اصلی ${waited / 1000}s پاسخ نمی‌دهد:\n$st")
                    }
                    if (waited == 10_000) {
                        val st = Looper.getMainLooper().thread.stackTrace.take(30).joinToString("\n") { "  at $it" }
                        CrashReporter.writeHang(app, st)
                    }
                }
                try { Thread.sleep(2_000) } catch (e: InterruptedException) { return@Thread }
            }
        }, "solina-watchdog").apply { isDaemon = true }.start()
    }
}
