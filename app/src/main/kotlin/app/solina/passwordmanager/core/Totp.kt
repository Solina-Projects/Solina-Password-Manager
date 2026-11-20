package app.solina.passwordmanager.core

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** نوع کد احراز هویت دومرحله‌ای: زمان‌محور (TOTP)، شمارنده‌محور (HOTP) یا استیم (Steam). */
enum class OtpType(val code: String) {
    TOTP("totp"),
    HOTP("hotp"),
    /** کد ۵حرفی استیم (Steam Guard) با الفبای ۲۶حرفی مخصوص؛ SHA1 و دورهٔ ۳۰ ثانیه. */
    STEAM("steam");

    companion object {
        fun fromCode(code: String?): OtpType = when (code?.lowercase()) {
            "hotp" -> HOTP
            "steam" -> STEAM
            else -> TOTP
        }
    }
}

/**
 * پیاده‌سازی استاندارد احراز هویت دومرحله‌ای:
 * - HOTP مطابق RFC 4226 (شمارنده‌محور)
 * - TOTP مطابق RFC 6238 (زمان‌محور)
 * پشتیبانی از کدهای ۶ و ۸ رقمی و الگوریتم‌های HmacSHA1، HmacSHA256 و HmacSHA512.
 */
object Totp {
    /** تولید کد HOTP (RFC 4226) برای شمارندهٔ [counter]. */
    fun generateHotp(
        secret: ByteArray,
        counter: Long,
        digits: Int = 6,
        algorithm: String = "HmacSHA1",
    ): String {
        require(digits in 6..8) { "digits must be 6..8" }
        require(counter >= 0L) { "counter must be non-negative" }
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret, algorithm))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var modulus = 1L
        repeat(digits) { modulus *= 10L }
        return (binary.toLong() % modulus).toString().padStart(digits, '0')
    }

    /** تولید کد TOTP (RFC 6238) برای زمان [timeMillis]. */
    fun generate(
        secret: ByteArray,
        timeMillis: Long,
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: String = "HmacSHA1",
    ): String {
        require(periodSeconds > 0) { "periodSeconds must be positive" }
        val counter = Math.floorDiv(timeMillis, periodSeconds * 1000L)
        return generateHotp(secret, counter, digits, algorithm)
    }

    /** میلی‌ثانیه‌های باقی‌مانده تا تغییر کد بعدی. */
    fun remainingMillis(timeMillis: Long, periodSeconds: Int = 30): Long {
        val period = periodSeconds * 1000L
        return period - (timeMillis % period)
    }

    /**
     * کد استیم (Steam Guard، ۵ حرفی) — همان HMAC-SHA1 زمان‌محور، ولی با الفبای ۲۶حرفی
     * «23456789BCDFGHJKMNPQRTVWXY» و تقسیم پی‌درپی مقدار ۳۱بیتی بر ۲۶. همیشه ۵ حرف و
     * همیشه SHA1 با دورهٔ ۳۰ ثانیه؛ با TOTP استاندارد (ارقام) فرق دارد و قابل جایگزینی نیست.
     */
    val STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY"

    fun generateSteam(secret: ByteArray, timeMillis: Long, periodSeconds: Int = 30): String {
        require(periodSeconds > 0) { "periodSeconds must be positive" }
        val counter = Math.floorDiv(timeMillis, periodSeconds * 1000L)
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        var full = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val sb = StringBuilder()
        repeat(5) {
            sb.append(STEAM_ALPHABET[full % STEAM_ALPHABET.length])
            full /= STEAM_ALPHABET.length
        }
        return sb.toString()
    }

    /** گروه‌بندی نمایشی کد، مثلاً 482913 ← "482 913" یا 12345678 ← "1234 5678". */
    fun group(code: String): String {
        val half = code.length / 2
        return code.substring(0, half) + " " + code.substring(half)
    }
}
