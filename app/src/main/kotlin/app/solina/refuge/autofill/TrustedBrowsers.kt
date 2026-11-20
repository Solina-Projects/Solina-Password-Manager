package app.solina.refuge.autofill

/**
 * فهرست مرورگرهای معتبر که گزارش `webDomain` آن‌ها در AssistStructure قابل اعتماد است.
 * اپلیکیشن‌های ناشناس اجازه ندارند با جعل `webDomain` رمزهای وب‌سایت‌ها را از سرویس Autofill درخواست کنند.
 */
object TrustedBrowsers {
    private val PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.microsoft.emmx",
        "com.duckduckgo.mobile.android",
        "app.vanadium.browser",
        "org.cromite.cromite",
        "org.bromite.bromite",
        "us.spotco.fennec_dos",
        "org.torproject.torbrowser",
        "com.sec.android.app.sbrowser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
    )

    fun isTrusted(packageName: String?): Boolean =
        !packageName.isNullOrBlank() && packageName in PACKAGES
}
