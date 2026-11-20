package app.solina.refuge.core.security

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * بررسی اختیاری افشای رمز عبور با پروتکل k-Anonymity (Have I Been Pwned Pwned Passwords v3).
 * رمز عبور و هش کامل SHA-1 هرگز از دستگاه خارج نمی‌شوند؛ فقط ۵ کاراکتر اول روی HTTPS ارسال می‌شود.
 */
object BreachChecker {
    private const val ENDPOINT = "https://api.pwnedpasswords.com/range/"

    fun sha1UpperHex(password: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(password.toByteArray(Charsets.UTF_8))
        return buildString(40) {
            for (b in digest) {
                val v = b.toInt() and 0xff
                append("0123456789ABCDEF"[v ushr 4])
                append("0123456789ABCDEF"[v and 0x0f])
            }
        }
    }

    fun prefix5(sha1Hex: String): String = sha1Hex.take(5).uppercase()

    fun suffix35(sha1Hex: String): String = sha1Hex.drop(5).uppercase()

    fun parseRangeResponse(responseBody: String, expectedSuffix: String): Int {
        val target = expectedSuffix.uppercase()
        for (line in responseBody.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val colon = trimmed.indexOf(':')
            if (colon <= 0) continue
            if (trimmed.substring(0, colon).equals(target, ignoreCase = true)) {
                return trimmed.substring(colon + 1).trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
            }
        }
        return 0
    }

    fun checkOnline(password: String, timeoutMillis: Int = 8_000): Int? {
        if (password.isEmpty()) return 0
        val hash = sha1UpperHex(password)
        val prefix = prefix5(hash)
        val suffix = suffix35(hash)
        return runCatching {
            val conn = (URL(ENDPOINT + prefix).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                setRequestProperty("Add-Padding", "true")
                setRequestProperty("User-Agent", "Solina-Refuge")
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseRangeResponse(body, suffix)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
