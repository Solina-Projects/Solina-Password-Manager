package app.solina.passwordmanager.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.solina.passwordmanager.core.sync.SyncRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val d = dir(true) ?: throw java.io.IOException("پوشه در دسترس نیست")
        val existing = d.findFile(name)
        val target = existing ?: d.createFile(MIME, name) ?: throw java.io.IOException("ساخت فایل ممکن نشد")
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(bytes) }
            ?: throw java.io.IOException("نوشتن فایل ممکن نشد")
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { dir(false)?.findFile(name)?.delete() }
    }

    suspend fun readHeader(): ByteArray? = read(HEADER)
    suspend fun writeHeader(bytes: ByteArray) = write(HEADER, bytes)
}
