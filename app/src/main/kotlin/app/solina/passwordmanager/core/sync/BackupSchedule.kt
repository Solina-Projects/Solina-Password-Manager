package app.solina.passwordmanager.core.sync

/**
 * زمان‌بندی پشتیبان/همگام‌سازی. پشتیبان فقط وقتی اجرا می‌شود که خزانه باز است
 * (کلید فقط در حافظهٔ جلسه است و بدون آن نمی‌توان فایل‌ها را رمز و امضا کرد).
 */
enum class BackupSchedule(val periodMillis: Long?) {
    OnChange(0),
    Daily(24L * 3600_000),
    Weekly(7L * 24 * 3600_000),
    Monthly(30L * 24 * 3600_000),
    Manual(null);

    companion object {
        fun parse(name: String?): BackupSchedule = entries.firstOrNull { it.name == name } ?: OnChange
    }
}

enum class BackupTrigger { Unlock, Change }

/** آیا اجرای خودکار الان لازم است؟ lastAt = زمان آخرین همگام‌سازی موفق (۰ = هرگز). */
fun shouldRunAutomatically(schedule: BackupSchedule, lastAt: Long, now: Long, trigger: BackupTrigger): Boolean {
    val period = schedule.periodMillis ?: return false // دستی
    if (schedule == BackupSchedule.OnChange) return true
    if (lastAt <= 0L) return true
    return now - lastAt >= period
}
