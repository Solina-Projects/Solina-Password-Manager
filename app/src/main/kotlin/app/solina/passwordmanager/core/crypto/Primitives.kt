package app.solina.passwordmanager.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** رمزگشایی ناموفق: کلید نادرست یا داده دست‌کاری‌شده. عمداً بین این دو تفاوتی نمی‌گذارد. */
class AuthenticationFailedException(cause: Throwable? = null) :
    GeneralSecurityException("Authentication failed", cause)

/** پارامترهای Argon2id. حافظه به KiB. */
data class KdfParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    init {
        require(parallelism in 1..MAX_PARALLELISM) { "parallelism out of range" }
        require(iterations in 1..MAX_ITERATIONS) { "iterations out of range" }
        require(memoryKiB in (8 * parallelism)..MAX_MEMORY_KIB) { "memory out of range" }
    }

    companion object {
        const val MAX_PARALLELISM = 8
        const val MAX_ITERATIONS = 10
        /** سقف ۲۵۶ MiB؛ جلوگیری از حملهٔ مصرف حافظه از طریق هدر دست‌کاری‌شده. */
        const val MAX_MEMORY_KIB = 256 * 1024

        /** پیش‌فرض پیشنهادی (طبق ARCHITECTURE.md). در M2 با اندازه‌گیری روی دستگاه نهایی می‌شود. */
        val DEFAULT = KdfParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 4)
    }
}

object Argon2id {
    fun derive(
        password: ByteArray,
        salt: ByteArray,
        params: KdfParams,
        outLength: Int = 32,
        secret: ByteArray? = null,
        additional: ByteArray? = null,
    ): ByteArray {
        require(salt.size >= 8) { "salt too short" }
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(params.memoryKiB)
            .withIterations(params.iterations)
            .withParallelism(params.parallelism)
        if (secret != null) builder.withSecret(secret)
        if (additional != null) builder.withAdditional(additional)
        val generator = Argon2BytesGenerator()
        generator.init(builder.build())
        val out = ByteArray(outLength)
        generator.generateBytes(password, out)
        return out
    }
}

/** HKDF-SHA256 طبق RFC 5869. */
object Hkdf {
    private const val HASH_LEN = 32

    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val key = if (salt == null || salt.isEmpty()) ByteArray(HASH_LEN) else salt
        return hmac(key, ikm)
    }

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * HASH_LEN)) { "length out of range" }
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val n = minOf(HASH_LEN, length - pos)
            System.arraycopy(previous, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }

    fun derive(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int = 32): ByteArray =
        expand(extract(salt, ikm), info, length)

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}

/**
 * AES-256-GCM با nonce تصادفی ۹۶ بیتی.
 * قالب خروجی: nonce(12) ‖ ciphertext ‖ tag(16).
 */
object Aead {
    const val KEY_SIZE = 32
    const val NONCE_SIZE = 12
    const val TAG_BITS = 128
    private val random = SecureRandom()

    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
        return sealWithNonce(key, nonce, plaintext, aad)
    }

    /** فقط برای تست با بردار ثابت. در کد اصلی از [seal] استفاده شود (nonce هرگز تکرار نشود). */
    internal fun sealWithNonce(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "key must be 32 bytes" }
        require(nonce.size == NONCE_SIZE) { "nonce must be 12 bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(key.size == KEY_SIZE) { "key must be 32 bytes" }
        if (sealed.size < NONCE_SIZE + TAG_BITS / 8) throw AuthenticationFailedException()
        try {
            val nonce = sealed.copyOfRange(0, NONCE_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad)
            return cipher.doFinal(sealed, NONCE_SIZE, sealed.size - NONCE_SIZE)
        } catch (e: GeneralSecurityException) {
            throw AuthenticationFailedException(e)
        }
    }
}

fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }

/** پاک‌سازی تا حد امکان. JVM تضمین نمی‌کند نسخه‌ای در حافظه نماند؛ این فقط کاهش ریسک است. */
fun ByteArray.wipe() = fill(0)

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "odd hex length" }
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
