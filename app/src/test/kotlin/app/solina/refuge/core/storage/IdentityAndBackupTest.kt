package app.solina.refuge.core.storage

import app.solina.refuge.core.OtpType
import app.solina.refuge.core.backup.Backup
import app.solina.refuge.core.backup.BackupFormatException
import app.solina.refuge.core.crypto.InvalidRecoveryPhraseException
import app.solina.refuge.core.crypto.KdfParams
import app.solina.refuge.core.crypto.VaultCrypto
import app.solina.refuge.core.crypto.VaultHeader
import app.solina.refuge.core.crypto.randomBytes
import app.solina.refuge.core.security.VaultSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityAndBackupTest {
    private val fastKdf = KdfParams(memoryKiB = 8 * 1024, iterations = 1, parallelism = 1)

    @Test fun identityAndExtendedFieldsRoundTrip() {
        val p = ItemPayload(
            kind = ItemKind.Identity,
            title = "Personal ID",
            category = "Personal",
            icon = "🪪",
            favorite = true,
            fullName = "Sara Rezai",
            email = "sara@example.com",
            phone = "+989120000000",
            nationalId = "0012345678",
            company = "Refuge Labs",
            address = "Valiasr St 10",
            city = "Tehran",
            postalCode = "1234567890",
            country = "Iran",
            createdAt = 1000L,
            editedAt = 2000L,
        )
        val restored = ItemPayload.fromBytes(p.toBytes())
        assertEquals(p, restored)
    }

    @Test fun legacyV1JsonDeserializesWithSafeDefaults() {
        val v1 = """{"kind":"login","title":"Old","username":"u","password":"p","totp":"JBSWY3DPEHPK3PXP"}""".toByteArray()
        val p = ItemPayload.fromBytes(v1)
        assertEquals(ItemKind.Login, p.kind)
        assertEquals(OtpType.TOTP, p.otpType)
        assertEquals(0L, p.totpCounter)
        assertEquals("", p.category)
        assertEquals("", p.fullName)
    }

    @Test fun authenticatorOnlyBackupAndInspectWork() {
        val items = listOf(
            ItemPayload(kind = ItemKind.Login, title = "NoOTP", username = "u", password = "secret"),
            ItemPayload(kind = ItemKind.Login, title = "GitHub", username = "dev", totpSecret = "JBSWY3DPEHPK3PXP", otpType = OtpType.TOTP),
            ItemPayload(kind = ItemKind.Login, title = "Token", username = "ops", totpSecret = "MZXW6YTBOI", otpType = OtpType.HOTP, totpCounter = 12L),
        )
        val blob = Backup.encodeAuthenticatorOnly(items, "backup-pass-123".toCharArray(), 1700000000L, fastKdf)
        assertTrue(Backup.isRefugeBackup(blob))
        assertFalse(Backup.isRefugeBackup("{}".toByteArray()))
        val info = Backup.inspect(blob)
        assertEquals(1, info.version)
        assertEquals(fastKdf, info.kdf)
        assertEquals(blob.size, info.totalBytes)
        assertTrue(Backup.formatByteSize(512).endsWith("B"))
        assertTrue(Backup.formatByteSize(4096).endsWith("KB"))
        assertTrue(Backup.formatByteSize(2 * 1024 * 1024).endsWith("MB"))

        val decoded = Backup.decode(blob, "backup-pass-123".toCharArray())
        assertEquals(2, decoded.size)
        assertTrue(decoded.none { it.title == "NoOTP" })
        assertEquals(OtpType.HOTP, decoded[1].otpType)
        assertEquals(12L, decoded[1].totpCounter)
    }

    @Test fun fullBackupRestorePreservesDistinctIdentitiesInRepository() = runBlocking {
        val items = listOf(
            ItemPayload(kind = ItemKind.Identity, title = "ID", fullName = "Alice", email = "alice@example.com", nationalId = "111"),
            ItemPayload(kind = ItemKind.Identity, title = "ID", fullName = "Bob", email = "bob@example.com", nationalId = "222"),
            ItemPayload(kind = ItemKind.Card, title = "Bank", bankName = "Melli", cardNumber = "6037997712345678", cardPin = "1234"),
            ItemPayload(kind = ItemKind.Login, title = "HOTP", username = "ops", totpSecret = "MZXW6YTBOI", otpType = OtpType.HOTP, totpCounter = 3L),
        )
        val blob = Backup.encode(items, "restore-pass-123".toCharArray(), 1700000000L, fastKdf)
        val decoded = Backup.decode(blob, "restore-pass-123".toCharArray())

        val session = VaultSession().also { it.unlock(randomBytes(32)) }
        var n = 0
        val repository = VaultRepository(FakeStore(), session, clock = { 10_000L }, newId = { "r${++n}" })
        val stats = repository.importItems(decoded)
        assertEquals(4, stats.added)
        assertEquals(0, stats.skipped)
        assertEquals(4, repository.items.value.size)

        // Re-importing the exact same backup should deduplicate cleanly
        val stats2 = repository.importItems(decoded)
        assertEquals(0, stats2.added)
        assertEquals(4, stats2.skipped)
    }

    @Test fun authenticatorOnlyBackupRejectsEmptyOtpSetAndInspectRejectsGarbage() {
        assertThrows(BackupFormatException::class.java) {
            Backup.encodeAuthenticatorOnly(
                listOf(ItemPayload(kind = ItemKind.Note, title = "OnlyNote", note = "hi")),
                "backup-pass-123".toCharArray(),
                1700000000L,
                fastKdf,
            )
        }
        assertThrows(BackupFormatException::class.java) {
            Backup.inspect("not-a-solina-backup".toByteArray())
        }
    }

    @Test fun backupPreservesOriginalVaultHeaderMasterPasswordAnd24Words() {
        val originalPw = "MyOriginalMaster#2026".toCharArray()
        val created = VaultCrypto.create(originalPw, fastKdf)
        val originalPhrase = created.recoveryWords.joinToString(" ")
        val items = listOf(ItemPayload(kind = ItemKind.Login, title = "Mail", username = "a@b.c", password = "p1"))

        val blob = Backup.encode(
            items = items,
            password = "DifferentBackupPw#99".toCharArray(),
            now = 1700000000L,
            params = fastKdf,
            vaultHeader = created.header.encode(),
        )
        val decoded = Backup.decodeFull(blob, "DifferentBackupPw#99".toCharArray())
        assertEquals(1, decoded.items.size)
        val restoredHeader = VaultHeader.decode(decoded.vaultHeader!!)

        // Exact original master password AND exact original 24 words unlock the restored header!
        val vkByPw = VaultCrypto.unlockWithPassword(restoredHeader, "MyOriginalMaster#2026".toCharArray())
        val vkByWords = VaultCrypto.unlockWithRecovery(restoredHeader, originalPhrase)
        assertTrue(vkByPw.contentEquals(created.vaultKey))
        assertTrue(vkByWords.contentEquals(created.vaultKey))
    }

    @Test fun createWithExistingRecoveryPreservesExact24Words() {
        val original = VaultCrypto.create("InitialPass#1234".toCharArray(), fastKdf)
        val phrase = original.recoveryWords.joinToString(" ")

        val recreated = VaultCrypto.createWithExistingRecovery("InitialPass#1234".toCharArray(), phrase, fastKdf)
        assertEquals(original.recoveryWords, recreated.recoveryWords)
        val vkByWords = VaultCrypto.unlockWithRecovery(recreated.header, phrase)
        assertTrue(vkByWords.contentEquals(recreated.vaultKey))

        assertThrows(InvalidRecoveryPhraseException::class.java) {
            VaultCrypto.createWithExistingRecovery("InitialPass#1234".toCharArray(), "invalid words here", fastKdf)
        }
    }
}
