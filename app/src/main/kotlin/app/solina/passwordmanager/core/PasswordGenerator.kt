package app.solina.passwordmanager.core

import java.security.SecureRandom
import kotlin.math.ln

/** مولد رمز تصادفی با SecureRandom. */
object PasswordGenerator {
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?"
    private const val AMBIGUOUS = "O0oIl1|"

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
}
