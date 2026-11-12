package app.solina.passwordmanager.hardening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** نگهبان‌های رگرسیون: اگر کسی ناخواسته مجوز، لاگ یا مؤلفهٔ export‌شده اضافه کند CI قرمز می‌شود. */
class GuardTest {
    private fun moduleDir(): File {
        var d = File("").absoluteFile
        if (!File(d, "src/main").exists() && File(d, "app/src/main").exists()) d = File(d, "app")
        return d
    }

    /** متن مانیفست بدون کامنت‌های XML (تا اشاره‌ها در توضیحات باعث مثبت کاذب نشود). */
    private val manifest get() = File(moduleDir(), "src/main/AndroidManifest.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    @Test fun onlyCameraAndNotificationPermissionsAndNoNetwork() {
        val perms = Regex("""<uses-permission[^>]*android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        // POST_NOTIFICATIONS فقط برای نوتیف محلی خطا است (0.0.11-alpha5، به درخواست کاربر):
        // هیچ داده‌ای ارسال نمی‌شود و متن خطا در نوتیف نمی‌آید. INTERNET همچنان ممنوع است.
        assertEquals(setOf("android.permission.CAMERA", "android.permission.POST_NOTIFICATIONS"), perms)
        assertTrue("INTERNET نباید اضافه شود", !manifest.contains("INTERNET"))
    }

    @Test fun backupDisabledAndNoDebuggableFlag() {
        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(!manifest.contains("android:debuggable"))
        assertTrue(!manifest.contains("usesCleartextTraffic"))
    }

    @Test fun onlyExpectedComponentsAreExported() {
        val exported = Regex("""<(activity|service|receiver|provider)\b[^>]*?android:name="([^"]+)"[^>]*?android:exported="true"""", RegexOption.DOT_MATCHES_ALL)
        // هر تگ را جدا بررسی می‌کنیم تا regex از یک تگ به تگ بعدی نپرد.
        val tags = Regex("""<(activity|service|receiver|provider)\b[^>]*>""").findAll(manifest).map { it.value }.toList()
        val names = tags.filter { it.contains("""android:exported="true"""") }
            .map { Regex("""android:name="([^"]+)"""").find(it)!!.groupValues[1] }.toSet()
        assertEquals(setOf(".MainActivity", ".autofill.SolinaAutofillService"), names)
        assertTrue(exported.pattern.isNotEmpty())
    }

    @Test fun noLoggingOrStackTracesInMainSources() {
        val bad = Regex("""\b(Log\.[a-z]\(|println\(|printStackTrace\(|Timber\.)""")
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
