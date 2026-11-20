package app.solina.refuge.core.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoLockOption(val millis: Long, val labelFa: String, val labelEn: String) {
    Immediate(0L, "فوری پس از خروج", "Immediately on leave"),
    ThirtySeconds(30_000L, "۳۰ ثانیه", "30 seconds"),
    OneMinute(60_000L, "۱ دقیقه", "1 minute"),
    FiveMinutes(300_000L, "۵ دقیقه", "5 minutes"),
    FifteenMinutes(900_000L, "۱۵ دقیقه", "15 minutes");

    val label: String get() = labelFa

    companion object {
        fun parse(name: String?): AutoLockOption = entries.firstOrNull { it.name == name } ?: OneMinute
    }
}

enum class ClipboardClearOption(val millis: Long, val labelFa: String, val labelEn: String) {
    FifteenSeconds(15_000L, "۱۵ ثانیه", "15 seconds"),
    ThirtySeconds(30_000L, "۳۰ ثانیه", "30 seconds"),
    SixtySeconds(60_000L, "۶۰ ثانیه", "60 seconds");

    val label: String get() = labelFa

    companion object {
        fun parse(name: String?): ClipboardClearOption = entries.firstOrNull { it.name == name } ?: ThirtySeconds
    }
}

enum class ThemeMode(val labelFa: String, val labelEn: String) {
    System("مطابق سیستم", "System default"),
    Light("روشن", "Light"),
    Dark("تاریک", "Dark"),
    Amoled("مشکی AMOLED", "AMOLED Black");

    companion object {
        fun parse(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: System
    }
}

enum class AppLanguage(val label: String) {
    FA("فارسی"),
    EN("English");

    companion object {
        fun parse(name: String?): AppLanguage = entries.firstOrNull { it.name == name } ?: FA
    }
}

/** تراکم فهرست خزانه و کدها (فاز ۴): معمولی / فشرده / کاشی‌ها (شبکهٔ دو ستونه). */
enum class ListDensity(val labelFa: String, val labelEn: String) {
    Normal("معمولی", "Normal"),
    Compact("فشرده", "Compact"),
    Tiles("کاشی‌ها", "Tiles");

    companion object {
        fun parse(name: String?): ListDensity = entries.firstOrNull { it.name == name } ?: Normal
    }
}

/** تنظیمات امنیتی و شخصی‌سازی کاربر (قفل خودکار، کلیپ‌بورد، تم و زبان). */
class SecurityPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    private val _autoLock = MutableStateFlow(AutoLockOption.parse(prefs.getString("auto_lock", null)))
    val autoLock: StateFlow<AutoLockOption> = _autoLock.asStateFlow()

    private val _clipboardClear = MutableStateFlow(ClipboardClearOption.parse(prefs.getString("clipboard_clear", null)))
    val clipboardClear: StateFlow<ClipboardClearOption> = _clipboardClear.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.parse(prefs.getString("theme_mode", null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.parse(prefs.getString("language", null)))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** پیشنهاد ذخیرهٔ رمز جدید (SaveInfo سیستم)؛ خاموش که باشد هیچ‌وقت پیشنهاد نمی‌شود. */
    private val _autosaveEnabled = MutableStateFlow(prefs.getBoolean("autosave_enabled", true))
    val autosaveEnabled: StateFlow<Boolean> = _autosaveEnabled.asStateFlow()

    /** گرفتن اسکرین‌شات و فیلم از صفحه؛ پیش‌فرض خاموش است یعنی مسدود (FLAG_SECURE). */
    private val _screenshotsAllowed = MutableStateFlow(prefs.getBoolean("screenshots_allowed", false))
    val screenshotsAllowed: StateFlow<Boolean> = _screenshotsAllowed.asStateFlow()

    /** تراکم فهرست خزانه و کدها (فاز ۴). */
    private val _listDensity = MutableStateFlow(ListDensity.parse(prefs.getString("list_density", null)))
    val listDensity: StateFlow<ListDensity> = _listDensity.asStateFlow()

    /** آنبوردینگ اولین اجرا دیده شده است (فقط نصب تازه، فقط یک‌بار). */
    private val _onboardingDone = MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

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

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString("language", lang.name).apply()
        _language.value = lang
    }

    fun setAutosaveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("autosave_enabled", enabled).apply()
        _autosaveEnabled.value = enabled
    }

    fun setScreenshotsAllowed(allowed: Boolean) {
        prefs.edit().putBoolean("screenshots_allowed", allowed).apply()
        _screenshotsAllowed.value = allowed
    }

    fun setListDensity(density: ListDensity) {
        prefs.edit().putString("list_density", density.name).apply()
        _listDensity.value = density
    }

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean("onboarding_done", done).apply()
        _onboardingDone.value = done
    }

    fun clear() {
        prefs.edit().clear().apply()
        _autoLock.value = AutoLockOption.OneMinute
        _clipboardClear.value = ClipboardClearOption.ThirtySeconds
        _themeMode.value = ThemeMode.System
        _language.value = AppLanguage.FA
        _autosaveEnabled.value = true
        _screenshotsAllowed.value = false
        _listDensity.value = ListDensity.Normal
        _onboardingDone.value = false
    }
}
