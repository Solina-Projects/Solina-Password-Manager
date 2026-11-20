package app.solina.refuge

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/**
 * گزارش عیب‌یابی محلی (فقط روی دستگاه، بدون ارسال خودکار و بدون مجوز اضافه).
 * قاعده: فقط برچسب مرحله، نام کلاس خطا و خطوط stack ثبت می‌شود؛ هرگز رمز، متن ورودی، نام فایل یا پیام استثنا.
 * عملیات نوشتن روی نخ پس‌زمینه انجام می‌شود تا روی نخ UI باعث لگ (DiskWriteViolation) نشود،
 * و Watchdog تنها زمانی که برنامه در پیش‌زمینه است اجرا می‌شود تا در حالت App Freezer اندروید ۱۱+
 * گزارش Hang کاذب و مصرف باتری ایجاد نکند.
 */
object DiagLog {
    private const val MAX_BYTES = 64 * 1024
    @Volatile private var file: File? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var isForeground: Boolean = false
    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US)
    private val ioLock = Any()
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "refuge-diag-io").apply { isDaemon = true }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        file = File(context.applicationContext.filesDir, "diag.log")
    }

    fun setForeground(foreground: Boolean) {
        isForeground = foreground
    }

    fun log(tag: String, message: String) {
        val stamp = runCatching { LocalDateTime.now().format(fmt) }.getOrDefault("--")
        val line = "$stamp [$tag] $message\n"
        ioExecutor.execute {
            writeLineSync(line)
        }
    }

    private fun writeLineSync(line: String) {
        synchronized(ioLock) {
            val f = file ?: return
            runCatching {
                if (f.length() > MAX_BYTES) f.writeText(f.readText().takeLast(MAX_BYTES / 2))
                f.appendText(line)
            }
        }
    }

    fun logError(tag: String, error: Throwable, where: String = "") {
        val top = error.stackTrace.take(8).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        log(tag, "ERROR ${error.javaClass.name} $where @ $top")
        appContext?.let { ErrorNotifier.notifyError(it) }
    }

    fun read(): String = synchronized(ioLock) {
        runCatching { file?.takeIf { it.isFile }?.readText().orEmpty() }.getOrDefault("")
    }

    fun clear() {
        ioExecutor.execute {
            synchronized(ioLock) {
                runCatching { file?.delete() }
            }
        }
    }

    /**
     * نگهبان قفل‌شدن رابط: تنها در پیش‌زمینه و خارج از حالت دیباگ فعال است.
     * اگر نخ اصلی بیش از ۵ ثانیه پاسخ ندهد، stack نخ اصلی ثبت می‌شود.
     */
    fun startWatchdog(context: Context) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread({
            while (true) {
                if (!isForeground || Debug.isDebuggerConnected()) {
                    try { Thread.sleep(2_000) } catch (e: InterruptedException) { return@Thread }
                    continue
                }
                val answered = java.util.concurrent.atomic.AtomicBoolean(false)
                main.post { answered.set(true) }
                var waited = 0
                var logged = false
                while (!answered.get()) {
                    try { Thread.sleep(500) } catch (e: InterruptedException) { return@Thread }
                    if (!isForeground || Debug.isDebuggerConnected()) break
                    waited += 500
                    if (waited >= 5_000 && !logged) {
                        logged = true
                        val st = Looper.getMainLooper().thread.stackTrace.take(25).joinToString("\n") { "  at $it" }
                        writeLineSync("${runCatching { LocalDateTime.now().format(fmt) }.getOrDefault("--")} [HANG] نخ اصلی ${waited / 1000}s پاسخ نمی‌دهد:\n$st\n")
                        ErrorNotifier.notifyError(app)
                    }
                    if (waited == 10_000) {
                        val st = Looper.getMainLooper().thread.stackTrace.take(30).joinToString("\n") { "  at $it" }
                        CrashReporter.writeHang(app, st)
                    }
                }
                try { Thread.sleep(2_000) } catch (e: InterruptedException) { return@Thread }
            }
        }, "refuge-watchdog").apply { isDaemon = true }.start()
    }
}
