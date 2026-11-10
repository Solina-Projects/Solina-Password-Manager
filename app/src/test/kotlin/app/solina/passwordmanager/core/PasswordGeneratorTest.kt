package app.solina.passwordmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    @Test
    fun respectsLength() {
        assertEquals(24, PasswordGenerator.generate(24).length)
    }

    @Test
    fun containsEverySelectedClass() {
        repeat(200) {
            val p = PasswordGenerator.generate(12)
            assertTrue(p.any { it in 'a'..'z' })
            assertTrue(p.any { it in 'A'..'Z' })
            assertTrue(p.any { it in '0'..'9' })
            assertTrue(p.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun excludesAmbiguousCharactersWhenAsked() {
        repeat(200) {
            val p = PasswordGenerator.generate(32, avoidAmbiguous = true)
            assertFalse(p.any { it in "O0oIl1|" })
        }
    }

    @Test
    fun fallsBackToLowercaseWhenNothingSelected() {
        val p = PasswordGenerator.generate(10, upper = false, lower = false, digits = false, symbols = false)
        assertTrue(p.all { it in 'a'..'z' })
    }
}
