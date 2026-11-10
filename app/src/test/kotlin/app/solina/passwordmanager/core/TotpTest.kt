package app.solina.passwordmanager.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TotpTest {
    private val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    /** بردارهای رسمی RFC 6238 پیوست B (SHA-1، ۸ رقم). */
    @Test
    fun rfc6238Sha1Vectors() {
        val vectors = listOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130",
        )
        for ((seconds, expected) in vectors) {
            assertEquals("t=$seconds", expected, Totp.generate(secret, seconds * 1000L, 30, 8))
        }
    }

    @Test
    fun sixDigitCodeIsLastSixOfEight() {
        assertEquals("287082", Totp.generate(secret, 59_000L, 30, 6))
    }

    @Test
    fun remainingMillisIsWithinPeriod() {
        assertEquals(30_000L, Totp.remainingMillis(0L))
        assertEquals(1_000L, Totp.remainingMillis(29_000L))
    }
}
