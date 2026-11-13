package app.solina.passwordmanager.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.solina.passwordmanager.core.sync.StagedFileIo
import app.solina.passwordmanager.core.sync.StagedFileWriter
import app.solina.passwordmanager.core.sync.SyncRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * پوشهٔ همگام‌سازی روی ارائه‌دهندگان فایل اندروید از طریق SAF.
 * فقط بایت‌های رمزشده/احراز‌شده نوشته می‌شوند؛ فایل ابتدا با نام موقت نوشته و راستی‌آزمایی
 * می‌شود و سپس به نام نهایی تغییر نام می‌دهد تا فایل نیمه‌نوشته به‌عنوان رکورد معتبر دیده نشود.
 */
class FolderRemote(private val context: Context, private val tree: Uri) : SyncRemote {
    companion object {
        const val DIR = "Solina-Sync"
        const val HEADER = "vault.hdr"
        private const val MAX_READ = 8 * 1024 * 1024
        private const val MAX_WRITE = 8 * 1024 * 1024
        private const val MIME = "application/octet-stream"
    }

    private fun root(): DocumentFile? = DocumentFile.fromTreeUri(context, tree)

    private fun dir(create: Boolean): DocumentFile? {
        val r = root() ?: return null
        r.findFile(DIR)?.let { return if (it.isDirectory) it else null }
        return if (create) r.createDirectory(DIR) else null
    }

    fun displayName(): String = root()?.name ?: "پوشه"

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        dir(false)?.listFiles()?.mapNotNull { it.name }.orEmpty()
    }

    private fun readFrom(d: DocumentFile, name: String): ByteArray? {
        val f = d.findFile(name) ?: return null
        val input = context.contentResolver.openInputStream(f.uri)
            ?: throw java.io.IOException("خواندن فایل ممکن نشد")
        input.use {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                if (out.size().toLong() + n > MAX_READ) throw java.io.IOException("فایل از حد مجاز بزرگ‌تر است")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        dir(false)?.let { readFrom(it, name) }
    }

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val d = dir(true) ?: throw java.io.IOException("پوشه در دسترس نیست")
        val io = object : StagedFileIo {
            private val cached = HashMap<String, DocumentFile>()

            private fun resolve(fileName: String): DocumentFile? =
                cached[fileName] ?: d.findFile(fileName) ?: d.findFile("$fileName.bin")

            override fun read(fileName: String): ByteArray? {
                val f = resolve(fileName) ?: return null
                val input = context.contentResolver.openInputStream(f.uri)
                    ?: throw java.io.IOException("خواندن فایل ممکن نشد")
                input.use {
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = it.read(buf)
                        if (n < 0) break
                        if (out.size().toLong() + n > MAX_READ) throw java.io.IOException("فایل از حد مجاز بزرگ‌تر است")
                        out.write(buf, 0, n)
                    }
                    return out.toByteArray()
                }
            }

            override fun create(fileName: String): Boolean {
                val created = d.createFile(MIME, fileName) ?: return false
                cached[fileName] = created
                return true
            }

            override fun write(fileName: String, data: ByteArray) {
                val f = resolve(fileName) ?: throw java.io.IOException("فایل موقت پیدا نشد")
                context.contentResolver.openOutputStream(f.uri, "wt")?.use { output ->
                    output.write(data)
                    output.flush()
                } ?: throw java.io.IOException("نوشتن فایل ممکن نشد")
            }

            override fun rename(from: String, to: String): Boolean {
                val f = resolve(from) ?: return false
                val ok = runCatching { f.renameTo(to) }.getOrDefault(false)
                if (ok) {
                    cached.remove(from)
                    cached[to] = f
                }
                return ok
            }

            override fun delete(fileName: String): Boolean {
                val f = resolve(fileName) ?: return true
                val ok = f.delete()
                if (ok) cached.remove(fileName)
                return ok
            }
        }
        StagedFileWriter(io, MAX_WRITE).write(name, bytes)
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) {
            val f = dir(false)?.findFile(name) ?: return@withContext
            if (!f.delete()) throw java.io.IOException("حذف فایل همگام‌سازی ممکن نشد")
        }
    }

    suspend fun readHeader(): ByteArray? = read(HEADER)
    suspend fun writeHeader(bytes: ByteArray) = write(HEADER, bytes)
}
