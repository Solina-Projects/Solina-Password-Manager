package app.solina.passwordmanager.ui.components

import app.solina.passwordmanager.DiagLog

/**
 * حذف ورودی‌های با شناسهٔ تکراری پیش از تحویل به LazyColumn.
 *
 * پس‌زمینه: کرش ۲۰۲۶-۱۱-۲۰ روی POCO F3 — «Key ... was already used» در
 * SubcomposeLayout.kt:453 کامپوز ۱٫۷٫۵: یک LazyColumn دو ردیف با یک کلید گرفت
 * (همهٔ لیست‌ها با it.id کلید می‌خورند). شناسهٔ آیتم، هویت منطقی آن است؛ دو
 * ردیف با یک شناسه یعنی یک آیتم که دو بار نشان داده شده، پس نگه‌داشتن اولی
 * درست است و کرش‌کردن هرگز درست نیست. دادهٔ زیرین دست نمی‌خورد (فقط نمایش)،
 * و هر تکراریِ حذف‌شده در DiagLog ثبت می‌شود تا علت اصلی پیدا شود.
 */
fun <T> List<T>.distinctById(tag: String, idOf: (T) -> String): List<T> {
    if (size <= 1) return this
    val seen = HashSet<String>(size)
    var dropped = 0
    var firstDup = ""
    val out = ArrayList<T>(size)
    for (item in this) {
        val id = idOf(item)
        if (seen.add(id)) out.add(item)
        else {
            dropped++
            if (firstDup.isEmpty()) firstDup = id.take(8)
        }
    }
    if (dropped > 0) DiagLog.log("dup-key", "$tag: $dropped ورودی با شناسهٔ تکراری حذف شد (id=$firstDup…)")
    return out
}
