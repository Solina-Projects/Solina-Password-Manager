package app.solina.passwordmanager.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class JalaliTest {
    @Test fun knownDates() {
        assertEquals(Triple(1403, 1, 1), gregorianToJalali(2024, 3, 20))
        assertEquals(Triple(1378, 10, 11), gregorianToJalali(2000, 1, 1))
        assertEquals(Triple(1402, 12, 29), gregorianToJalali(2024, 3, 19))
        assertEquals(Triple(1399, 12, 30), gregorianToJalali(2021, 3, 20)) // سال کبیسه
        assertEquals(Triple(1400, 1, 1), gregorianToJalali(2021, 3, 21))
    }

    @Test fun formatsWithPersianDigits() {
        assertEquals("۱۴۰۳/۰۱/۰۱", formatJalali(1710892800000L, ZoneOffset.UTC)) // 2024-03-20T00:00Z
        assertEquals("—", formatJalali(0))
    }
}
