package app.solina.passwordmanager.core.security

import app.solina.passwordmanager.core.crypto.CorruptVaultException
import app.solina.passwordmanager.core.crypto.VaultHeader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class UnlockState(val failedAttempts: Int, val lastFailureAt: Long)

/**
 * ذخیرهٔ فایل‌های خزانه در پوشهٔ خصوصی اپ. نوشتن اتمی است: اول فایل موقت، fsync، بعد rename،
 * تا قطع برق وسط نوشتن هدر معتبر قبلی را خراب نکند.
 */
class VaultFiles(private val dir: File) {
    private val header = File(dir, "vault.hdr")
    private val biometric = File(dir, "biometric.bin")
    private val unlockState = File(dir, "unlock.state")

    init {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("cannot create vault dir")
    }

    fun hasVault(): Boolean = header.isFile

    fun writeHeader(h: VaultHeader) = atomicWrite(header, h.encode())

    /** @throws CorruptVaultException اگر فایل خراب یا دست‌کاری شده باشد. */
    fun readHeader(): VaultHeader {
        if (!header.isFile) throw CorruptVaultException("no vault")
        return VaultHeader.decode(header.readBytes())
    }

    fun hasBiometric(): Boolean = biometric.isFile
    fun writeBiometric(blob: ByteArray) = atomicWrite(biometric, blob)
    fun readBiometric(): ByteArray? = if (biometric.isFile) biometric.readBytes() else null
    fun clearBiometric() {
        biometric.delete()
    }

    /** شمارندهٔ تلاش ناموفق و زمان آخرین شکست (میلی‌ثانیهٔ ساعت دیواری)؛ برای تأخیر فزاینده. */
    fun readUnlockState(): UnlockState = runCatching {
        val parts = unlockState.readText().trim().split(",")
        UnlockState(parts[0].toInt().coerceIn(0, 1000), parts[1].toLong().coerceAtLeast(0))
    }.getOrDefault(UnlockState(0, 0))

    fun writeUnlockState(state: UnlockState) =
        atomicWrite(unlockState, "${state.failedAttempts},${state.lastFailureAt}".toByteArray())

    /** حذف کامل خزانه (فقط با تأیید صریح کاربر). */
    fun deleteAll() {
        header.delete()
        biometric.delete()
        unlockState.delete()
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val tmp = File(dir, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("cannot replace ${target.name}")
        }
    }
}
