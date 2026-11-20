package app.solina.refuge.hardening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** نگهبان‌های رگرسیون: اگر کسی ناخواسته مجوز، لاگ، ترافیک غیررمزنگاری یا مؤلفهٔ export‌شده اضافه کند CI قرمز می‌شود. */
class GuardTest {
    private fun moduleDir(): File {
        var d = File("").absoluteFile
        if (!File(d, "src/main").exists() && File(d, "app/src/main").exists()) d = File(d, "app")
        return d
    }

    /** متن مانیفست بدون کامنت‌های XML (تا اشاره‌ها در توضیحات باعث مثبت کاذب نشود). */
    private val manifest get() = File(moduleDir(), "src/main/AndroidManifest.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    @Test fun onlyExpectedMinimalPermissions() {
        val perms = Regex("""<uses-permission[^>]*android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        // CAMERA: اسکن QR؛ POST_NOTIFICATIONS: اعلان محلی خطا؛ INTERNET: فقط برای امکانات اختیاری HTTPS-only (WebDAV، k-Anonymity HIBP، OAuth PKCE).
        assertEquals(
            setOf(
                "android.permission.CAMERA",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.INTERNET",
            ),
            perms,
        )
    }

    @Test fun backupDisabledCleartextBlockedAndNoDebuggableFlag() {
        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(!manifest.contains("android:debuggable"))
        assertTrue(manifest.contains("""android:usesCleartextTraffic="false""""))
        assertTrue(manifest.contains("""android:networkSecurityConfig="@xml/network_security_config""""))
        val netSec = File(moduleDir(), "src/main/res/xml/network_security_config.xml").readText()
        assertTrue(netSec.contains("""cleartextTrafficPermitted="false""""))
    }

    @Test fun onlyExpectedComponentsAreExported() {
        val tags = Regex("""<(activity|service|receiver|provider)\b[^>]*>""").findAll(manifest).map { it.value }.toList()
        val names = tags.filter { it.contains("""android:exported="true"""") }
            .map { Regex("""android:name="([^"]+)"""").find(it)!!.groupValues[1] }.toSet()
        assertEquals(setOf(".MainActivity", ".autofill.RefugeAutofillService"), names)
    }

    @Test fun noLoggingOrStackTracesInMainSources() {
        val bad = Regex("""\b(Log\.[a-z]\(|android\.util\.Log|println\(|print\(|printStackTrace\(|Timber\.|System\.(out|err)\.)""")
        val offenders = File(moduleDir(), "src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .filter { f -> f.readLines().any { l -> !l.trim().startsWith("//") && !l.trim().startsWith("*") && bad.containsMatchIn(l) } }
            .map { it.name }.toList()
        assertTrue("لاگ در: $offenders", offenders.isEmpty())
    }

    @Test fun noHardcodedSecretsPatterns() {
        val bad = Regex("""(?i)(storePassword|keyPassword)\s*=\s*"[^"]+"""")
        val files = listOf("build.gradle.kts").map { File(moduleDir(), it) } + File(moduleDir().parentFile, ".github/workflows").walkTopDown().filter { it.isFile }.toList()
        files.filter { it.exists() }.forEach { assertTrue("رمز ثابت در ${it.name}", !bad.containsMatchIn(it.readText())) }
    }
}
