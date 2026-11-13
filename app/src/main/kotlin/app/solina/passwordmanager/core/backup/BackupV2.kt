package app.solina.passwordmanager.core.backup

import app.solina.passwordmanager.core.crypto.Aead
import app.solina.passwordmanager.core.crypto.Argon2id
import app.solina.passwordmanager.core.crypto.Bip39
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

/**
 * SLNB v2 codec. v1 remains handled by [Backup] and is never reinterpreted here.
 *
 * Layout (big endian): preamble(50) || passwordWrappedBEK(60) ||
 * recoveryWrappedBEK(60) || payload(nonce || ciphertext || tag).
 * Both unlock slots are mandatory in every v2 file.
 */
object BackupV2 {
    private val MAGIC = "SLNB".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 2
    private const val FLAGS = 0x03
    private const val SALT_SIZE = 16
    private const val BEK_SIZE = 32
    private const val WRAPPED_BEK_SIZE = 12 + BEK_SIZE + 16
    private const val PREAMBLE_SIZE = 4 + 1 + 1 + 12 + SALT_SIZE + SALT_SIZE
    private const val MIN_FILE_SIZE = PREAMBLE_SIZE + WRAPPED_BEK_SIZE * 2 + 12 + 16
    private const val MAX_PAYLOAD_SIZE = 64 * 1024 * 1024

    private val PASSWORD_INFO = "solina/backup/v2/password-wrap".toByteArray(Charsets.US_ASCII)
    private val RECOVERY_INFO = "solina/backup/v2/recovery-wrap".toByteArray(Charsets.US_ASCII)
    private val PASSWORD_AAD_LABEL = "solina/slnb/v2/wrap/password".toByteArray(Charsets.US_ASCII)
    private val RECOVERY_AAD_LABEL = "solina/slnb/v2/wrap/recovery".toByteArray(Charsets.US_ASCII)

    fun isV2(bytes: ByteArray): Boolean =
        bytes.size >= 5 && bytes.copyOfRange(0, 4).contentEquals(MAGIC) && bytes[4].toInt() and 0xff == VERSION

    /** Creates a v2 file with both password and 24-word recovery unlock slots. */
    fun encode(
        items: List<ItemPayload>,
        password: CharArray,
        recoveryPhrase: String,
        now: Long,
        params: KdfParams = KdfParams.DEFAULT,
    ): ByteArray {
        val entropy = Bip39.wordsToEntropy(recoveryPhrase)
        val passwordSalt = randomBytes(SALT_SIZE)
        val recoverySalt = randomBytes(SALT_SIZE)
        val bek = randomBytes(BEK_SIZE)
        var passwordKey: ByteArray? = null
        var recoveryKey: ByteArray? = null
        var passwordWrapped: ByteArray? = null
        var recoveryWrapped: ByteArray? = null
        var payload: ByteArray? = null
        var json: ByteArray? = null
        try {
            val preamble = makePreamble(params, passwordSalt, recoverySalt)
            passwordKey = derivePasswordKey(password, passwordSalt, params)
            recoveryKey = Hkdf.derive(entropy, recoverySalt, RECOVERY_INFO)
            passwordWrapped = Aead.seal(passwordKey, bek, preamble + PASSWORD_AAD_LABEL)
            recoveryWrapped = Aead.seal(recoveryKey, bek, preamble + RECOVERY_AAD_LABEL)
            json = encodeItems(items, now)
            payload = Aead.seal(bek, json, preamble + passwordWrapped + recoveryWrapped)
            if (payload.size - 28 > MAX_PAYLOAD_SIZE) throw BackupFormatException("حجم محتوای بکاپ بیش از حد مجاز است.")
            return preamble + passwordWrapped + recoveryWrapped + payload
        } finally {
            entropy.wipe()
            bek.wipe()
            passwordKey?.wipe()
            recoveryKey?.wipe()
            json?.wipe()
            // Wrapped keys and ciphertext are not plaintext secrets, but avoid retaining temporary copies.
            passwordWrapped?.wipe()
            recoveryWrapped?.wipe()
            payload?.wipe()
            passwordSalt.wipe()
            recoverySalt.wipe()
        }
    }

    fun decodeWithPassword(bytes: ByteArray, password: CharArray): List<ItemPayload> {
        val parsed = parse(bytes)
        val passwordSalt = parsed.preamble.copyOfRange(18, 34)
        val params = parsed.params
        val key = derivePasswordKey(password, passwordSalt, params)
        try {
            val bek = Aead.open(key, parsed.passwordWrapped, parsed.preamble + PASSWORD_AAD_LABEL)
            try {
                return openPayload(parsed, bek)
            } finally {
                bek.wipe()
            }
        } finally {
            key.wipe()
            passwordSalt.wipe()
        }
    }

    fun decodeWithRecoveryPhrase(bytes: ByteArray, recoveryPhrase: String): List<ItemPayload> {
        val parsed = parse(bytes)
        val entropy = try {
            Bip39.wordsToEntropy(recoveryPhrase)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("عبارت بازیابی معتبر نیست.")
        }
        val recoverySalt = parsed.preamble.copyOfRange(34, 50)
        val key = try {
            Hkdf.derive(entropy, recoverySalt, RECOVERY_INFO)
        } finally {
            entropy.wipe()
            recoverySalt.wipe()
        }
        try {
            val bek = Aead.open(key, parsed.recoveryWrapped, parsed.preamble + RECOVERY_AAD_LABEL)
            try {
                return openPayload(parsed, bek)
            } finally {
                bek.wipe()
            }
        } finally {
            key.wipe()
        }
    }

    private data class Parsed(
        val preamble: ByteArray,
        val params: KdfParams,
        val passwordWrapped: ByteArray,
        val recoveryWrapped: ByteArray,
        val payload: ByteArray,
    )

    private fun parse(bytes: ByteArray): Parsed {
        if (bytes.size < MIN_FILE_SIZE || bytes.size > PREAMBLE_SIZE + WRAPPED_BEK_SIZE * 2 + MAX_PAYLOAD_SIZE + 28) {
            throw BackupFormatException("اندازهٔ فایل بکاپ نامعتبر است.")
        }
        if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC) || (bytes[4].toInt() and 0xff) != VERSION) {
            throw BackupFormatException("این فایل بکاپ SLNB v2 نیست.")
        }
        if ((bytes[5].toInt() and 0xff) != FLAGS) throw BackupFormatException("پرچم‌های بکاپ پشتیبانی نمی‌شوند.")
        val bb = ByteBuffer.wrap(bytes, 6, 12)
        val params = try {
            KdfParams(bb.int, bb.int, bb.int)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("پارامترهای امنیتی بکاپ نامعتبر یا بیش از حد بزرگ‌اند.")
        }
        val preamble = bytes.copyOfRange(0, PREAMBLE_SIZE)
        var offset = PREAMBLE_SIZE
        val passwordWrapped = bytes.copyOfRange(offset, offset + WRAPPED_BEK_SIZE)
        offset += WRAPPED_BEK_SIZE
        val recoveryWrapped = bytes.copyOfRange(offset, offset + WRAPPED_BEK_SIZE)
        offset += WRAPPED_BEK_SIZE
        val payload = bytes.copyOfRange(offset, bytes.size)
        if (payload.size - 28 > MAX_PAYLOAD_SIZE) throw BackupFormatException("حجم محتوای بکاپ بیش از حد مجاز است.")
        return Parsed(preamble, params, passwordWrapped, recoveryWrapped, payload)
    }

    private fun makePreamble(params: KdfParams, passwordSalt: ByteArray, recoverySalt: ByteArray): ByteArray =
        ByteBuffer.allocate(PREAMBLE_SIZE)
            .put(MAGIC).put(VERSION.toByte()).put(FLAGS.toByte())
            .putInt(params.memoryKiB).putInt(params.iterations).putInt(params.parallelism)
            .put(passwordSalt).put(recoverySalt).array()

    private fun derivePasswordKey(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val normalized = Normalizer.normalize(String(password), Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)
        try {
            val master = Argon2id.derive(normalized, salt, params)
            try {
                return Hkdf.derive(master, null, PASSWORD_INFO)
            } finally {
                master.wipe()
            }
        } finally {
            normalized.wipe()
        }
    }

    private fun encodeItems(items: List<ItemPayload>, now: Long): ByteArray =
        JSONObject().apply {
            put("format", "solina-backup")
            put("version", 2)
            put("exportedAt", now)
            put("items", JSONArray().also { arr ->
                items.forEach { arr.put(JSONObject(String(it.toBytes(), Charsets.UTF_8))) }
            })
        }.toString().toByteArray(Charsets.UTF_8)

    private fun openPayload(parsed: Parsed, bek: ByteArray): List<ItemPayload> {
        val aad = parsed.preamble + parsed.passwordWrapped + parsed.recoveryWrapped
        val plain = Aead.open(bek, parsed.payload, aad)
        try {
            val root = JSONObject(String(plain, Charsets.UTF_8))
            if (root.optString("format") != "solina-backup" || root.optInt("version") != 2) {
                throw BackupFormatException("محتوای بکاپ v2 ناشناخته است.")
            }
            val arr = root.getJSONArray("items")
            return (0 until arr.length()).map {
                ItemPayload.fromBytes(arr.getJSONObject(it).toString().toByteArray(Charsets.UTF_8))
            }
        } catch (e: JSONException) {
            throw BackupFormatException("محتوای بکاپ خراب است.")
        } finally {
            plain.wipe()
        }
    }
}
