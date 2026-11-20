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
 * اگر کاربر خود پوشهٔ `Solina-Sync` (حاوی `vault.hdr`) را مستقیم انتخاب کند نیز به‌درستی تشخیص داده می‌شود.
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
        // اگر کاربر مستقیم خود پوشهٔ Solina-Sync را انتخاب کرده باشد (دارای vault.hdr)، همان را استفاده کن
        if (r.findFile(HEADER)?.isFile == true) return r
        r.findFile(DIR)?.let { return if (it.isDirectory) it else null }
        return if (create) r.createDirectory(DIR) else null
    }

    fun displayName(): String = root()?.name ?: "پوشه"

    /** بررسی وجود فایل‌های بکاپ ‎.slnb‎ درون پوشهٔ انتخاب‌شده برای راهنمایی دقیق کاربر. */
    suspend fun findBackupFiles(): List<String> = withContext(Dispatchers.IO) {
        val r = root() ?: return@withContext emptyList()
        r.listFiles()
            .filter { it.isFile && it.name?.endsWith(".slnb", ignoreCase = true) == true }
            .mapNotNull { it.name }
    }

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
                tmp.delete()
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }
        val existing = d.findFile(name)
        val target = existing ?: d.createFile(MIME, name) ?: throw IOException("ساخت فایل ممکن نشد")
        val stream = runCatching { context.contentResolver.openOutputStream(target.uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(target.uri, "w")
            ?: throw IOException("نوشتن فایل ممکن نشد")
        stream.use { out ->
            out.write(bytes)
            out.flush()
        }
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { dir(false)?.findFile(name)?.delete() }
    }

    suspend fun readHeader(): ByteArray? = read(HEADER)
    suspend fun writeHeader(bytes: ByteArray) = write(HEADER, bytes)
}
