package app.solina.passwordmanager.core.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoLockOption(val millis: Long, val label: String) {
    Immediate(0L, "فوری پس از خروج"),
    ThirtySeconds(30_000L, "۳۰ ثانیه"),
    OneMinute(60_000L, "۱ دقیقه"),
    FiveMinutes(300_000L, "۵ دقیقه");

    companion object {
        fun parse(name: String?): AutoLockOption = entries.firstOrNull { it.name == name } ?: OneMinute
    }
}

enum class ClipboardClearOption(val millis: Long, val label: String) {
    FifteenSeconds(15_000L, "۱۵ ثانیه"),
    ThirtySeconds(30_000L, "۳۰ ثانیه"),
    SixtySeconds(60_000L, "۶۰ ثانیه");

    companion object {
        fun parse(name: String?): ClipboardClearOption = entries.firstOrNull { it.name == name } ?: ThirtySeconds
    }
}

/** تنظیمات امنیتی قابل تغییر توسط کاربر (مهلت قفل خودکار و پاک‌سازی کلیپ‌بورد). */
class SecurityPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    private val _autoLock = MutableStateFlow(AutoLockOption.parse(prefs.getString("auto_lock", null)))
    val autoLock: StateFlow<AutoLockOption> = _autoLock.asStateFlow()

    private val _clipboardClear = MutableStateFlow(ClipboardClearOption.parse(prefs.getString("clipboard_clear", null)))
    val clipboardClear: StateFlow<ClipboardClearOption> = _clipboardClear.asStateFlow()

    fun autoLockMillis(): Long = _autoLock.value.millis
    fun clipboardClearMillis(): Long = _clipboardClear.value.millis

    fun setAutoLock(option: AutoLockOption) {
        prefs.edit().putString("auto_lock", option.name).apply()
        _autoLock.value = option
    }

    fun setClipboardClear(option: ClipboardClearOption) {
        prefs.edit().putString("clipboard_clear", option.name).apply()
        _clipboardClear.value = option
    }

    fun clear() {
        prefs.edit().clear().apply()
        _autoLock.value = AutoLockOption.OneMinute
        _clipboardClear.value = ClipboardClearOption.ThirtySeconds
    }
}
