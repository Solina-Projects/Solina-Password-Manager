package app.solina.passwordmanager.core.sync

import app.solina.passwordmanager.core.crypto.Hkdf
import app.solina.passwordmanager.core.storage.StoredItem
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * رکورد همگام‌سازی یک ورودی (فایل `.slni`). محتوای حساس همان blob رمزشدهٔ ورودی است؛ فیلدهای آشکار
 * (شناسه، ویرایش، زمان‌ها) با HMAC مبتنی بر کلید خزانه احراز هویت می‌شوند تا کسی که فقط به پوشه دسترسی دارد
 * نتواند رکورد (مثلاً حذف) جعل کند.
 *
 * چیدمان: "SLNI" | v(1) | idLen(2) id | rev(8) | updatedAt(8) | deletedAt(8, -1=زنده) | keyLen(2) key | blobLen(4) blob | hmac(32)
 */
object SyncRecord {
    private val MAGIC = "SLNI".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val MAX_BLOB = 4 * 1024 * 1024
    private val MAC_INFO = "solina/sync/mac/v1".toByteArray(Charsets.US_ASCII)
    private val ID_REGEX = Regex("[A-Za-z0-9_-]{1,64}")

    fun isValidId(id: String) = ID_REGEX.matches(id)

    fun macKey(vaultKey: ByteArray): ByteArray = Hkdf.derive(vaultKey, null, MAC_INFO)

    private fun mac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    fun encode(item: StoredItem, macKey: ByteArray): ByteArray {
        val id = item.id.toByteArray(Charsets.UTF_8)
        val body = ByteBuffer.allocate(4 + 1 + 2 + id.size + 8 + 8 + 8 + 2 + item.wrappedItemKey.size + 4 + item.blob.size)
            .put(MAGIC).put(VERSION)
            .putShort(id.size.toShort()).put(id)
            .putLong(item.rev).putLong(item.updatedAt).putLong(item.deletedAt ?: -1L)
            .putShort(item.wrappedItemKey.size.toShort()).put(item.wrappedItemKey)
            .putInt(item.blob.size).put(item.blob)
            .array()
        return body + mac(macKey, body)
    }

    /** null اگر ساختار نامعتبر یا HMAC نادرست باشد (کلید دیگر، جعل یا خرابی). */
    fun decode(bytes: ByteArray, macKey: ByteArray): StoredItem? {
        if (bytes.size < 4 + 1 + 2 + 8 + 8 + 8 + 2 + 4 + 32) return null
        val body = bytes.copyOfRange(0, bytes.size - 32)
        val tag = bytes.copyOfRange(bytes.size - 32, bytes.size)
        if (!MessageDigest.isEqual(tag, mac(macKey, body))) return null
        return try {
            val bb = ByteBuffer.wrap(body)
            val magic = ByteArray(4).also { bb.get(it) }
            if (!magic.contentEquals(MAGIC) || bb.get() != VERSION) return null
            val idLen = bb.short.toInt() and 0xffff
            if (idLen !in 1..64) return null
            val id = String(ByteArray(idLen).also { bb.get(it) }, Charsets.UTF_8)
            if (!isValidId(id)) return null
            val rev = bb.long
            val updatedAt = bb.long
            val deletedAt = bb.long.takeIf { it != -1L }
            val keyLen = bb.short.toInt() and 0xffff
            if (keyLen > 256 || keyLen > bb.remaining()) return null
            val key = ByteArray(keyLen).also { bb.get(it) }
            val blobLen = bb.int
            if (blobLen < 0 || blobLen > MAX_BLOB || blobLen != bb.remaining()) return null
            val blob = ByteArray(blobLen).also { bb.get(it) }
            if (rev < 1) return null
            if (deletedAt != null && (blob.isNotEmpty() || key.isNotEmpty())) return null
            if (deletedAt == null && blob.isEmpty()) return null
            StoredItem(id, rev, updatedAt, deletedAt, key, blob)
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    fun contentHash(recordBytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(recordBytes))

    /** نام فایل: `i-<id>-<rev>-<hash8>.slni` */
    fun fileName(id: String, rev: Long, recordBytes: ByteArray) = "i-$id-$rev-${contentHash(recordBytes).take(8)}.slni"

    class Name(val id: String, val rev: Long, val hash8: String)

    fun parseName(name: String): Name? {
        // بعضی ارائه‌دهندگان SAF پسوند .bin اضافه می‌کنند
        val n = name.removeSuffix(".bin")
        if (!n.startsWith("i-") || !n.endsWith(".slni")) return null
        val core = n.substring(2, n.length - 5)
        val parts = core.split('-')
        if (parts.size < 3) return null
        val hash8 = parts.last()
        val rev = parts[parts.size - 2].toLongOrNull() ?: return null
        val id = parts.dropLast(2).joinToString("-")
        if (!isValidId(id) || rev < 1 || !Regex("[0-9a-f]{8}").matches(hash8)) return null
        return Name(id, rev, hash8)
    }
}
