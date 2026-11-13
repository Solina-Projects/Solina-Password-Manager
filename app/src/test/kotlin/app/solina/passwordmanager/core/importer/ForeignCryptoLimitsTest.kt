package app.solina.passwordmanager.core.importer

import org.junit.Assert.assertThrows
import org.junit.Test

class ForeignCryptoLimitsTest {
    private val password = "test-password".toCharArray()
    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun pbkdf2RejectsExcessiveIterationsBeforeDerivation() {
        assertThrows(ImportException::class.java) {
            ForeignCrypto.pbkdf2Sha256(password, salt, 1_000_001)
        }
    }

    @Test
    fun pbkdf2RejectsInvalidSaltAndOutputSize() {
        assertThrows(ImportException::class.java) {
            ForeignCrypto.pbkdf2Sha256(password, ByteArray(0), 10_000)
        }
        assertThrows(ImportException::class.java) {
            ForeignCrypto.pbkdf2Sha256(password, salt, 10_000, 128)
        }
    }

    @Test
    fun bitwardenArgon2RejectsExcessiveMemoryIterationsAndLanes() {
        assertThrows(ImportException::class.java) {
            ForeignCrypto.bitwardenArgon2(password, "user@example.test", 3, 129, 4)
        }
        assertThrows(ImportException::class.java) {
            ForeignCrypto.bitwardenArgon2(password, "user@example.test", 7, 64, 4)
        }
        assertThrows(ImportException::class.java) {
            ForeignCrypto.bitwardenArgon2(password, "user@example.test", 3, 64, 5)
        }
    }

    @Test
    fun scryptRejectsExcessiveMemoryAndInvalidCost() {
        assertThrows(ImportException::class.java) {
            ForeignCrypto.scrypt(password, salt, 1 shl 16, 8, 2)
        }
        assertThrows(ImportException::class.java) {
            ForeignCrypto.scrypt(password, salt, 1000, 8, 1)
        }
        assertThrows(ImportException::class.java) {
            ForeignCrypto.scrypt(password, salt, 1 shl 15, 8, 5)
        }
    }

    @Test
    fun normalAegisScryptParametersRemainSupported() {
        val derived = ForeignCrypto.scrypt(password, salt, 1 shl 12, 8, 1)
        assert(derived.size == 32)
        derived.fill(0)
    }
}
