package app.solina.passwordmanager.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PrimitivesTest {

    /** RFC 9106 §5.3: Argon2id با secret و associated data. */
    @Test
    fun argon2idRfc9106Vector() {
        val tag = Argon2id.derive(
            password = ByteArray(32) { 0x01 },
            salt = ByteArray(16) { 0x02 },
            params = KdfParams(memoryKiB = 32, iterations = 3, parallelism = 4),
            outLength = 32,
            secret = ByteArray(8) { 0x03 },
            additional = ByteArray(12) { 0x04 },
        )
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", tag.toHex())
    }

    /** RFC 5869 پیوست A، موارد ۱ و ۳. */
    @Test
    fun hkdfRfc5869Vectors() {
        val okm1 = Hkdf.derive(
            ikm = ByteArray(22) { 0x0b },
            salt = "000102030405060708090a0b0c".hexToBytes(),
            info = "f0f1f2f3f4f5f6f7f8f9".hexToBytes(),
            length = 42,
        )
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm1.toHex())

        val prk3 = Hkdf.extract(null, ByteArray(22) { 0x0b })
        assertEquals("19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04", prk3.toHex())
        val okm3 = Hkdf.expand(prk3, ByteArray(0), 42)
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8", okm3.toHex())
    }

    /** بردار رسمی GCM (McGrew-Viega، مورد ۱۵: کلید ۲۵۶ بیتی، بدون AAD). */
    @Test
    fun aesGcmKnownAnswer() {
        val key = "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308".hexToBytes()
        val nonce = "cafebabefacedbaddecaf888".hexToBytes()
        val plain = (
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
                "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255"
            ).hexToBytes()
        val expected = nonce.toHex() +
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
            "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015ad" +
            "b094dac5d93471bdec1a502270e3cc6c"
        val sealed = Aead.sealWithNonce(key, nonce, plain, ByteArray(0))
        assertEquals(expected, sealed.toHex())
        assertArrayEquals(plain, Aead.open(key, sealed))
    }

    @Test
    fun aeadRoundTripAndTamperDetection() {
        val key = randomBytes(32)
        val aad = "ctx".toByteArray()
        val sealed = Aead.seal(key, "secret".toByteArray(), aad)
        assertArrayEquals("secret".toByteArray(), Aead.open(key, sealed, aad))

        // تغییر یک بیت در هر بخش (nonce، متن رمزشده، tag) باید شناسایی شود
        for (pos in listOf(0, 13, sealed.size - 1)) {
            val bad = sealed.copyOf().also { it[pos] = (it[pos].toInt() xor 1).toByte() }
            assertThrows(AuthenticationFailedException::class.java) { Aead.open(key, bad, aad) }
        }
        assertThrows(AuthenticationFailedException::class.java) { Aead.open(key, sealed, "other".toByteArray()) }
        assertThrows(AuthenticationFailedException::class.java) { Aead.open(randomBytes(32), sealed, aad) }
        assertThrows(AuthenticationFailedException::class.java) { Aead.open(key, ByteArray(5), aad) }
    }

    @Test
    fun nonceIsFreshEveryTime() {
        val key = randomBytes(32)
        val nonces = (1..200).map { Aead.seal(key, ByteArray(1)).copyOfRange(0, 12).toHex() }.toSet()
        assertEquals(200, nonces.size)
    }

    @Test
    fun kdfParamsRejectDangerousValues() {
        assertThrows(IllegalArgumentException::class.java) { KdfParams(Int.MAX_VALUE, 3, 4) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(64 * 1024, 0, 4) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(64 * 1024, 3, 0) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(16, 3, 4) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(65 * 1024, 3, 4) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(64 * 1024, 5, 4) }
        assertThrows(IllegalArgumentException::class.java) { KdfParams(64 * 1024, 3, 5) }
        assertEquals(KdfParams(64 * 1024, 4, 4), KdfParams(64 * 1024, 4, 4))
    }

    // ───── BIP-39 ─────

    @Test
    fun bip39WordlistIntegrity() {
        assertEquals(2048, Bip39Words.ENGLISH.size)
        assertEquals(2048, Bip39Words.ENGLISH.toSet().size)
        val text = Bip39Words.ENGLISH.joinToString("\n") + "\n"
        val sha = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.US_ASCII)).toHex()
        assertEquals("2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda", sha)
    }

    @Test
    fun bip39OfficialVectors256Bit() {
        val vectors = mapOf(
            0x00 to ("abandon ".repeat(23) + "art"),
            0x7f to "legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth title",
            0x80 to "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic bless",
            0xff to ("zoo ".repeat(23) + "vote"),
        )
        for ((byte, phrase) in vectors) {
            val entropy = ByteArray(32) { byte.toByte() }
            assertEquals(phrase, Bip39.entropyToWords(entropy).joinToString(" "))
            assertArrayEquals(entropy, Bip39.wordsToEntropy(phrase))
        }
    }

    @Test
    fun bip39RejectsBadInput() {
        val good = "abandon ".repeat(23) + "art"
        assertArrayEquals(ByteArray(32), Bip39.wordsToEntropy("  " + good.uppercase().replace(" ", "   ") + " "))
        assertThrows(InvalidRecoveryPhraseException::class.java) { Bip39.wordsToEntropy("abandon ".repeat(23) + "zoo") } // checksum
        assertThrows(InvalidRecoveryPhraseException::class.java) { Bip39.wordsToEntropy("abandon ".repeat(23) + "notaword") }
        assertThrows(InvalidRecoveryPhraseException::class.java) { Bip39.wordsToEntropy("abandon ".repeat(11) + "about") }
        assertThrows(InvalidRecoveryPhraseException::class.java) { Bip39.wordsToEntropy("") }
    }

    @Test
    fun bip39RandomRoundTrip() {
        repeat(200) {
            val e = randomBytes(32)
            val words = Bip39.entropyToWords(e)
            assertEquals(24, words.size)
            assertArrayEquals(e, Bip39.wordsToEntropy(words.joinToString(" ")))
        }
    }

    @Test
    fun hexHelpers() {
        assertEquals("00ff10", byteArrayOf(0, -1, 16).toHex())
        assertTrue("00ff10".hexToBytes().contentEquals(byteArrayOf(0, -1, 16)))
    }
}
