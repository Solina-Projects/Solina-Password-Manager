package app.solina.refuge.core

import app.solina.refuge.core.crypto.Bip39Words
import java.security.SecureRandom
import kotlin.math.ln

/** مولد رمز تصادفی و عبارت عبور (Passphrase) با SecureRandom. */
object PasswordGenerator {
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?"
    private const val AMBIGUOUS = "O0oIl1|"

    private val COMMON_SUBSTRINGS = setOf(
        "password", "passw0rd", "qwerty", "admin", "123456", "12345678",
        "refuge", "tehran", "iran", "welcome", "iloveyou", "letmein",
        "dragon", "football", "monkey", "master", "secret", "login",
    )
    private val SEQUENTIAL_REGEX = Regex(
        "(0123|1234|2345|3456|4567|5678|6789|7890|9876|8765|7654|6543|5432|4321|abcd|bcde|cdef|qwer|wert|asdf|sdfg|zxcv|xcvb)",
        RegexOption.IGNORE_CASE,
    )
    private val REPEAT_REGEX = Regex("(.)\\1{2,}")
    private val YEAR_REGEX = Regex("(19|20|13|14)\\d{2}")

    fun generate(
        length: Int,
        upper: Boolean = true,
        lower: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
        avoidAmbiguous: Boolean = false,
        random: SecureRandom = SecureRandom(),
    ): String {
        val selected = mutableListOf<String>()
        if (upper) selected.add(UPPER)
        if (lower) selected.add(LOWER)
        if (digits) selected.add(DIGITS)
        if (symbols) selected.add(SYMBOLS)

        fun clean(pool: String) = if (avoidAmbiguous) pool.filter { it !in AMBIGUOUS } else pool
        val pools = selected.map { clean(it) }.filter { it.isNotEmpty() }
        val effective = if (pools.isEmpty()) listOf(clean(LOWER)) else pools

        val size = length.coerceIn(effective.size, 128)
        val all = effective.joinToString("")
        val chars = ArrayList<Char>(size)

        // حداقل یک کاراکتر از هر دستهٔ انتخاب‌شده
        for (pool in effective) chars.add(pool[random.nextInt(pool.length)])
        while (chars.size < size) chars.add(all[random.nextInt(all.length)])

        // درهم‌ریزی Fisher-Yates
        for (i in chars.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return chars.joinToString("")
    }

    /**
     * تولید عبارت عبور چندکلمه‌ای (Passphrase) از فهرست ۲۰۴۸ کلمه‌ای استاندارد (۱۱ بیت آنتروپی به‌ازای هر کلمه).
     * پنج کلمه = ۵۵ بیت، شش کلمه = ۶۶ بیت.
     */
    fun generatePassphrase(
        wordCount: Int = 5,
        separator: String = "-",
        random: SecureRandom = SecureRandom(),
    ): String {
        val words = Bip39Words.ENGLISH
        val count = wordCount.coerceIn(3, 12)
        return List(count) { words[random.nextInt(words.size)] }.joinToString(separator)
    }

    /** برآورد خام آنتروپی بر پایهٔ طول و دستهٔ کاراکترها (فقط برای رمزهای تصادفی معتبر است). */
    fun estimateBits(password: String): Double {
        if (password.isEmpty()) return 0.0
        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in '0'..'9' }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 24
        if (pool == 0) pool = 26
        return password.length * (ln(pool.toDouble()) / ln(2.0))
    }

    /**
     * برآورد واقع‌بینانه‌تر قدرت رمز با جریمهٔ واژگان رایج، توالی‌های کیبورد/عددی،
     * تکرار کاراکترها، سال‌های تقویمی و تنوع پایین کاراکترها.
     */
    fun effectiveBits(password: String): Double {
        if (password.isEmpty()) return 0.0
        val lower = password.lowercase()
        val uniqueRatio = password.toSet().size.toDouble() / password.length
        var bits = estimateBits(password) * (0.4 + 0.6 * uniqueRatio)
        if (COMMON_SUBSTRINGS.any { it in lower }) bits = minOf(bits, 28.0)
        if (SEQUENTIAL_REGEX.containsMatchIn(lower)) bits -= 18.0
        if (REPEAT_REGEX.containsMatchIn(password)) bits -= 12.0
        if (YEAR_REGEX.containsMatchIn(password)) bits -= 10.0
        return bits.coerceAtLeast(0.0)
    }
}
