package app.solina.passwordmanager.core.backup

import app.solina.passwordmanager.core.crypto.randomBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupKeyTest {
    @Test fun derivedPasswordIsDeterministicAndKeyBound() {
        val key = ByteArray(32) { it.toByte() }
        val a = AutoBackupKey.backupPasswordChars(key)
        val b = AutoBackupKey.backupPasswordChars(key)
        assertEquals(String(a), String(b))
        assertEquals(43, a.size)
        assertTrue(String(a).all { it.isLetterOrDigit() || it == '-' || it == '_' })
        val other = AutoBackupKey.backupPasswordChars(ByteArray(32) { (it + 1).toByte() })
        assertFalse(String(a) == String(other))
    }

    @Test fun derivedPasswordOpensItsOwnSnapshot() {
        val key = randomBytes(32)
        val pw = AutoBackupKey.backupPasswordChars(key)
        val items = listOf(
            app.solina.passwordmanager.core.storage.ItemPayload(title = "GitHub", username = "u", password = "p"),
        )
        val bytes = Backup.encode(items, pw, 1_700_000_000_000L)
        val decoded = Backup.decode(bytes, AutoBackupKey.backupPasswordChars(key))
        assertEquals(listOf("GitHub"), decoded.map { it.title })
        // رمز نادرست باز نمی‌کند.
        try {
            Backup.decode(bytes, "wrong-password".toCharArray())
            throw AssertionError("wrong password must not decode an auto snapshot")
        } catch (e: app.solina.passwordmanager.core.crypto.AuthenticationFailedException) {
            // مورد انتظار
        }
    }
}
