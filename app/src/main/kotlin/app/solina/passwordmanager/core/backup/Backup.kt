package app.solina.passwordmanager.core.backup

import app.solina.passwordmanager.core.crypto.Aead
import app.solina.passwordmanager.core.crypto.Argon2id
import app.solina.passwordmanager.core.crypto.Hkdf
import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.crypto.randomBytes
import app.solina.passwordmanager.core.crypto.wipe
import app.solina.passwordmanager.core.storage.ItemPayload
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.ByteBuffer
import java.text.Normalizer

class BackupFormatException(message: String) : Exception(message)

/**
 * بکاپ رمزشده (SLNB v1). با «رمز بکاپ» جداگانه محافظت می‌شود (Argon2id ← HKDF ← AES-256-GCM)،
 * نه با کلید خزانه، تا بتوان آن را روی دستگاه دیگر هم باز کرد. هدر در AAD است و دست‌کاری آن شناسایی می‌شود.
 *
 * چیدمان: "SLNB"(4) | نسخه(1) | حافظه KiB(4) | تکرار(4) | موازی(4) | salt(16) | AES-GCM(JSON)
 */
object Backup {
    private val MAGIC = "SLNB".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_SIZE = 16
    private const val HEADER_SIZE = 4 + 1 + 12 + SALT_SIZE
    private val INFO = "solina/backup/v1".toByteArray(Charsets.US_ASCII)

    private fun key(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val pw = Normalizer.normalize(String(password), Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)
        try {
            val master = Argon2id.derive(pw, salt, params)
            try {
                return Hkdf.derive(master, null, INFO)
            } finally {
                master.wipe()
            }
        } finally {
            pw.wipe()
        }
    }

    fun encode(items: List<ItemPayload>, password: CharArray, now: Long, params: KdfParams = KdfParams.DEFAULT): ByteArray {
        val json = JSONObject().apply {
            put("format", "solina-backup")
            put("version", 1)
            put("exportedAt", now)
            put("items", JSONArray().also { arr -> items.forEach { arr.put(JSONObject(String(it.toBytes(), Charsets.UTF_8))) } })
        }.toString().toByteArray(Charsets.UTF_8)

        val salt = randomBytes(SALT_SIZE)
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC).put(VERSION).putInt(params.memoryKiB).putInt(params.iterations).putInt(params.parallelism).put(salt)
            .array()
        val k = key(password, salt, params)
        try {
            return header + Aead.seal(k, json, header)
        } finally {
            k.wipe()
            json.wipe()
        }
    }

    /** @throws BackupFormatException فایل بکاپ سولینا نیست یا ناقص است؛ @throws AuthenticationFailedException رمز اشتباه یا فایل دست‌کاری‌شده. */
    fun decode(bytes: ByteArray, password: CharArray): List<ItemPayload> {
        if (bytes.size < HEADER_SIZE + 28 || !bytes.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw BackupFormatException("این فایل یک بکاپ Solina نیست.")
        }
        if (bytes[4] != VERSION) throw BackupFormatException("نسخهٔ این بکاپ پشتیبانی نمی‌شود.")
        val bb = ByteBuffer.wrap(bytes, 5, 12)
        val params = try {
            KdfParams(bb.int, bb.int, bb.int)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("هدر بکاپ نامعتبر است.")
        }
        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val salt = bytes.copyOfRange(HEADER_SIZE - SALT_SIZE, HEADER_SIZE)
        val k = key(password, salt, params)
        val plain = try {
            Aead.open(k, bytes.copyOfRange(HEADER_SIZE, bytes.size), header)
        } finally {
            k.wipe()
        }
        try {
            val root = JSONObject(String(plain, Charsets.UTF_8))
            if (root.optString("format") != "solina-backup") throw BackupFormatException("محتوای بکاپ ناشناخته است.")
            val arr = root.getJSONArray("items")
            return (0 until arr.length()).map { ItemPayload.fromBytes(arr.getJSONObject(it).toString().toByteArray(Charsets.UTF_8)) }
        } catch (e: JSONException) {
            throw BackupFormatException("محتوای بکاپ خراب است.")
        } finally {
            plain.wipe()
        }
    }
}
