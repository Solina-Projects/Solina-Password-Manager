package app.solina.passwordmanager.core.sync

import java.io.IOException
import java.util.UUID

internal interface StagedFileIo {
    fun read(name: String): ByteArray?
    fun create(name: String): Boolean
    fun write(name: String, bytes: ByteArray)
    fun rename(from: String, to: String): Boolean
    fun delete(name: String): Boolean
}

internal class StagedFileWriter(
    private val io: StagedFileIo,
    private val maxBytes: Int,
    private val newToken: () -> String = { UUID.randomUUID().toString() },
) {
    fun write(name: String, bytes: ByteArray) {
        validateName(name)
        if (bytes.size > maxBytes) throw IOException("فایل از حد مجاز بزرگ‌تر است")
        recoverInterruptedReplacement(name, bytes)

        val current = io.read(name)
        if (current != null && current.contentEquals(bytes)) return

        val token = newToken().replace(Regex("[^A-Za-z0-9_-]"), "")
        if (token.isBlank()) throw IOException("شناسهٔ فایل موقت نامعتبر است")
        val temp = "solina-pending-$token"
        val backup = backupName(name)
        if (!io.create(temp)) throw IOException("ساخت فایل موقت ممکن نشد")

        var movedOld = false
        try {
            io.write(temp, bytes)
            val staged = io.read(temp) ?: throw IOException("فایل موقت پس از نوشتن پیدا نشد")
            if (!staged.contentEquals(bytes)) throw IOException("راستی‌آزمایی فایل موقت ناموفق بود")

            if (current != null) {
                if (io.read(backup) != null) throw IOException("فایل پشتیبان موقت قبلی باقی مانده است")
                if (!io.rename(name, backup)) throw IOException("ارائه‌دهنده امکان جایگزینی امن فایل را ندارد")
                movedOld = true
            }

            try {
                if (!io.rename(temp, name)) throw IOException("ثبت فایل نهایی با تغییر نام ممکن نشد")
                val committed = io.read(name)
                if (committed == null || !committed.contentEquals(bytes)) {
                    throw IOException("راستی‌آزمایی فایل نهایی ناموفق بود")
                }
            } catch (failure: Exception) {
                if (movedOld && io.read(name) == null) {
                    if (!io.rename(backup, name)) failure.addSuppressed(IOException("بازیابی نسخهٔ قبلی فایل ناموفق بود"))
                    else movedOld = false
                }
                throw failure
            }

            if (movedOld) {
                if (!io.delete(backup)) throw IOException("فایل پشتیبان قدیمی پاک نشد")
                movedOld = false
            }
        } finally {
            runCatching { io.delete(temp) }
        }
    }

    private fun recoverInterruptedReplacement(name: String, expected: ByteArray) {
        val backup = backupName(name)
        val current = io.read(name)
        val previous = io.read(backup)
        when {
            current == null && previous != null -> {
                if (!io.rename(backup, name)) throw IOException("بازیابی فایل نیمه‌تمام ممکن نشد")
            }
            current != null && previous != null -> {
                if (current.contentEquals(expected)) {
                    if (!io.delete(backup)) throw IOException("پاک‌سازی فایل پشتیبان قدیمی ممکن نشد")
                } else {
                    if (!io.delete(name)) throw IOException("حذف فایل نهایی نیمه‌نوشته ممکن نشد")
                    if (!io.rename(backup, name)) throw IOException("بازیابی نسخهٔ قبلی فایل ناموفق بود")
                }
            }
        }
    }

    private fun backupName(name: String) = "solina-backup-$name"

    private fun validateName(name: String) {
        if (name.length !in 1..160 ||
            name == "." || name == ".." ||
            name.startsWith("solina-pending-") ||
            name.startsWith("solina-backup-") ||
            !Regex("[A-Za-z0-9._-]+").matches(name)
        ) throw IOException("نام فایل همگام‌سازی نامعتبر است")
    }
}
