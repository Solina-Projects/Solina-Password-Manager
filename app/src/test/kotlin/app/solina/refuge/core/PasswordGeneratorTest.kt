package app.solina.refuge.core

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

    @Test
    fun passphraseGeneratesWordsFromBip39List() {
        val phrase = PasswordGenerator.generatePassphrase(wordCount = 6, separator = "-")
        val parts = phrase.split("-")
        assertEquals(6, parts.size)
        assertTrue(parts.all { it.isNotEmpty() && it.all { ch -> ch in 'a'..'z' } })
    }

    @Test
    fun effectiveBitsPenalizesPredictablePasswords() {
        assertTrue(PasswordGenerator.effectiveBits("Password123!") < 50.0)
        assertTrue(PasswordGenerator.effectiveBits("qwerty2024!") < 50.0)
        assertTrue(PasswordGenerator.effectiveBits("aaaaaaaaaaaa") < 30.0)
        assertTrue(PasswordGenerator.effectiveBits(PasswordGenerator.generate(16)) >= 70.0)
    }
}
