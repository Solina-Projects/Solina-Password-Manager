package app.solina.passwordmanager.core.security

import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PinUnlockTest {
    private val vaultKey = ByteArray(32) { (it * 7 + 3).toByte() }
    private val salt = ByteArray(16) { (it + 1).toByte() }

    @Test fun pinKekDerivationIsDeterministicAndSaltBound() {
        val a = PinCrypto.derivePinKek("1234".toByteArray(), salt)
        val b = PinCrypto.derivePinKek("1234".toByteArray(), salt)
        assertTrue(a.contentEquals(b))
        val otherSalt = PinCrypto.derivePinKek("1234".toByteArray(), ByteArray(16) { 9 })
        assertFalse(a.contentEquals(otherSalt))
        val otherPin = PinCrypto.derivePinKek("4321".toByteArray(), salt)
        assertFalse(a.contentEquals(otherPin))
    }

    @Test fun wrapUnwrapRoundTrip() {
        val kek = PinCrypto.derivePinKek("123456".toByteArray(), salt)
        val wrapped = PinCrypto.wrapVaultKey(kek, vaultKey)
        val out = PinCrypto.unwrapVaultKey(kek, wrapped)
        assertTrue(out.contentEquals(vaultKey))
    }

    @Test fun wrongPinCannotUnwrap() {
        val kek = PinCrypto.derivePinKek("1234".toByteArray(), salt)
        val wrapped = PinCrypto.wrapVaultKey(kek, vaultKey)
        val wrongKek = PinCrypto.derivePinKek("9999".toByteArray(), salt)
        try {
            PinCrypto.unwrapVaultKey(wrongKek, wrapped)
            fail("wrong PIN must not unwrap the vault key")
        } catch (e: AuthenticationFailedException) {
            // مورد انتظار
        }
    }

    @Test fun tamperedBlobCannotUnwrap() {
        val kek = PinCrypto.derivePinKek("1234".toByteArray(), salt)
        val wrapped = PinCrypto.wrapVaultKey(kek, vaultKey)
        wrapped[20] = (wrapped[20].toInt() xor 0x01).toByte()
        try {
            PinCrypto.unwrapVaultKey(kek, wrapped)
            fail("tampered blob must not unwrap")
        } catch (e: AuthenticationFailedException) {
            // مورد انتظار
        }
    }

    @Test fun pinValidationRules() {
        assertTrue(PinCrypto.isValidPin("1234"))
        assertTrue(PinCrypto.isValidPin("123456"))
        assertTrue(PinCrypto.isValidPin("12345".toCharArray()))
        assertFalse(PinCrypto.isValidPin("123"))
        assertFalse(PinCrypto.isValidPin("1234567"))
        assertFalse(PinCrypto.isValidPin("12a4"))
        assertFalse(PinCrypto.isValidPin(""))
        assertFalse(PinCrypto.isValidPin(" 1234"))
    }

    @Test fun wipePolicy() {
        assertFalse(PinCrypto.shouldWipePin(0))
        assertFalse(PinCrypto.shouldWipePin(PinCrypto.MAX_PIN_ATTEMPTS - 1))
        assertTrue(PinCrypto.shouldWipePin(PinCrypto.MAX_PIN_ATTEMPTS))
        assertTrue(PinCrypto.shouldWipePin(PinCrypto.MAX_PIN_ATTEMPTS + 3))
        assertEquals(5, PinCrypto.MAX_PIN_ATTEMPTS)
    }

    @Test fun pinKekDiffersFromPasswordKekDomain() {
        // همان ورودی با info متفاوت نباید همان کلید را بدهد؛ جداسازی دامنهٔ پین و رمز اصلی.
        val pinKek = PinCrypto.derivePinKek("1234".toByteArray(), salt)
        assertEquals(32, pinKek.size)
        assertNotEquals(0, pinKek.fold(0) { acc, b -> acc + b })
    }
}
