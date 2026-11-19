package app.solina.passwordmanager.core.sync

import java.net.URI
import java.net.URLDecoder

/**
 * ابزارهای پروتکل WebDAV (مستقل از اندروید، قابل تست روی JVM).
 * - فقط HTTPS را می‌پذیرد.
 * - نام فایل‌ها را در برابر Path Traversal اعتبارسنجی می‌کند.
 */
object WebDavProtocol {
    private val HREF_REGEX = Regex("""<[^:>]*:?href[^>]*>([^<]+)</[^:>]*:?href>""", RegexOption.IGNORE_CASE)
    private val SAFE_NAME = Regex("""^[A-Za-z0-9._-]{1,128}$""")

    fun normalizeHttpsBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.startsWith("https://", ignoreCase = true)) {
            "نشانی سرور همگام‌سازی باید با https:// شروع شود."
        }
        val uri = URI(trimmed)
        require(!uri.host.isNullOrBlank()) { "دامنهٔ سرور معتبر نیست." }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    fun isSafeFileName(name: String): Boolean =
        SAFE_NAME.matches(name) && name != "." && name != ".." && !name.contains("..")

    fun parsePropfindFileNames(xml: String, collectionPath: String): List<String> {
        val normCol = collectionPath.trimEnd('/')
        val out = LinkedHashSet<String>()
        for (m in HREF_REGEX.findAll(xml)) {
            val rawHref = m.groupValues[1].trim()
            val path = runCatching {
                val u = URI(rawHref)
                u.rawPath ?: rawHref
            }.getOrDefault(rawHref).trimEnd('/')
            if (path.isEmpty() || path == normCol) continue
            val segment = path.substringAfterLast('/')
            val decoded = runCatching { URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)
            if (isSafeFileName(decoded)) out.add(decoded)
        }
        return out.toList()
    }
}
