package app.solina.refuge.core.importer

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
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
    fun hex(s: String): ByteArray {
        val clean = s.trim()
        require(clean.length % 2 == 0) { "odd hex length" }
        return ByteArray(clean.length / 2) { i ->
            val hi = Character.digit(clean[2 * i], 16)
            val lo = Character.digit(clean[2 * i + 1], 16)
            require(hi >= 0 && lo >= 0) { "invalid hex character" }
            ((hi shl 4) or lo).toByte()
        }
    }

    /**
     * رمزگشایی Base64 مقاوم در برابر شکست خط (\n و \r که بعضی نسخه‌های Aegis و ابزارها می‌گذارند)،
     * فاصله‌های اضافی، الفبای URL-safe (-/_) و نبود padding (=).
     */
    fun b64(s: String): ByteArray {
        val compact = buildString(s.length) {
            for (ch in s) {
                if (!ch.isWhitespace()) {
                    append(when (ch) {
                        '-' -> '+'
                        '_' -> '/'
                        else -> ch
                    })
                }
            }
        }
        val rem = compact.length % 4
        if (rem == 1) throw IllegalArgumentException("invalid base64 length")
        val padded = if (rem == 0) compact else compact + "=".repeat(4 - rem)
        return java.util.Base64.getDecoder().decode(padded)
    }

    fun scrypt(password: CharArray, salt: ByteArray, n: Int, r: Int, p: Int): ByteArray {
        val pw = toUtf8Bytes(password)
        return try { scryptBytes(pw, salt, n, r, p) } finally { pw.fill(0) }
    }

    fun scryptBytes(passwordBytes: ByteArray, salt: ByteArray, n: Int, r: Int, p: Int): ByteArray = try {
        SCrypt.generate(passwordBytes, salt, n, r, p, 32)
    } catch (e: OutOfMemoryError) {
        throw ImportException("حافظهٔ دستگاه برای بازگشایی این فایل کافی نیست.")
    }

    fun toUtf8Bytes(chars: CharArray): ByteArray {
        val bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(bb.limit())
        bb.get(out)
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }

    /**
     * سازگاری با باگ قدیمی Aegis (issue #95) در اسلات‌های ترمیم‌نشده (repaired=false):
     * نسخهٔ قدیمی Aegis از ByteBuffer.array() به‌جای limit() استفاده می‌کرد که بایت‌های صفرِ انتهایی
     * تا ظرفیت بافر (۳× یا ۴× طول رشته بسته به JVM/Android) اضافه می‌کرد. برای کلیدهای بلندتر از ۶۴ بایت،
     * HMAC-SHA256 داخل scrypt خروجی متفاوتی می‌دهد.
     */
    fun aegisLegacyPasswordBytes(password: CharArray): List<ByteArray> {
        val exact = toUtf8Bytes(password)
        val out = ArrayList<ByteArray>(3)
        fun addIfNew(buf: ByteArray) {
            if (buf.size > 64 && !buf.contentEquals(exact) && out.none { it.contentEquals(buf) }) {
                out += buf
            } else {
                buf.fill(0)
            }
        }
        // ۱) دقیقاً معادل ByteBuffer.array() روی همین دستگاه/JVM
        val runtimeBuf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password)).array()
        addIfNew(runtimeBuf)
        // ۲) ظرفیت بافر Android ICU (4 * len) و OpenJDK (3 * len) برای پشتیبانی میان‌پلتفرمی
        for (mult in intArrayOf(4, 3)) {
            val cap = password.size * mult
            if (cap > 64 && cap >= exact.size) {
                addIfNew(exact.copyOf(cap))
            }
        }
        exact.fill(0)
        return out
    }

    /**
     * فهرست نامزدهای رمز برای فایل‌های خارجیِ دارای احراز اصالت (GCM/HMAC):
     * اولویت اول همیشه خود رمزِ دقیق واردشده است. اگر ناموفق بود، خطاهای رایج صفحه‌کلید فارسی
     * (ارقام فارسی/عربی ۰-۹)، نویسه‌های پنهان کپی‌پیست (LRM/RLM/BOM/خط جدید) و نرمال‌سازی NFC امتحان می‌شوند.
     * از آنجا که رمزگشایی با برچسب ۱۲۸ یا ۲۵۶ بیتی احراز می‌شود، هیچ مثبت کاذبی رخ نمی‌دهد.
     */
    fun passwordCandidates(password: CharArray): List<CharArray> {
        val list = ArrayList<CharArray>(5)
        fun addUnique(c: CharArray) {
            if (c.isNotEmpty() && list.none { it.contentEquals(c) }) list += c else c.fill('\u0000')
        }
        addUnique(password.copyOf())

        val asciiDigits = CharArray(password.size) { ch -> toAsciiDigit(password[ch]) }
        addUnique(asciiDigits)

        val cleaned = stripInvisibleControls(password)
        addUnique(cleaned)

        val cleanedAscii = CharArray(cleaned.size) { ch -> toAsciiDigit(cleaned[ch]) }
        addUnique(cleanedAscii)

        val s = String(password)
        val nfc = Normalizer.normalize(s, Normalizer.Form.NFC)
        if (nfc != s) addUnique(nfc.toCharArray())

        return list
    }

    fun containsNonAsciiDigits(s: CharSequence): Boolean =
        s.any { it in '\u06F0'..'\u06F9' || it in '\u0660'..'\u0669' }

    private fun toAsciiDigit(ch: Char): Char = when (ch) {
        in '\u06F0'..'\u06F9' -> ('0'.code + (ch.code - 0x06F0)).toChar()
        in '\u0660'..'\u0669' -> ('0'.code + (ch.code - 0x0660)).toChar()
        else -> ch
    }

    private fun stripInvisibleControls(chars: CharArray): CharArray {
        val sb = StringBuilder(chars.size)
        for (ch in chars) {
            if (ch != '\u200E' && ch != '\u200F' && ch != '\uFEFF' && ch != '\r' && ch != '\n') {
                sb.append(ch)
            }
        }
        return sb.toString().toCharArray()
    }

    fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int = 32): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyBytes * 8)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    fun pbkdf2Sha1(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int = 32): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyBytes * 8)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun argon2id(password: CharArray, salt: ByteArray, iterations: Int, memoryKiB: Int, parallelism: Int, keyBytes: Int = 32): ByteArray {
        val pw = toUtf8Bytes(password)
        try {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKiB)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build()
            val out = ByteArray(keyBytes)
            Argon2BytesGenerator().apply { init(params) }.generateBytes(pw, out)
            return out
        } catch (e: OutOfMemoryError) {
            throw ImportException("حافظهٔ دستگاه برای بازگشایی این فایل کافی نیست.")
        } finally {
            pw.fill(0)
        }
    }

    /** AES-256-GCM؛ ورودی = متن رمز + برچسب (۱۶ بایت) پشت‌سرهم. */
    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ctAndTag: ByteArray, aad: ByteArray? = null): ByteArray {
        if (ctAndTag.size < 16 || nonce.isEmpty()) throw ImportException("ساختار رمزنگاری فایل نامعتبر یا خراب است.")
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            if (aad != null) c.updateAAD(aad)
            c.doFinal(ctAndTag)
        } catch (e: AEADBadTagException) {
            throw WrongImportPasswordException()
        } catch (e: BadPaddingException) {
            throw WrongImportPasswordException()
        } catch (e: GeneralSecurityException) {
            throw ImportException("ساختار رمزنگاری فایل نامعتبر یا خراب است.")
        }
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
        } catch (e: BadPaddingException) {
            throw WrongImportPasswordException()
        } catch (e: GeneralSecurityException) {
            throw ImportException("ساختار رمزنگاری فایل Bitwarden نامعتبر است.")
        }
    }

    /** Argon2id مطابق Bitwarden: نمک = SHA-256 رشتهٔ نمک، حافظه به MiB. */
    fun bitwardenArgon2(password: CharArray, salt: String, iterations: Int, memoryMiB: Int, parallelism: Int): ByteArray {
        val saltHash = sha256(salt.toByteArray(Charsets.UTF_8))
        return argon2id(password, saltHash, iterations, memoryMiB * 1024, parallelism, 32)
    }
}
