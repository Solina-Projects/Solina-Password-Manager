package app.solina.refuge.core.storage

/**
 * کمک‌تابع‌های مشترک برچسب‌ها (Tags)، قابل استفادهٔ خزانه، Authenticator و بعداً «یادداشت و کارها».
 * برچسب چندمقداری و مکمل دسته‌بندی (category) است، نه جایگزین آن.
 */
object TagHelper {
    const val MAX_TAGS = 20
    const val MAX_TAG_LENGTH = 30

    /** نرمال‌سازی یک برچسب خام: پیرایش، خالی → null، برش به حداکثر طول. */
    fun normalize(raw: String): String? {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
        return t.take(MAX_TAG_LENGTH)
    }

    /** تجزیهٔ ورودی کاربر (جدا با ویرگول فارسی/انگلیسی یا خط جدید) به فهرست یکتا. */
    fun parseTags(input: String): List<String> {
        val out = ArrayList<String>()
        for (part in input.split(',', '،', '\n')) {
            val t = normalize(part) ?: continue
            if (out.none { it.equals(t, ignoreCase = true) }) out.add(t)
        }
        return out.take(MAX_TAGS)
    }

    fun addTag(tags: List<String>, raw: String): List<String> {
        val t = normalize(raw) ?: return tags
        if (tags.size >= MAX_TAGS) return tags
        if (tags.any { it.equals(t, ignoreCase = true) }) return tags
        return tags + t
    }

    fun removeTag(tags: List<String>, tag: String): List<String> =
        tags.filterNot { it.equals(tag, ignoreCase = true) }

    /** همهٔ برچسب‌های موجود در فهرست، مرتب‌شده و یکتا (برای چیپ‌های فیلتر). */
    fun allTags(items: List<VaultItem>): List<String> =
        items.flatMap { it.payload.tags }.mapNotNull(::normalize).distinct().sorted()

    fun matchesTag(payload: ItemPayload, tag: String): Boolean =
        payload.tags.any { it.equals(tag.trim(), ignoreCase = true) }
}
