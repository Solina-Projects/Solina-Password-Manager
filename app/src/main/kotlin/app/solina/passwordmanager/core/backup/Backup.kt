package app.solina.passwordmanager.core.backup

import app.solina.passwordmanager.core.crypto.Aead
import app.solina.passwordmanager.core.crypto.Argon2id
import app.solina.passwordmanager.core.crypto.Hkdf
import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.crypto.VaultCrypto
import app.solina.passwordmanager.core.crypto.randomBytes
import app.solina.passwordmanager.core.crypto.wipe
import app.solina.passwordmanager.core.storage.ItemPayload
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.Base64

class BackupFormatException(message: String) : Exception(message)

/** متادیتای اعتبارسنجی‌شدهٔ هدر فایل بکاپ پیش از دریافت رمز عبور. */
data class BackupHeaderInfo(
    val version: Int,
    val kdf: KdfParams,
    val totalBytes: Int,
)

/** نتیجهٔ کامل رمزگشایی بکاپ شامل ورودی‌ها و در صورت وجود، هدر اصلی خزانه (رمز اصلی و ۲۴ کلمهٔ قبلی). */
data class DecodedBackup(
    val items: List<ItemPayload>,
    val vaultHeader: ByteArray?,
)

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
        val pw = VaultCrypto.passwordBytes(password)
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

    /** آیا بایت‌ها با سرآیند رسمی بکاپ Solina («SLNB») شروع می‌شوند؟ */
    fun isSolinaBackup(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** نمایش خوانای اندازهٔ فایل بکاپ. */
    fun formatByteSize(bytes: Int): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "${(bytes + 512) / 1024} KB"
        else -> "$bytes B"
    }

    /** اعتبارسنجی هدر و پارامترهای KDF پیش از نمایش صفحهٔ ورود رمز بازیابی. */
    fun inspect(bytes: ByteArray): BackupHeaderInfo {
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
        return BackupHeaderInfo(version = bytes[4].toInt() and 0xff, kdf = params, totalBytes = bytes.size)
    }

    fun encode(
        items: List<ItemPayload>,
        password: CharArray,
        now: Long,
        params: KdfParams = KdfParams.DEFAULT,
        vaultHeader: ByteArray? = null,
    ): ByteArray {
        val json = JSONObject().apply {
            put("format", "solina-backup")
            put("version", 1)
            put("exportedAt", now)
            if (vaultHeader != null && vaultHeader.isNotEmpty()) {
                put("vaultHeader", Base64.getEncoder().encodeToString(vaultHeader))
            }
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

    /** بکاپ مستقل و رمزشده صرفاً برای حساب‌های Authenticator (TOTP و HOTP). */
    fun encodeAuthenticatorOnly(
        items: List<ItemPayload>,
        password: CharArray,
        now: Long,
        params: KdfParams = KdfParams.DEFAULT,
        vaultHeader: ByteArray? = null,
    ): ByteArray {
        val otpItems = items.filter { it.totpSecret.isNotBlank() }
        if (otpItems.isEmpty()) {
            throw BackupFormatException("هیچ حساب Authenticator (کد TOTP/HOTP) برای بکاپ وجود ندارد.")
        }
        return encode(otpItems, password, now, params, vaultHeader)
    }

    /** رمزگشایی کامل بکاپ (ورودی‌ها + هدر اصلی خزانه در صورت وجود). */
    fun decodeFull(bytes: ByteArray, password: CharArray): DecodedBackup {
        val info = inspect(bytes)
        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val salt = bytes.copyOfRange(HEADER_SIZE - SALT_SIZE, HEADER_SIZE)
        val k = key(password, salt, info.kdf)
        val plain = try {
            Aead.open(k, bytes.copyOfRange(HEADER_SIZE, bytes.size), header)
        } finally {
            k.wipe()
        }
        try {
            val root = JSONObject(String(plain, Charsets.UTF_8))
            if (root.optString("format") != "solina-backup") throw BackupFormatException("محتوای بکاپ ناشناخته است.")
            val arr = root.getJSONArray("items")
            val items = (0 until arr.length()).map {
                ItemPayload.fromBytes(arr.getJSONObject(it).toString().toByteArray(Charsets.UTF_8))
            }
            val vhB64 = root.optString("vaultHeader", "")
            val vh = if (vhB64.isNotEmpty()) runCatching { Base64.getDecoder().decode(vhB64) }.getOrNull() else null
            return DecodedBackup(items = items, vaultHeader = vh)
        } catch (e: JSONException) {
            throw BackupFormatException("محتوای بکاپ خراب است.")
        } finally {
            plain.wipe()
        }
    }

    /** @throws BackupFormatException فایل بکاپ سولینا نیست یا ناقص است؛ @throws AuthenticationFailedException رمز اشتباه یا فایل دست‌کاری‌شده. */
    fun decode(bytes: ByteArray, password: CharArray): List<ItemPayload> = decodeFull(bytes, password).items
}
