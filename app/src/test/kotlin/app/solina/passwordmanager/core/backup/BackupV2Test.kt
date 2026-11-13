package app.solina.passwordmanager.core.backup

import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import app.solina.passwordmanager.core.crypto.Bip39
import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupV2Test {
    private val fast = KdfParams(64, 1, 1)
    private val entropy = ByteArray(32) { it.toByte() }
    private val phrase get() = Bip39.entropyToWords(entropy).joinToString(" ")
    private val items = listOf(
        ItemPayload(ItemKind.Login, "GitHub", "ali", "SuperSecret!42", "github.com", "یادداشت", "JBSWY3DPEHPK3PXP", true, createdAt = 5, editedAt = 9, totpDigits = 8, totpPeriod = 60, totpAlgorithm = "SHA256"),
        ItemPayload(ItemKind.Card, "کارت", cardNumber = "6037997712345678", cardCvv = "123"),
    )

    @Test fun roundTripWithBothUnlockMethods() {
        val file = BackupV2.encode(items, "password-for-backup".toCharArray(), phrase, 1L, fast)
        assertEquals(items, BackupV2.decodeWithPassword(file, "password-for-backup".toCharArray()))
        assertEquals(items, BackupV2.decodeWithRecoveryPhrase(file, phrase))
    }

    @Test fun wrongPasswordAndWrongPhraseFailAuthentication() {
        val file = BackupV2.encode(items, "password-for-backup".toCharArray(), phrase, 1L, fast)
        assertThrows(AuthenticationFailedException::class.java) {
            BackupV2.decodeWithPassword(file, "incorrect-password".toCharArray())
        }
        val wrongEntropy = ByteArray(32) { (it + 1).toByte() }
        val wrongPhrase = Bip39.entropyToWords(wrongEntropy).joinToString(" ")
        assertThrows(AuthenticationFailedException::class.java) {
            BackupV2.decodeWithRecoveryPhrase(file, wrongPhrase)
        }
    }

    @Test fun invalidPhraseRejectedBeforeDecryption() {
        val file = BackupV2.encode(items, "password-for-backup".toCharArray(), phrase, 1L, fast)
        assertThrows(BackupFormatException::class.java) {
            BackupV2.decodeWithRecoveryPhrase(file, "not a valid recovery phrase")
        }
    }

    @Test fun tamperingWithEitherSlotOrPayloadFails() {
        val file = BackupV2.encode(items, "password-for-backup".toCharArray(), phrase, 1L, fast)
        val changedPasswordSlot = file.copyOf().also { it[60] = (it[60].toInt() xor 1).toByte() }
        assertThrows(AuthenticationFailedException::class.java) {
            BackupV2.decodeWithPassword(changedPasswordSlot, "password-for-backup".toCharArray())
        }
        val changedRecoverySlot = file.copyOf().also { it[120] = (it[120].toInt() xor 1).toByte() }
        assertThrows(AuthenticationFailedException::class.java) {
            BackupV2.decodeWithRecoveryPhrase(changedRecoverySlot, phrase)
        }
        val changedPayload = file.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(AuthenticationFailedException::class.java) {
            BackupV2.decodeWithPassword(changedPayload, "password-for-backup".toCharArray())
        }
    }

    @Test fun malformedHeadersAndKdfCapsAreRejected() {
        val file = BackupV2.encode(items, "password-for-backup".toCharArray(), phrase, 1L, fast)
        assertThrows(BackupFormatException::class.java) { BackupV2.decodeWithPassword(file.copyOf(20), "password-for-backup".toCharArray()) }
        val hostile = file.copyOf().also { it[9] = 0x7f }
        assertThrows(BackupFormatException::class.java) {
            BackupV2.decodeWithPassword(hostile, "password-for-backup".toCharArray())
        }
    }

    @Test fun bothUnlockSlotsAreAuthenticatedByPayload() {
        val file = BackupV2.encode(items, "password-for-backup".toCharArray(), phrase, 1L, fast)
        val alteredSlot = file.copyOf().also { it[50 + 60 + 4] = (it[50 + 60 + 4].toInt() xor 1).toByte() }
        assertThrows(AuthenticationFailedException::class.java) {
            BackupV2.decodeWithPassword(alteredSlot, "password-for-backup".toCharArray())
        }
    }

    @Test fun generatedFilesDifferAndDoNotExposeKnownPlaintext() {
        val a = BackupV2.encode(items, "password-for-backup".toCharArray(), phrase, 1L, fast)
        val b = BackupV2.encode(items, "password-for-backup".toCharArray(), phrase, 1L, fast)
        assertFalse(a.contentEquals(b))
        val raw = String(a, Charsets.ISO_8859_1)
        assertFalse(raw.contains("GitHub"))
        assertFalse(raw.contains("SuperSecret"))
        assertFalse(raw.contains("6037997712345678"))
    }
}
