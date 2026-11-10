package app.solina.passwordmanager.core.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.text.Normalizer

class CorruptVaultException(message: String) : IOException(message)

/**
 * هدر خزانه: پارامترهای KDF، سالت و دو نسخهٔ پیچیده‌شدهٔ Vault Key
 * (یکی با رمز اصلی، یکی با عبارت بازیابی). طبق docs/ARCHITECTURE.md §۲.
 *
 * قالب باینری: "SLNV" | version(1) | memKiB(4) | iter(4) | par(4) | salt(16)
 *              | wrappedPw(60) | wrappedRec(60)
 */
class VaultHeader(
    val version: Int,
    val kdf: KdfParams,
    val salt: ByteArray,
    val wrappedVkByPassword: ByteArray,
    val wrappedVkByRecovery: ByteArray,
) {
    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.write(MAGIC)
            out.writeByte(version)
            out.writeInt(kdf.memoryKiB)
            out.writeInt(kdf.iterations)
            out.writeInt(kdf.parallelism)
            out.write(salt)
            out.write(wrappedVkByPassword)
            out.write(wrappedVkByRecovery)
        }
        return bytes.toByteArray()
    }

    /** بخشی از هدر که به AAD بستهٔ رمز اصلی بسته می‌شود (جلوگیری از جابه‌جایی پارامترها). */
    internal fun passwordAad(): ByteArray = aadForPassword(version, kdf, salt)

    companion object {
        const val CURRENT_VERSION = 1
        const val SALT_SIZE = 16
        /** nonce(12) + کلید(32) + tag(16). */
        const val WRAPPED_SIZE = Aead.NONCE_SIZE + Aead.KEY_SIZE + Aead.TAG_BITS / 8
        private val MAGIC = byteArrayOf('S'.code.toByte(), 'L'.code.toByte(), 'N'.code.toByte(), 'V'.code.toByte())

        internal fun aadForPassword(version: Int, kdf: KdfParams, salt: ByteArray): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.write("solina/vk/pw".toByteArray(Charsets.US_ASCII))
                out.writeByte(version)
                out.writeInt(kdf.memoryKiB)
                out.writeInt(kdf.iterations)
                out.writeInt(kdf.parallelism)
                out.write(salt)
            }
            return bytes.toByteArray()
        }

        internal fun aadForRecovery(version: Int): ByteArray =
            "solina/vk/rec".toByteArray(Charsets.US_ASCII) + byteArrayOf(version.toByte())

        fun decode(data: ByteArray): VaultHeader {
            val expected = MAGIC.size + 1 + 12 + SALT_SIZE + 2 * WRAPPED_SIZE
            if (data.size != expected) throw CorruptVaultException("bad header size")
            try {
                val input = DataInputStream(ByteArrayInputStream(data))
                val magic = ByteArray(MAGIC.size).also { input.readFully(it) }
                if (!magic.contentEquals(MAGIC)) throw CorruptVaultException("bad magic")
                val version = input.readUnsignedByte()
                if (version != CURRENT_VERSION) throw CorruptVaultException("unsupported version $version")
                val mem = input.readInt()
                val iter = input.readInt()
                val par = input.readInt()
                val kdf = try {
                    KdfParams(mem, iter, par)
                } catch (e: IllegalArgumentException) {
                    throw CorruptVaultException("kdf parameters out of range")
                }
                val salt = ByteArray(SALT_SIZE).also { input.readFully(it) }
                val wPw = ByteArray(WRAPPED_SIZE).also { input.readFully(it) }
                val wRec = ByteArray(WRAPPED_SIZE).also { input.readFully(it) }
                return VaultHeader(version, kdf, salt, wPw, wRec)
            } catch (e: IOException) {
                if (e is CorruptVaultException) throw e
                throw CorruptVaultException("truncated header")
            }
        }
    }
}

class SealedItem(val wrappedItemKey: ByteArray, val blob: ByteArray)

/** نتیجهٔ ساخت خزانه. فراخواننده باید [recoveryWords] را فقط یک‌بار به کاربر نشان دهد و نگه ندارد. */
class CreatedVault(val header: VaultHeader, val vaultKey: ByteArray, val recoveryWords: List<String>)

object VaultCrypto {
    private val INFO_PW = "solina/wrap/pw/v1".toByteArray(Charsets.US_ASCII)
    private val INFO_REC = "solina/wrap/rec/v1".toByteArray(Charsets.US_ASCII)

    /** NFKC تا کاراکترهای معادل (مثلاً فارسی/عربی هم‌شکل) در ورودی‌های مختلف یکسان شوند. */
    private fun passwordBytes(password: CharArray): ByteArray =
        Normalizer.normalize(String(password), Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)

    private fun passwordKek(password: CharArray, salt: ByteArray, kdf: KdfParams): ByteArray {
        val pw = passwordBytes(password)
        try {
            val master = Argon2id.derive(pw, salt, kdf)
            try {
                return Hkdf.derive(master, null, INFO_PW)
            } finally {
                master.wipe()
            }
        } finally {
            pw.wipe()
        }
    }

    private fun recoveryKek(entropy: ByteArray): ByteArray = Hkdf.derive(entropy, null, INFO_REC)

    fun create(password: CharArray, kdf: KdfParams = KdfParams.DEFAULT): CreatedVault {
        val vk = randomBytes(Aead.KEY_SIZE)
        val entropy = randomBytes(Bip39.ENTROPY_BYTES)
        val salt = randomBytes(VaultHeader.SALT_SIZE)
        val version = VaultHeader.CURRENT_VERSION

        val kekPw = passwordKek(password, salt, kdf)
        val kekRec = recoveryKek(entropy)
        try {
            val header = VaultHeader(
                version, kdf, salt,
                wrappedVkByPassword = Aead.seal(kekPw, vk, VaultHeader.aadForPassword(version, kdf, salt)),
                wrappedVkByRecovery = Aead.seal(kekRec, vk, VaultHeader.aadForRecovery(version)),
            )
            return CreatedVault(header, vk, Bip39.entropyToWords(entropy))
        } finally {
            kekPw.wipe()
            kekRec.wipe()
            entropy.wipe()
        }
    }

    fun unlockWithPassword(header: VaultHeader, password: CharArray): ByteArray {
        val kek = passwordKek(password, header.salt, header.kdf)
        try {
            return Aead.open(kek, header.wrappedVkByPassword, header.passwordAad())
        } finally {
            kek.wipe()
        }
    }

    fun unlockWithRecovery(header: VaultHeader, phrase: String): ByteArray {
        val entropy = Bip39.wordsToEntropy(phrase)
        val kek = recoveryKek(entropy)
        try {
            return Aead.open(kek, header.wrappedVkByRecovery, VaultHeader.aadForRecovery(header.version))
        } finally {
            kek.wipe()
            entropy.wipe()
        }
    }

    /** تغییر رمز اصلی: فقط Vault Key دوباره پیچیده می‌شود؛ داده‌ها و عبارت بازیابی تغییر نمی‌کنند. */
    fun changePassword(header: VaultHeader, vaultKey: ByteArray, newPassword: CharArray, kdf: KdfParams = header.kdf): VaultHeader {
        val salt = randomBytes(VaultHeader.SALT_SIZE)
        val kek = passwordKek(newPassword, salt, kdf)
        try {
            return VaultHeader(
                header.version, kdf, salt,
                wrappedVkByPassword = Aead.seal(kek, vaultKey, VaultHeader.aadForPassword(header.version, kdf, salt)),
                wrappedVkByRecovery = header.wrappedVkByRecovery,
            )
        } finally {
            kek.wipe()
        }
    }

    // ───── ورودی‌ها: هر ورودی کلید تصادفی خودش را دارد ─────

    private fun itemKeyAad(itemId: String) = "solina/itemkey|$itemId".toByteArray(Charsets.UTF_8)
    private fun blobAad(itemId: String, rev: Long, schema: Int) =
        "solina/item|$itemId|$rev|$schema".toByteArray(Charsets.UTF_8)

    fun sealItem(vaultKey: ByteArray, itemId: String, rev: Long, schema: Int, plaintext: ByteArray): SealedItem {
        val itemKey = randomBytes(Aead.KEY_SIZE)
        try {
            return SealedItem(
                wrappedItemKey = Aead.seal(vaultKey, itemKey, itemKeyAad(itemId)),
                blob = Aead.seal(itemKey, plaintext, blobAad(itemId, rev, schema)),
            )
        } finally {
            itemKey.wipe()
        }
    }

    fun openItem(vaultKey: ByteArray, itemId: String, rev: Long, schema: Int, sealed: SealedItem): ByteArray {
        val itemKey = Aead.open(vaultKey, sealed.wrappedItemKey, itemKeyAad(itemId))
        try {
            return Aead.open(itemKey, sealed.blob, blobAad(itemId, rev, schema))
        } finally {
            itemKey.wipe()
        }
    }
}
