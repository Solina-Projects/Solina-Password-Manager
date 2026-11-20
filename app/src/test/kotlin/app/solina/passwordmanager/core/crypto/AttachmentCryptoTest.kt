package app.solina.passwordmanager.core.crypto

import app.solina.passwordmanager.core.OtpType
import app.solina.passwordmanager.core.backup.Backup
import app.solina.passwordmanager.core.storage.CustomField
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** پیوست رمزگذاری‌شده (فاز ۴): رفت‌وبرگشت، شناسایی دست‌کاری، و محدودیت اندازه. */
class AttachmentCryptoTest {
    private val vaultKey = ByteArray(32) { (it * 7 + 3).toByte() }
    private val otherKey = ByteArray(32) { (it * 11 + 5).toByte() }
    private val fileId = "550e8400-e29b-41d4-a716-446655440000"

    @Test fun roundTrip() {
        val data = "متن پیوست آزمایشی — قرارداد بیمه".toByteArray(Charsets.UTF_8)
        val sealed = AttachmentCrypto.seal(vaultKey, fileId, data)
        assertArrayEquals(data, AttachmentCrypto.open(vaultKey, fileId, sealed))
    }

    @Test fun emptyFileRoundTrips() {
        val sealed = AttachmentCrypto.seal(vaultKey, fileId, ByteArray(0))
        assertArrayEquals(ByteArray(0), AttachmentCrypto.open(vaultKey, fileId, sealed))
    }

    @Test fun eachFileGetsItsOwnDek() {
        val data = "same content".toByteArray(Charsets.UTF_8)
        val a = AttachmentCrypto.seal(vaultKey, fileId, data)
        val b = AttachmentCrypto.seal(vaultKey, fileId, data)
        // DEK تصادفیِ هر فایل فرق می‌کند، پس خروجی بسته‌شده هم فرق می‌کند.
        assertFalse(a == b)
        assertArrayEquals(data, AttachmentCrypto.open(vaultKey, fileId, a))
        assertArrayEquals(data, AttachmentCrypto.open(vaultKey, fileId, b))
    }

    @Test fun tamperedDataIsDetected() {
        val sealed = AttachmentCrypto.seal(vaultKey, fileId, "secret".toByteArray(Charsets.UTF_8))
        val tampered = sealed.copy(sealedData = sealed.sealedData.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() })
        assertThrows(AuthenticationFailedException::class.java) {
            AttachmentCrypto.open(vaultKey, fileId, tampered)
        }
    }

    @Test fun tamperedWrappedDekIsDetected() {
        val sealed = AttachmentCrypto.seal(vaultKey, fileId, "secret".toByteArray(Charsets.UTF_8))
        val tampered = sealed.copy(wrappedDek = sealed.wrappedDek.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() })
        assertThrows(AuthenticationFailedException::class.java) {
            AttachmentCrypto.open(vaultKey, fileId, tampered)
        }
    }

    @Test fun wrongVaultKeyFails() {
        val sealed = AttachmentCrypto.seal(vaultKey, fileId, "secret".toByteArray(Charsets.UTF_8))
        assertThrows(AuthenticationFailedException::class.java) {
            AttachmentCrypto.open(otherKey, fileId, sealed)
        }
    }

    @Test fun swappedFileIdFails() {
        val sealed = AttachmentCrypto.seal(vaultKey, fileId, "secret".toByteArray(Charsets.UTF_8))
        assertThrows(AuthenticationFailedException::class.java) {
            AttachmentCrypto.open(vaultKey, "550e8400-e29b-41d4-a716-446655440001", sealed)
        }
        // پیوست فایل A با شناسهٔ فایل B باز نمی‌شود و برعکس.
        val sealedB = AttachmentCrypto.seal(vaultKey, "other-id", "other".toByteArray(Charsets.UTF_8))
        assertThrows(AuthenticationFailedException::class.java) {
            AttachmentCrypto.open(vaultKey, fileId, sealedB)
        }
    }

    @Test fun oversizedAttachmentRejected() {
        val big = ByteArray(AttachmentCrypto.MAX_ATTACHMENT_BYTES + 1)
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentCrypto.seal(vaultKey, fileId, big)
        }
        // دقیقاً در سقف، قبول است.
        val atLimit = AttachmentCrypto.seal(vaultKey, fileId, ByteArray(AttachmentCrypto.MAX_ATTACHMENT_BYTES))
        assertArrayEquals(ByteArray(AttachmentCrypto.MAX_ATTACHMENT_BYTES), AttachmentCrypto.open(vaultKey, fileId, atLimit))
    }

    @Test fun invalidInputsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentCrypto.seal(ByteArray(16), fileId, "x".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentCrypto.seal(vaultKey, "   ", "x".toByteArray())
        }
    }

    @Test fun v1BackupStillReadsPhase4Payloads() {
        // پیوست‌ها وارد بکاپ نمی‌شوند و Backup.encode دست نخورده است؛ پس بکاپ v1 باید
        // ورودی‌های فاز ۴ (فیلد سفارشی + Steam) را دقیقاً مثل قبل برگرداند.
        val items = listOf(
            ItemPayload(
                ItemKind.Login, "Steam",
                totpSecret = "GEZDGNBVGY3TQOJQ", totpDigits = 5, otpType = OtpType.STEAM,
                customFields = listOf(CustomField("پین بازیابی", "1234", hidden = true)),
            ),
        )
        val fast = KdfParams(64, 1, 1)
        val bytes = Backup.encode(items, "رمز-بکاپ-قوی".toCharArray(), 1L, fast)
        assertEquals(1, Backup.inspect(bytes).version)
        assertEquals(items, Backup.decode(bytes, "رمز-بکاپ-قوی".toCharArray()))
    }

    @Test fun dekIsNeverStoredInPlaintext() {
        // خروجی بسته‌شده نباید DEK یا متن اصلی را به‌صورت آشکار داشته باشد.
        val data = "TOPSECRETPLAINTEXT".toByteArray(Charsets.UTF_8)
        val sealed = AttachmentCrypto.seal(vaultKey, fileId, data)
        val combined = sealed.wrappedDek + sealed.sealedData
        assertFalse(containsSubarray(combined, data))
    }

    private fun containsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
