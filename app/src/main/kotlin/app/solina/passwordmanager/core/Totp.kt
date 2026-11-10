package app.solina.passwordmanager.core

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** پیاده‌سازی TOTP طبق RFC 6238 (بر پایهٔ HOTP در RFC 4226). */
object Totp {
    fun generate(
        secret: ByteArray,
        timeMillis: Long,
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: String = "HmacSHA1",
    ): String {
        require(periodSeconds > 0) { "period must be positive" }
        require(digits in 6..8) { "digits must be 6..8" }

        val counter = timeMillis / 1000L / periodSeconds
        val message = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret, "RAW"))
        val hash = mac.doFinal(message)

        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)

        var modulus = 1L
        repeat(digits) { modulus *= 10L }
        return (binary.toLong() % modulus).toString().padStart(digits, '0')
    }

    /** میلی‌ثانیه‌های باقی‌مانده تا تغییر کد بعدی. */
    fun remainingMillis(timeMillis: Long, periodSeconds: Int = 30): Long {
        val period = periodSeconds * 1000L
        return period - (timeMillis % period)
    }

    /** گروه‌بندی نمایشی کد، مثلاً 482913 ← "482 913". */
    fun group(code: String): String {
        val half = code.length / 2
        return code.substring(0, half) + " " + code.substring(half)
    }
}
