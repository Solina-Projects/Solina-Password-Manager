package app.solina.passwordmanager.core.storage

import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.OtpType
import app.solina.passwordmanager.core.backup.Backup
import app.solina.passwordmanager.core.backup.BackupFormatException
import org.junit.Assert.assertEquals
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
            company = "Solina Labs",
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
        val info = Backup.inspect(blob)
        assertEquals(1, info.version)
        assertEquals(fastKdf, info.kdf)
        assertEquals(blob.size, info.totalBytes)

        val decoded = Backup.decode(blob, "backup-pass-123".toCharArray())
        assertEquals(2, decoded.size)
        assertTrue(decoded.none { it.title == "NoOTP" })
        assertEquals(OtpType.HOTP, decoded[1].otpType)
        assertEquals(12L, decoded[1].totpCounter)
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
}
