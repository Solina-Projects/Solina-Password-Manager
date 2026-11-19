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
    private val MULTI_LABEL_PUBLIC_SUFFIXES = setOf(
        "co.ir", "com.ir", "org.ir", "net.ir", "gov.ir", "ac.ir", "sch.ir", "id.ir",
        "co.uk", "org.uk", "ac.uk", "gov.uk",
        "com.au", "net.au", "org.au", "edu.au",
        "co.jp", "ne.jp", "or.jp",
        "com.br", "com.tr", "co.in", "co.nz", "com.cn", "co.kr",
    )

    /** "https://www.GitHub.com:443/login" ← "github.com". */
    fun normalizeHost(input: String): String {
        var s = input.trim().lowercase()
        s = s.substringAfter("://", s)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@')
        s = s.substringBefore(':')
        s = s.trimEnd('.')
        return s.removePrefix("www.")
    }

    /**
     * بررسی امنیت پروتکل صفحه وب در Autofill:
     * تنها HTTPS (یا localhost / 127.0.0.1 برای توسعه) مجاز به پر کردن خودکار است تا
     * حملهٔ تنزل به HTTP در شبکه‌های عمومی نتواند رمزها را سرقت کند.
     */
    fun isSafeWebOrigin(scheme: String?, host: String?): Boolean {
        val normalized = host?.let(::normalizeHost).orEmpty()
        if (normalized.isEmpty()) return false
        if (normalized == "localhost" || normalized == "127.0.0.1") return true
        // در Android 8 (API 26-27) که webScheme وجود ندارد، scheme برابر null است.
        return scheme == null || scheme.equals("https", ignoreCase = true)
    }

    /** برابر یا زیردامنه؛ «evil-github.com»، «github.com.evil.com» و پسوندهای عمومی مانند «co.uk» یا «com.ir» رد می‌شوند. */
    fun hostMatches(itemWebsite: String, requestHost: String): Boolean {
        val item = normalizeHost(itemWebsite)
        val req = normalizeHost(requestHost)
        if (item.isEmpty() || req.isEmpty()) return false
        if (item in MULTI_LABEL_PUBLIC_SUFFIXES || req in MULTI_LABEL_PUBLIC_SUFFIXES) return false
        if (!item.contains('.') && item != "localhost") return false
        return req == item || req.endsWith(".$item")
    }
}

/** منطق انتخاب ورودی‌ها برای پیشنهاد Autofill. */
object AutofillMatcher {
    const val MAX_SUGGESTIONS = 8

    fun logins(items: List<VaultItem>) =
        items.filter { it.payload.kind == ItemKind.Login && (it.payload.username.isNotEmpty() || it.payload.password.isNotEmpty()) }

    /**
     * صفحهٔ وب: فقط ورودی‌هایی که دامنه‌شان مطابقت دارد (سخت‌گیرانه، برای جلوگیری از فیشینگ).
     * برنامهٔ بومی (بدون دامنه): فقط ورودی‌هایی که عنوان یا سایتشان با بخش‌های نام بسته می‌خواند؛
     * در نبود تطابق، فهرست خالی برمی‌گردد تا هیچ رمزی بی‌مورد به برنامه‌های ناشناس افشا نشود.
     */
    fun match(items: List<VaultItem>, webHost: String?, packageName: String?): List<VaultItem> {
        val all = logins(items)
        if (!webHost.isNullOrBlank()) {
            return all.filter { DomainMatcher.hostMatches(it.payload.website, webHost) }.take(MAX_SUGGESTIONS)
        }
        val parts = packageName.orEmpty().lowercase().split('.').filter { it.length >= 4 && it !in GENERIC }
        if (parts.isEmpty()) return emptyList()
        return all.filter { i ->
            val hay = (i.payload.title + " " + i.payload.website).lowercase()
            parts.any { hay.contains(it) }
        }.take(MAX_SUGGESTIONS)
    }

    private val GENERIC = setOf("android", "mobile", "app", "apps", "main", "client", "google", "com", "org", "net")
}
