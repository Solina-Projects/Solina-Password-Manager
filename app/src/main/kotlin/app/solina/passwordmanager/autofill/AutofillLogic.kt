package app.solina.passwordmanager.autofill

import android.text.InputType
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.VaultItem

enum class FieldKind { Username, Password, Other }

/** ویژگی‌های خام یک فیلد که از ساختار صفحه خوانده می‌شود (مستقل از کلاس‌های اندروید تا قابل تست باشد). */
class FieldInfo(
    val hints: List<String> = emptyList(),
    val inputType: Int = 0,
    val idEntry: String? = null,
    val hintText: String? = null,
    /** مقدار type در HTML (برای صفحات وب)، مثل password یا email. */
    val htmlType: String? = null,
)

object FieldClassifier {
    private val usernameWords = listOf("user", "login", "email", "mail", "phone", "mobile", "account", "نام_کاربری", "نام کاربری", "ایمیل", "موبایل", "شماره")
    private val passwordWords = listOf("pass", "pwd", "رمز", "گذرواژه")

    fun classify(f: FieldInfo): FieldKind {
        val hints = f.hints.map { it.lowercase() }
        // رمز جدید (ثبت‌نام/تغییر رمز) عمداً پر نمی‌شود.
        if ("newpassword" in hints) return FieldKind.Other
        if ("password" in hints) return FieldKind.Password
        if ("username" in hints || "emailaddress" in hints || "phone" in hints) return FieldKind.Username

        val cls = f.inputType and InputType.TYPE_MASK_CLASS
        val variation = f.inputType and InputType.TYPE_MASK_VARIATION
        val isPasswordInput = when (cls) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        if (isPasswordInput || f.htmlType.equals("password", ignoreCase = true)) return FieldKind.Password

        if (f.htmlType.equals("email", ignoreCase = true) || f.htmlType.equals("tel", ignoreCase = true)) return FieldKind.Username
        if (cls == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) return FieldKind.Username
        if (cls == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) return FieldKind.Username

        val text = listOfNotNull(f.idEntry, f.hintText).joinToString(" ").lowercase()
        if (text.isNotBlank()) {
            if (passwordWords.any { text.contains(it) }) return FieldKind.Password
            if (usernameWords.any { text.contains(it) }) return FieldKind.Username
        }
        return FieldKind.Other
    }

    /**
     * از فهرست فیلدهای صفحه (به ترتیب نمایش) یک جفت (نام کاربری، رمز) برمی‌گرداند.
     * نام کاربری نزدیک‌ترین فیلد متنی قبل از رمز است. اگر رمزی نبود ولی نام‌کاربری بود، فقط آن برگردانده می‌شود.
     * اندیس‌ها به همان فهرست ورودی اشاره می‌کنند.
     */
    fun pickLoginFields(kinds: List<FieldKind>): Pair<Int?, Int?>? {
        val pw = kinds.indexOf(FieldKind.Password)
        if (pw >= 0) {
            val user = (pw - 1 downTo 0).firstOrNull { kinds[it] == FieldKind.Username }
            return user to pw
        }
        val user = kinds.indexOf(FieldKind.Username)
        return if (user >= 0) user to null else null
    }
}

object DomainMatcher {
    /**
     * پسوندهای عمومی/میزبانی مشترک رایج تا هنگام نبود PSL کامل، ثبت دامنه‌های چندلایه عمومی
     * (مثل co.uk، com.ir یا github.io) باعث تطبیق سایر دامنه‌ها نشود.
     */
    private val KNOWN_MULTI_LABEL_PUBLIC_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk",
        "co.ir", "ac.ir", "org.ir", "gov.ir", "net.ir", "sch.ir", "id.ir",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp",
        "co.in", "net.in", "org.in", "gov.in",
        "com.br", "org.br", "com.tr", "com.cn", "com.mx", "co.kr", "co.za", "co.nz",
        "github.io", "gitlab.io", "pages.dev", "vercel.app", "netlify.app",
        "herokuapp.com", "blogspot.com", "wordpress.com", "appspot.com",
        "firebaseapp.com", "web.app", "cloudfront.net", "amazonaws.com", "azurewebsites.net",
    )

    private val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun isPublicSuffix(host: String): Boolean {
        val h = host.trim().lowercase().trimEnd('.')
        if (h.isEmpty() || h == "localhost") return false
        if (IPV4_REGEX.matches(h) || ':' in h) return false
        return '.' !in h || h in KNOWN_MULTI_LABEL_PUBLIC_SUFFIXES
    }

    /** "https://www.GitHub.com:443/login" ← "github.com". */
    fun normalizeHost(input: String): String {
        var s = input.trim().lowercase()
        if (s.startsWith("androidapp://")) return ""
        s = s.substringAfter("://", s)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@')
        s = s.substringBefore(':')
        s = s.trimEnd('.')
        return s.removePrefix("www.")
    }

    /** برابر یا زیردامنه؛ «evil-github.com»، «github.com.evil.com» و پسوندهای عمومی مطابق نیستند. */
    fun hostMatches(itemWebsite: String, requestHost: String): Boolean {
        val item = normalizeHost(itemWebsite)
        val req = normalizeHost(requestHost)
        if (item.isEmpty() || req.isEmpty()) return false
        if (item != "localhost" && isPublicSuffix(item)) return false
        if (IPV4_REGEX.matches(item)) return req == item
        return req == item || req.endsWith(".$item")
    }
}

/** منطق انتخاب ورودی‌ها برای پیشنهاد Autofill. */
object AutofillMatcher {
    const val MAX_SUGGESTIONS = 8

    fun logins(items: List<VaultItem>) = items.filter { it.payload.kind == ItemKind.Login && (it.payload.username.isNotEmpty() || it.payload.password.isNotEmpty()) }

    private fun extractPackageBinding(website: String): Pair<String, String?>? {
        val raw = website.trim().lowercase()
        if (!raw.startsWith("androidapp://")) return null
        val rest = raw.removePrefix("androidapp://").substringBefore('/')
        val pkg = rest.substringBefore('@').trim()
        val cert = rest.substringAfter('@', "").trim().takeIf { it.isNotEmpty() }
        if (pkg.isEmpty()) return null
        return pkg to cert
    }

    /**
     * صفحهٔ وب: فقط ورودی‌هایی که دامنه‌شان با رعایت مرز پسوند عمومی مطابقت دارد.
     * برنامهٔ بومی (بدون دامنه): فقط اتصال صریح `androidapp://<pkg>` (با بررسی اختیاری `@<sha256>`)
     * یا تطبیق با بخش‌های غیرعمومی نام بسته. هرگز کل ورودی‌های خزانه به‌عنوان fallback افشا نمی‌شوند.
     */
    fun match(
        items: List<VaultItem>,
        webHost: String?,
        packageName: String?,
        packageCertSha256: String? = null,
    ): List<VaultItem> {
        val all = logins(items)
        if (!webHost.isNullOrBlank()) {
            return all.filter { DomainMatcher.hostMatches(it.payload.website, webHost) }.take(MAX_SUGGESTIONS)
        }
        val pkg = packageName?.trim()?.lowercase().orEmpty()
        if (pkg.isEmpty()) return emptyList()

        val explicitMatches = all.filter { item ->
            val binding = extractPackageBinding(item.payload.website) ?: return@filter false
            if (binding.first != pkg) return@filter false
            val expectedCert = binding.second ?: return@filter true
            packageCertSha256 != null && expectedCert == packageCertSha256.lowercase()
        }
        if (explicitMatches.isNotEmpty()) return explicitMatches.take(MAX_SUGGESTIONS)

        val parts = pkg.split('.').filter { it.length >= 4 && it !in GENERIC }
        if (parts.isEmpty()) return emptyList()
        return all.filter { i ->
            if (i.payload.website.trim().lowercase().startsWith("androidapp://")) return@filter false
            val hay = (i.payload.title + " " + i.payload.website).lowercase()
            parts.any { hay.contains(it) }
        }.take(MAX_SUGGESTIONS)
    }

    private val GENERIC = setOf("android", "mobile", "app", "apps", "main", "client", "google", "com", "org", "net")
}
