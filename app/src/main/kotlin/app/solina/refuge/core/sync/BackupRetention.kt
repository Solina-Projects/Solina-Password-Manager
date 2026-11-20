package app.solina.refuge.core.sync

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * نام‌گذاری و نگهداری نسخه‌های بکاپ خودکار (اسنپ‌شات‌های `refuge-backup-*.rfgb`).
 * خالص و قابل تست (بدون اندروید). نام با زمان UTC ساخته می‌شود تا مرتب‌سازی
 * الفبایی نام‌ها برابر ترتیب زمانی باشد؛ پس «۵ نسخهٔ تازه» یعنی ۵ نام آخر.
 */
object BackupRetention {
    const val KEEP_VERSIONS = 5
    const val PREFIX = "refuge-backup-"
    const val SUFFIX = ".rfgb"

    private val FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    /** نام نسخهٔ جدید، مثل `refuge-backup-20261120-123045.rfgb`. */
    fun snapshotName(now: Long): String = PREFIX + FORMATTER.format(Instant.ofEpochMilli(now)) + SUFFIX

    fun isSnapshotName(name: String): Boolean =
        name.startsWith(PREFIX) && name.endsWith(SUFFIX) &&
            name.length == PREFIX.length + 15 + SUFFIX.length

    /** نام‌های قدیمی که باید حذف شوند تا فقط [keep] نسخهٔ تازه بماند (قدیمی‌ترین‌ها اول). */
    fun selectForDeletion(fileNames: List<String>, keep: Int = KEEP_VERSIONS): List<String> {
        val snapshots = fileNames.filter(::isSnapshotName).sorted()
        if (snapshots.size <= keep) return emptyList()
        return snapshots.dropLast(keep)
    }
}
