package app.solina.passwordmanager

import android.content.Context
import android.os.Build
import java.io.File

/**
 * ثبت محلی آخرین خطای کنترل‌نشده تا کاربر بتواند آن را برای رفع اشکال کپی کند (بدون اینترنت و بدون ارسال خودکار).
 * عمداً فقط نام کلاس‌ها و خط‌های stack ثبت می‌شود، نه پیام استثنا، چون پیام‌ها ممکن است ورودی کاربر را شامل شوند.
 */
object CrashReporter {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val text = describe(app, thread, error)
            runCatching { file(app).writeText(text) }
            DiagLog.log("CRASH", "\n$text")
            previous?.uncaughtException(thread, error)
        }
    }

    fun writeHang(context: Context, stack: String) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        runCatching {
            file(context).writeText("HANG (برنامه پاسخ نمی‌داد) | Solina $version | Android ${Build.VERSION.SDK_INT} | ${Build.MANUFACTURER} ${Build.MODEL}\nمحل گیر کردن نخ اصلی:\n$stack\n")
        }
    }

    fun read(context: Context): String? = runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    internal fun describe(context: Context, thread: Thread, error: Throwable): String = buildString {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        appendLine("Solina $version | Android ${Build.VERSION.SDK_INT} | ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("thread: ${thread.name}")
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 5) {
            appendLine(if (depth == 0) t.javaClass.name else "caused by ${t.javaClass.name}")
            t.stackTrace.take(30).forEach { appendLine("  at $it") }
            t = t.cause
            depth++
        }
    }
}
