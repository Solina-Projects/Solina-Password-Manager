package app.solina.passwordmanager.core.storage

import app.solina.passwordmanager.core.PasswordGenerator

enum class Flag { Weak, Reused, NoTwoFa }

data class SecurityReport(
    val flags: Map<String, Set<Flag>>,
    /** ۰..۱۰۰؛ بر پایهٔ سهم ورودی‌های بدون مشکل (فقط ورودی‌های ورود). */
    val score: Int,
    val weak: Int,
    val reused: Int,
    val withoutTotp: Int,
)

/** تحلیل محلی؛ هیچ داده‌ای جایی ارسال نمی‌شود. */
object SecurityAnalyzer {
    const val WEAK_BITS = 50.0

    fun analyze(items: List<VaultItem>): SecurityReport {
        val logins = items.filter { it.payload.kind == ItemKind.Login && it.payload.password.isNotEmpty() }
        val counts = logins.groupingBy { it.payload.password }.eachCount()
        val flags = HashMap<String, Set<Flag>>()
        var weak = 0; var reused = 0; var noTotp = 0; var clean = 0
        for (i in logins) {
            val f = mutableSetOf<Flag>()
            if (PasswordGenerator.estimateBits(i.payload.password) < WEAK_BITS) { f += Flag.Weak; weak++ }
            if ((counts[i.payload.password] ?: 0) > 1) { f += Flag.Reused; reused++ }
            if (i.payload.totpSecret.isBlank()) { f += Flag.NoTwoFa; noTotp++ }
            // نبود 2FA هشدار است ولی از امتیاز کم نمی‌کند؛ امتیاز فقط ضعیف/تکراری را می‌سنجد.
            if (Flag.Weak !in f && Flag.Reused !in f) clean++
            if (f.isNotEmpty()) flags[i.id] = f
        }
        val score = if (logins.isEmpty()) 100 else (clean * 100) / logins.size
        return SecurityReport(flags, score, weak, reused, noTotp)
    }
}
