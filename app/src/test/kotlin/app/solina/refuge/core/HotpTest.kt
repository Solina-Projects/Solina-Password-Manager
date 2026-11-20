package app.solina.refuge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** تست رسمی RFC 4226 Appendix D (هر ۱۰ بردار شمارندهٔ ۰ تا ۹). */
class HotpTest {
    private val rfcSecret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test fun rfc4226AppendixDVectors() {
        val expected = listOf(
            "755224",
            "287082",
            "359152",
            "969429",
            "338314",
            "254676",
            "287922",
            "162583",
            "399871",
            "520489",
        )
        expected.forEachIndexed { counter, want ->
            assertEquals("counter=$counter", want, Totp.generateHotp(rfcSecret, counter.toLong(), digits = 6, algorithm = "HmacSHA1"))
        }
    }

    @Test fun hotpSupports8DigitsAndSha256AndSha512() {
        val c8 = Totp.generateHotp(rfcSecret, 0L, digits = 8, algorithm = "HmacSHA1")
        assertEquals(8, c8.length)
        val c256 = Totp.generateHotp(rfcSecret, 1L, digits = 6, algorithm = "HmacSHA256")
        assertEquals(6, c256.length)
        val c512 = Totp.generateHotp(rfcSecret, 2L, digits = 8, algorithm = "HmacSHA512")
        assertEquals(8, c512.length)
    }

    @Test fun hotpRejectsNegativeCounter() {
        assertThrows(IllegalArgumentException::class.java) {
            Totp.generateHotp(rfcSecret, -1L)
        }
    }
}
