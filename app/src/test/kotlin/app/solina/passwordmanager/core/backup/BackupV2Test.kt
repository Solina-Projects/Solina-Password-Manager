package app.solina.passwordmanager.core.backup

import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import app.solina.passwordmanager.core.crypto.Bip39
import app.solina.passwordmanager.core.crypto.InvalidRecoveryPhraseException
import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import org.junit.Assert.assertArrayEquals
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

    @Test fun officialBip39TwentyFourWordZeroEntropyVector() {
        val expected = List(23) { "abandon" } + "art"
        assertEquals(expected, Bip39.entropyToWords(ByteArray(32)))
        assertArrayEquals(ByteArray(32), Bip39.wordsToEntropy(expected.joinToString(" ")))
    }

    @Test fun bip39RejectsTwentyFourKnownWordsWithInvalidChecksum() {
        val invalid = List(24) { "abandon" }.joinToString(" ")
        org.junit.Assert.assertThrows(InvalidRecoveryPhraseException::class.java) {
            Bip39.wordsToEntropy(invalid)
        }
    }

    @Test fun passwordDispatcherOpensV2AndV1PhrasePathIsRejected() {
        val v2 = BackupV2.encode(items, "password-for-backup".toCharArray(), phrase, 1L, fast)
        assertEquals(items, Backup.decode(v2, "password-for-backup".toCharArray()))

        val v1 = Backup.encode(items, "legacy-password".toCharArray(), 1L, fast)
        assertEquals(items, Backup.decode(v1, "legacy-password".toCharArray()))
        org.junit.Assert.assertThrows(BackupFormatException::class.java) {
            Backup.decodeWithRecoveryPhrase(v1, phrase)
        }
    }

    @Test fun truncatedTrailingAndSwappedSlotsAreRejected() {
        val file = BackupV2.encode(items, "password-for-backup".toCharArray(), phrase, 1L, fast)
        org.junit.Assert.assertThrows(BackupFormatException::class.java) {
            BackupV2.decodeWithPassword(file.copyOfRange(0, 110), "password-for-backup".toCharArray())
        }
        org.junit.Assert.assertThrows(Exception::class.java) {
            BackupV2.decodeWithPassword(file.copyOf(file.size - 1), "password-for-backup".toCharArray())
        }
        org.junit.Assert.assertThrows(AuthenticationFailedException::class.java) {
            BackupV2.decodeWithPassword(file + byteArrayOf(0), "password-for-backup".toCharArray())
        }

        val swapped = file.copyOf()
        val passwordSlot = file.copyOfRange(50, 110)
        val recoverySlot = file.copyOfRange(110, 170)
        recoverySlot.copyInto(swapped, 50)
        passwordSlot.copyInto(swapped, 110)
        org.junit.Assert.assertThrows(Exception::class.java) {
            BackupV2.decodeWithPassword(swapped, "password-for-backup".toCharArray())
        }

        val removedRecoverySlot = file.copyOfRange(0, 110) + file.copyOfRange(170, file.size)
        org.junit.Assert.assertThrows(Exception::class.java) {
            BackupV2.decodeWithPassword(removedRecoverySlot, "password-for-backup".toCharArray())
        }
    }

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
        val hostile = file.copyOf().also { it[6] = 0x7f }
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
