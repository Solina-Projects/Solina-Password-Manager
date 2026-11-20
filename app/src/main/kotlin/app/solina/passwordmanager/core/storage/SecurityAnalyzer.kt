package app.solina.passwordmanager.core.storage

import app.solina.passwordmanager.core.PasswordGenerator
import app.solina.passwordmanager.core.security.CommonPasswords

enum class Flag { Weak, Reused, NoTwoFa, InsecureHttp, Old, Common }

data class SecurityReport(
    val flags: Map<String, Set<Flag>>,
    /** ۰..۱۰۰؛ بر پایهٔ سهم ورودی‌های بدون مشکل (فقط ورودی‌های ورود). */
    val score: Int,
    val weak: Int,
    val reused: Int,
    val withoutTotp: Int,
    val insecureHttp: Int = 0,
    val oldPasswords: Int = 0,
    /** تعداد رمزهایی که در فهرست آفلاین رمزهای رایج هستند (فاز ۴). */
    val commonPasswords: Int = 0,
)

/** تحلیل محلی؛ هیچ داده‌ای جایی ارسال نمی‌شود. */
object SecurityAnalyzer {
    const val WEAK_BITS = 50.0
    /** رمز عبور قدیمی: بیش از یک سال از آخرین ویرایش گذشته باشد. */
    const val OLD_PASSWORD_MILLIS = 365L * 24 * 3600_000L

    private fun isInsecureHttp(website: String): Boolean {
        val w = website.trim().lowercase()
        if (!w.startsWith("http://")) return false
        val host = w.removePrefix("http://").substringBefore('/').substringBefore(':')
        return host != "localhost" && host != "127.0.0.1"
    }

    fun analyze(items: List<VaultItem>, now: Long = System.currentTimeMillis()): SecurityReport {
        val logins = items.filter { it.payload.kind == ItemKind.Login && it.payload.password.isNotEmpty() }
        val counts = logins.groupingBy { it.payload.password }.eachCount()
        val flags = HashMap<String, Set<Flag>>()
        var weak = 0
        var reused = 0
        var noTotp = 0
        var insecureHttp = 0
        var oldPasswords = 0
        var commonPasswords = 0
        var clean = 0
        for (i in logins) {
            val f = mutableSetOf<Flag>()
            if (PasswordGenerator.effectiveBits(i.payload.password) < WEAK_BITS) {
                f += Flag.Weak
                weak++
            }
            // رمز رایج (فاز ۴): بررسی آفلاین با فهرست داخلی؛ رمز هرگز از دستگاه خارج نمی‌شود.
            // دلیل جدا از Weak نگه داشته شده تا کاربر بداند مشکل «رایج‌بودن» است نه کوتاهی/سادگی.
            if (CommonPasswords.isCommon(i.payload.password)) {
                f += Flag.Common
                commonPasswords++
            }
            if ((counts[i.payload.password] ?: 0) > 1) {
                f += Flag.Reused
                reused++
            }
            if (i.payload.totpSecret.isBlank()) {
                f += Flag.NoTwoFa
                noTotp++
            }
            if (isInsecureHttp(i.payload.website)) {
                f += Flag.InsecureHttp
                insecureHttp++
            }
            // رمز قدیمی فقط هشدار است (مثل نبود 2FA) و روی امتیاز اثر نمی‌گذارد.
            if (i.payload.editedAt > 0 && now - i.payload.editedAt > OLD_PASSWORD_MILLIS) {
                f += Flag.Old
                oldPasswords++
            }
            // نبود 2FA و HTTP هشدار هستند؛ امتیاز اصلی ضعف و تکرار رمز را می‌سنجد.
            // رمز رایج هم مثل ضعف و تکرار، امتیاز را کم می‌کند.
            if (Flag.Weak !in f && Flag.Reused !in f && Flag.Common !in f) clean++
            if (f.isNotEmpty()) flags[i.id] = f
        }
        val score = if (logins.isEmpty()) 100 else (clean * 100) / logins.size
        return SecurityReport(flags, score, weak, reused, noTotp, insecureHttp, oldPasswords, commonPasswords)
    }
}
