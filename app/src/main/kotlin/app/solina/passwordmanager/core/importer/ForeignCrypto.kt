package app.solina.passwordmanager.core.importer

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** رمز اشتباه یا فایل دست‌کاری‌شده (برچسب احراز GCM/MAC نخواند). */
class WrongImportPasswordException : Exception()

/** ابزارهای رمزگشایی بکاپ برنامه‌های دیگر. فقط برای خواندن؛ چیزی با این قالب‌ها نوشته نمی‌شود. */
internal object ForeignCrypto {
    private const val MAX_PBKDF2_ITERATIONS = 1_000_000
    private const val MAX_SCRYPT_MEMORY_BYTES = 64L * 1024 * 1024
    private const val MAX_ARGON2_MEMORY_MIB = 64
    fun hex(s: String): ByteArray {
        require(s.length % 2 == 0)
        return ByteArray(s.length / 2) { ((Character.digit(s[2 * it], 16) shl 4) or Character.digit(s[2 * it + 1], 16)).toByte() }
    }

    fun b64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s.trim())

    fun scrypt(password: CharArray, salt: ByteArray, n: Int, r: Int, p: Int): ByteArray {
        if (n < 2 || n and (n - 1) != 0 || r !in 1..16 || p !in 1..4 ||
            128L * n * r * p > MAX_SCRYPT_MEMORY_BYTES || salt.size !in 8..64
        ) throw ImportException("پارامترهای scrypt فایل از محدودهٔ امن این دستگاه خارج است.")
        val pw = String(password).toByteArray(Charsets.UTF_8)
        return try { SCrypt.generate(pw, salt, n, r, p, 32) } finally { pw.fill(0) }
    }

    fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int = 32): ByteArray {
        if (iterations !in 1..MAX_PBKDF2_ITERATIONS || salt.size !in 1..1024 || keyBytes !in 16..64) {
            throw ImportException("پارامترهای PBKDF2 فایل از محدودهٔ امن این دستگاه خارج است.")
        }
        val spec = PBEKeySpec(password, salt, iterations, keyBytes * 8)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    /** AES-256-GCM؛ ورودی = متن رمز + برچسب (۱۶ بایت) پشت‌سرهم. */
    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ctAndTag: ByteArray): ByteArray = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.doFinal(ctAndTag)
    } catch (e: GeneralSecurityException) {
        throw WrongImportPasswordException()
    }

    private fun hmacSha256(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        for (p in parts) m.update(p)
        return m.doFinal()
    }

    /** HKDF-Expand (RFC 5869) با SHA-256 برای یک بلوک ۳۲ بایتی. */
    fun hkdfExpand32(prk: ByteArray, info: String): ByteArray = hmacSha256(prk, info.toByteArray(Charsets.UTF_8), byteArrayOf(1))

    /** Bitwarden EncString نوع ۲: "2.iv|ct|mac" ؛ AES-256-CBC + HMAC-SHA256 روی iv||ct. */
    fun bitwardenDecrypt(encString: String, encKey: ByteArray, macKey: ByteArray): ByteArray {
        if (!encString.startsWith("2.")) throw ImportException("نوع رمزنگاری Bitwarden پشتیبانی نمی‌شود.")
        val parts = encString.substring(2).split("|")
        if (parts.size != 3) throw ImportException("ساختار فایل Bitwarden خراب است.")
        val iv = try { b64(parts[0]) } catch (e: IllegalArgumentException) { throw ImportException("ساختار فایل Bitwarden خراب است.") }
        val ct = try { b64(parts[1]) } catch (e: IllegalArgumentException) { throw ImportException("ساختار فایل Bitwarden خراب است.") }
        val mac = try { b64(parts[2]) } catch (e: IllegalArgumentException) { throw ImportException("ساختار فایل Bitwarden خراب است.") }
        if (!MessageDigest.isEqual(hmacSha256(macKey, iv, ct), mac)) throw WrongImportPasswordException()
        return try {
            val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
            c.doFinal(ct)
        } catch (e: GeneralSecurityException) {
            throw WrongImportPasswordException()
        }
    }

    /** Argon2id مطابق Bitwarden: نمک = SHA-256 رشتهٔ نمک، حافظه به MiB. */
    fun bitwardenArgon2(password: CharArray, salt: String, iterations: Int, memoryMiB: Int, parallelism: Int): ByteArray {
        if (iterations !in 1..4 || memoryMiB !in 1..MAX_ARGON2_MEMORY_MIB ||
            parallelism !in 1..4 || memoryMiB * 1024 < 8 * parallelism || salt.length !in 1..320
        ) throw ImportException("پارامترهای Argon2id فایل Bitwarden از محدودهٔ امن این دستگاه خارج است.")
        val pw = String(password).toByteArray(Charsets.UTF_8)
        try {
            val saltHash = MessageDigest.getInstance("SHA-256").digest(salt.toByteArray(Charsets.UTF_8))
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations).withMemoryAsKB(memoryMiB * 1024).withParallelism(parallelism)
                .withSalt(saltHash).build()
            val out = ByteArray(32)
            Argon2BytesGenerator().apply { init(params) }.generateBytes(pw, out)
            return out
        } finally { pw.fill(0) }
    }
}
