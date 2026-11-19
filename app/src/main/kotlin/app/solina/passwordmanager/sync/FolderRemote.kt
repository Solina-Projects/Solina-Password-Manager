package app.solina.passwordmanager.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.solina.passwordmanager.core.sync.SyncRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * پوشهٔ همگام‌سازی روی هر ارائه‌دهندهٔ فایل اندروید (حافظهٔ داخلی، Syncthing، برنامهٔ Drive، Nextcloud ...)
 * از طریق Storage Access Framework؛ بدون مجوز اضافه و بدون شبکه در خود Solina.
 * همه‌چیز داخل زیرپوشهٔ `Solina-Sync` نوشته می‌شود. فقط بایت‌های رمزشده/احراز‌شده روی پوشه می‌روند.
 */
class FolderRemote(private val context: Context, private val tree: Uri) : SyncRemote {
    companion object {
        const val DIR = "Solina-Sync"
        const val HEADER = "vault.hdr"
        private const val MAX_READ = 8 * 1024 * 1024
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

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = dir(false)?.findFile(name) ?: return@withContext null
        context.contentResolver.openInputStream(f.uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_READ) return@withContext null
            }
            out.toByteArray()
        }
    }

    /**
     * نوشتن امن (تا حد امکان اتمی در SAF): ابتدا در فایل موقت می‌نویسد، flush می‌کند،
     * سپس جایگزین فایل هدف می‌کند تا قطع وسط نوشتن باعث فایل ۰ بایتی یا ناقص نشود.
     */
    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val d = dir(true) ?: throw IOException("پوشه در دسترس نیست")
        val tmpName = "$name.tmp-${System.nanoTime()}"
        val tmp = d.createFile(MIME, tmpName)
        if (tmp != null) {
            try {
                context.contentResolver.openOutputStream(tmp.uri, "w")?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: throw IOException("نوشتن فایل ممکن نشد")
                val existing = d.findFile(name)
                existing?.delete()
                if (tmp.renameTo(name)) return@withContext
                // اگر ارائه‌دهنده SAF تغییر نام را پشتیبانی نکرد، فایل موقت را پاک و مستقیم می‌نویسیم
                tmp.delete()
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }
        val existing = d.findFile(name)
        val target = existing ?: d.createFile(MIME, name) ?: throw IOException("ساخت فایل ممکن نشد")
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IOException("نوشتن فایل ممکن نشد")
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { dir(false)?.findFile(name)?.delete() }
    }

    suspend fun readHeader(): ByteArray? = read(HEADER)
    suspend fun writeHeader(bytes: ByteArray) = write(HEADER, bytes)
}
