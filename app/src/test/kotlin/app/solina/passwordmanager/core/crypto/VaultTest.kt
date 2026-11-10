package app.solina.passwordmanager.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultTest {
    /** پارامتر سبک فقط برای سرعت تست. مقدار واقعی KdfParams.DEFAULT است. */
    private val fast = KdfParams(memoryKiB = 64, iterations = 1, parallelism = 1)
    private val pw = "رمز-اصلی-تست-۱۲۳".toCharArray()

    @Test
    fun createThenUnlockWithPassword() {
        val created = VaultCrypto.create(pw, fast)
        val vk = VaultCrypto.unlockWithPassword(created.header, pw)
        assertArrayEquals(created.vaultKey, vk)
        assertEquals(32, vk.size)
    }

    @Test
    fun wrongPasswordFails() {
        val created = VaultCrypto.create(pw, fast)
        assertThrows(AuthenticationFailedException::class.java) {
            VaultCrypto.unlockWithPassword(created.header, "wrong".toCharArray())
        }
    }

    @Test
    fun recoveryPhraseUnlocksAndIsCaseAndSpaceInsensitive() {
        val created = VaultCrypto.create(pw, fast)
        assertEquals(24, created.recoveryWords.size)
        val phrase = created.recoveryWords.joinToString(" ")
        assertArrayEquals(created.vaultKey, VaultCrypto.unlockWithRecovery(created.header, phrase))
        assertArrayEquals(created.vaultKey, VaultCrypto.unlockWithRecovery(created.header, "  " + phrase.uppercase().replace(" ", "  ")))
    }

    @Test
    fun wrongButValidRecoveryPhraseFails() {
        val a = VaultCrypto.create(pw, fast)
        val b = VaultCrypto.create(pw, fast)
        assertThrows(AuthenticationFailedException::class.java) {
            VaultCrypto.unlockWithRecovery(a.header, b.recoveryWords.joinToString(" "))
        }
    }

    @Test
    fun twoVaultsNeverShareKeysOrSalts() {
        val a = VaultCrypto.create(pw, fast)
        val b = VaultCrypto.create(pw, fast)
        assertFalse(a.vaultKey.contentEquals(b.vaultKey))
        assertFalse(a.header.salt.contentEquals(b.header.salt))
        assertTrue(a.recoveryWords != b.recoveryWords)
    }

    @Test
    fun changePasswordKeepsVaultKeyAndRecovery() {
        val created = VaultCrypto.create(pw, fast)
        val item = VaultCrypto.sealItem(created.vaultKey, "id-1", 1, 1, "data".toByteArray())

        val newPw = "new-password".toCharArray()
        val newHeader = VaultCrypto.changePassword(created.header, created.vaultKey, newPw)

        assertThrows(AuthenticationFailedException::class.java) { VaultCrypto.unlockWithPassword(newHeader, pw) }
        val vk = VaultCrypto.unlockWithPassword(newHeader, newPw)
        assertArrayEquals(created.vaultKey, vk)
        // داده‌ها دست‌نخورده و با همان کلید قابل خواندن‌اند (بدون رمزگذاری مجدد)
        assertArrayEquals("data".toByteArray(), VaultCrypto.openItem(vk, "id-1", 1, 1, item))
        // عبارت بازیابی همچنان کار می‌کند
        assertArrayEquals(created.vaultKey, VaultCrypto.unlockWithRecovery(newHeader, created.recoveryWords.joinToString(" ")))
        assertFalse(newHeader.salt.contentEquals(created.header.salt))
    }

    @Test
    fun headerEncodeDecodeRoundTrip() {
        val created = VaultCrypto.create(pw, fast)
        val bytes = created.header.encode()
        assertEquals(153, bytes.size)
        val decoded = VaultHeader.decode(bytes)
        assertArrayEquals(created.vaultKey, VaultCrypto.unlockWithPassword(decoded, pw))
        assertEquals(fast, decoded.kdf)
        assertArrayEquals(bytes, decoded.encode())
    }

    @Test
    fun corruptHeadersAreRejected() {
        val bytes = VaultCrypto.create(pw, fast).header.encode()
        assertThrows(CorruptVaultException::class.java) { VaultHeader.decode(bytes.copyOf(bytes.size - 1)) }
        assertThrows(CorruptVaultException::class.java) { VaultHeader.decode(bytes + byteArrayOf(0)) }
        assertThrows(CorruptVaultException::class.java) { VaultHeader.decode(ByteArray(0)) }
        assertThrows(CorruptVaultException::class.java) { VaultHeader.decode(bytes.copyOf().also { it[0] = 'X'.code.toByte() }) }
        assertThrows(CorruptVaultException::class.java) { VaultHeader.decode(bytes.copyOf().also { it[4] = 9 }) }
    }

    @Test
    fun headerWithHugeMemoryIsRejectedBeforeAnyKdfWork() {
        val bytes = VaultCrypto.create(pw, fast).header.encode()
        // حافظهٔ Int.MAX_VALUE (بایت‌های ۵ تا ۸)
        val evil = bytes.copyOf()
        evil[5] = 0x7f; evil[6] = -1; evil[7] = -1; evil[8] = -1
        assertThrows(CorruptVaultException::class.java) { VaultHeader.decode(evil) }
    }

    @Test
    fun tamperedKdfParamsInHeaderBreakUnlock() {
        val created = VaultCrypto.create(pw, fast)
        val bytes = created.header.encode()
        bytes[12] = 2 // تعداد تکرار ۱ ← ۲ (بایت آخر iterations)
        val tampered = VaultHeader.decode(bytes)
        assertThrows(AuthenticationFailedException::class.java) { VaultCrypto.unlockWithPassword(tampered, pw) }
    }

    @Test
    fun passwordNormalizationMakesEquivalentFormsMatch() {
        // «é» یکپارچه در برابر e + اکسان ترکیبی
        val composed = "café-pass".toCharArray()
        val decomposed = "café-pass".toCharArray()
        val created = VaultCrypto.create(composed, fast)
        assertArrayEquals(created.vaultKey, VaultCrypto.unlockWithPassword(created.header, decomposed))
    }

    // ───── ورودی‌ها ─────

    @Test
    fun itemRoundTripAndBinding() {
        val vk = randomBytes(32)
        val data = "{\"user\":\"nasser\",\"pass\":\"x\"}".toByteArray()
        val sealed = VaultCrypto.sealItem(vk, "item-A", 7, 1, data)
        assertArrayEquals(data, VaultCrypto.openItem(vk, "item-A", 7, 1, sealed))

        // انتقال بستهٔ رمزشده به شناسه، نسخه یا اسکیمای دیگر باید شکست بخورد
        assertThrows(AuthenticationFailedException::class.java) { VaultCrypto.openItem(vk, "item-B", 7, 1, sealed) }
        assertThrows(AuthenticationFailedException::class.java) { VaultCrypto.openItem(vk, "item-A", 6, 1, sealed) }
        assertThrows(AuthenticationFailedException::class.java) { VaultCrypto.openItem(vk, "item-A", 7, 2, sealed) }
        assertThrows(AuthenticationFailedException::class.java) { VaultCrypto.openItem(randomBytes(32), "item-A", 7, 1, sealed) }
    }

    @Test
    fun eachItemGetsItsOwnKey() {
        val vk = randomBytes(32)
        val a = VaultCrypto.sealItem(vk, "x", 1, 1, "same".toByteArray())
        val b = VaultCrypto.sealItem(vk, "x", 1, 1, "same".toByteArray())
        assertFalse(a.wrappedItemKey.contentEquals(b.wrappedItemKey))
        assertFalse(a.blob.contentEquals(b.blob))
    }

    @Test
    fun ciphertextDoesNotContainPlaintext() {
        val vk = randomBytes(32)
        val marker = "SUPER-SECRET-MARKER-12345"
        val sealed = VaultCrypto.sealItem(vk, "x", 1, 1, marker.toByteArray())
        assertFalse(String(sealed.blob, Charsets.ISO_8859_1).contains(marker))
    }
}
