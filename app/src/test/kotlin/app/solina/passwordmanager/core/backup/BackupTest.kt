package app.solina.passwordmanager.core.backup

import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private val fast = KdfParams(64, 1, 1)
    private val items = listOf(
        ItemPayload(ItemKind.Login, "GitHub", "ali", "SuperSecret!42", "github.com", "یادداشت", "JBSWY3DPEHPK3PXP", true, createdAt = 5, editedAt = 9, totpDigits = 8, totpPeriod = 60, totpAlgorithm = "SHA256"),
        ItemPayload(ItemKind.Card, "کارت", cardNumber = "6037997712345678", cardCvv = "123"),
    )

    @Test fun roundTrip() {
        val b = Backup.encode(items, "رمز-بکاپ-قوی".toCharArray(), 1L, fast)
        assertEquals(items, Backup.decode(b, "رمز-بکاپ-قوی".toCharArray()))
    }

    @Test fun nothingPlaintextInFile() {
        val b = Backup.encode(items, "pw-pw-pw-pw-1".toCharArray(), 1L, fast)
        val s = String(b, Charsets.ISO_8859_1)
        assertFalse(s.contains("GitHub")); assertFalse(s.contains("SuperSecret")); assertFalse(s.contains("6037997712345678"))
    }

    @Test fun wrongPasswordFails() {
        val b = Backup.encode(items, "right-password".toCharArray(), 1L, fast)
        assertThrows(AuthenticationFailedException::class.java) { Backup.decode(b, "wrong-password".toCharArray()) }
    }

    @Test fun tamperingFails() {
        val b = Backup.encode(items, "right-password".toCharArray(), 1L, fast)
        val body = b.copyOf(); body[body.size - 3] = (body[body.size - 3].toInt() xor 1).toByte()
        assertThrows(AuthenticationFailedException::class.java) { Backup.decode(body, "right-password".toCharArray()) }
        val hdr = b.copyOf(); hdr[8] = (hdr[8].toInt() xor 1).toByte()      // پارامتر KDF در هدر (AAD)
        assertThrows(Exception::class.java) { Backup.decode(hdr, "right-password".toCharArray()) }
        val salt = b.copyOf(); salt[20] = (salt[20].toInt() xor 1).toByte()
        assertThrows(AuthenticationFailedException::class.java) { Backup.decode(salt, "right-password".toCharArray()) }
    }

    @Test fun rejectsForeignAndHostileFiles() {
        assertThrows(BackupFormatException::class.java) { Backup.decode("hello world, not a backup at all.......".toByteArray(), "x".toCharArray()) }
        val b = Backup.encode(items, "right-password".toCharArray(), 1L, fast)
        val huge = b.copyOf(); huge[5] = 0x7f   // حافظهٔ KDF نامعتبر و بسیار بزرگ → قبل از Argon2 رد می‌شود
        assertThrows(BackupFormatException::class.java) { Backup.decode(huge, "right-password".toCharArray()) }
    }

    @Test fun twoBackupsDiffer() {
        val a = Backup.encode(items, "pw-pw-pw-pw-1".toCharArray(), 1L, fast)
        val b = Backup.encode(items, "pw-pw-pw-pw-1".toCharArray(), 1L, fast)
        assertFalse(a.contentEquals(b))
    }
}
