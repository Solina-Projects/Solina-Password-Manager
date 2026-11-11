package app.solina.passwordmanager.core

/** رمزگشایی Base32 طبق RFC 4648 (برای رازهای TOTP). فاصله، خط تیره و = نادیده گرفته می‌شوند. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** null اگر ورودی خالی یا نامعتبر باشد. */
    fun decode(input: String): ByteArray? {
        val clean = input.uppercase().filter { it != ' ' && it != '-' && it != '=' }
        if (clean.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (ch in clean) {
            val v = ALPHABET.indexOf(ch)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xff)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        val bytes = out.toByteArray()
        return if (bytes.isEmpty()) null else bytes
    }

    /** رمزگذاری Base32 بدون padding، با حروف بزرگ (قالب رایج رازهای TOTP). */
    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer shr bits) and 31])
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }
}
