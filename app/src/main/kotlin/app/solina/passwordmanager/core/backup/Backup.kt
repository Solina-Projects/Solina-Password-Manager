package app.solina.passwordmanager.core.backup

import app.solina.passwordmanager.core.crypto.Aead
import app.solina.passwordmanager.core.crypto.Argon2
import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.crypto.VaultCrypto
import app.solina.passwordmanager.core.crypto.randomBytes
import app.solina.passwordmanager.core.crypto.wipe
import app.solina.passwordmanager.core.storage.ItemPayload
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer

class BackupFormatException(message: String) : Exception(message)

/** اطلاعات تاییدشدهٔ هدر فایل بکاپ پیش از رمزگشایی (بدون نیاز به رمز). */
data class BackupHeaderInfo(
    val version: Int,
    val kdf: KdfParams,
    val totalBytes: Int,
)

/**
 * قالب بکاپ رمزشدهٔ Solina (نسخهٔ ۱):
 *   "SLNB" (4) | version=1 (1) | kdf (12) | salt (16) | AES-256-GCM(key, json, aad=هدر)
 */
object Backup {
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'L'.code.toByte(), 'N'.code.toByte(), 'B'.code.toByte())
    private const val VERSION: Byte = 1
    private const val SALT_SIZE = 16
    private const val HEADER_SIZE = 4 + 1 + 12 + SALT_SIZE
    private const val MIN_BODY_SIZE = Aead.NONCE_SIZE + Aead.TAG_BITS / 8

    private fun key(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val bytes = VaultCrypto.passwordBytes(password)
        try {
            return Argon2.derive(bytes, salt, params)
        } finally {
            bytes.wipe()
        }
    }

    /**
     * اعتبارسنجی ساختار و هدر فایل بکاپ پیش از دریافت رمز یا بازیابی.
     */
    fun inspect(bytes: ByteArray): BackupHeaderInfo {
        if (bytes.size < HEADER_SIZE + MIN_BODY_SIZE) {
            throw BackupFormatException("فایل بکاپ Solina نیست یا ناقص است.")
        }
        val b = ByteBuffer.wrap(bytes)
        val magic = ByteArray(4).also { b.get(it) }
        if (!magic.contentEquals(MAGIC)) throw BackupFormatException("فایل بکاپ Solina نیست.")
        val ver = b.get()
        if (ver != VERSION) throw BackupFormatException("نسخهٔ بکاپ پشتیبانی نمی‌شود.")
        val params = KdfParams(b.int, b.int, b.int)
        if (!params.isReasonable()) throw BackupFormatException("پارامترهای رمزنگاری فایل نامعتبر است.")
        return BackupHeaderInfo(version = ver.toInt(), kdf = params, totalBytes = bytes.size)
    }

    fun encode(items: List<ItemPayload>, password: CharArray, now: Long, params: KdfParams = KdfParams.DEFAULT): ByteArray {
        val salt = randomBytes(SALT_SIZE)
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC).put(VERSION)
            .putInt(params.memoryKiB).putInt(params.iterations).putInt(params.parallelism)
            .put(salt).array()

        val arr = JSONArray()
        for (item in items) arr.put(JSONObject(String(item.toBytes(), Charsets.UTF_8)))
        val payload = JSONObject()
            .put("schema", 1)
            .put("exportedAt", now)
            .put("items", arr)
            .toString().toByteArray(Charsets.UTF_8)

        val k = key(password, salt, params)
        return try {
            header + Aead.seal(k, payload, header)
        } finally {
            k.wipe()
            payload.wipe()
        }
    }

    /** بکاپ مستقل فقط برای حساب‌های احراز هویت دومرحله‌ای (TOTP و HOTP). */
    fun encodeAuthenticatorOnly(
        items: List<ItemPayload>,
        password: CharArray,
        now: Long,
        params: KdfParams = KdfParams.DEFAULT,
    ): ByteArray {
        val authItems = items.filter { it.totpSecret.isNotBlank() }
        if (authItems.isEmpty()) throw BackupFormatException("هیچ حساب احراز هویت (TOTP/HOTP) برای بکاپ وجود ندارد.")
        return encode(authItems, password, now, params)
    }

    fun decode(bytes: ByteArray, password: CharArray): List<ItemPayload> {
        val info = inspect(bytes)
        val salt = bytes.copyOfRange(HEADER_SIZE - SALT_SIZE, HEADER_SIZE)
        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val body = bytes.copyOfRange(HEADER_SIZE, bytes.size)

        val k = key(password, salt, info.kdf)
        val plain = try {
            Aead.open(k, body, header)
        } finally {
            k.wipe()
        }
        return try {
            val root = JSONObject(String(plain, Charsets.UTF_8))
            val arr = root.getJSONArray("items")
            List(arr.length()) { i -> ItemPayload.fromBytes(arr.getJSONObject(i).toString().toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            throw BackupFormatException("محتوای بکاپ خراب است.")
        } finally {
            plain.wipe()
        }
    }
}
