package app.solina.passwordmanager.core.storage

/** یک ردیف ذخیره‌شده. فقط id، شمارهٔ ویرایش و زمان‌ها آشکارند؛ هر چیز دیگر داخل [blob] رمزشده است. */
class StoredItem(
    val id: String,
    val rev: Long,
    val updatedAt: Long,
    /** غیر null یعنی حذف‌شده (tombstone برای همگام‌سازی آینده)؛ [blob] در آن حالت خالی است. */
    val deletedAt: Long?,
    val wrappedItemKey: ByteArray,
    val blob: ByteArray,
)

/** نسخهٔ قدیمی یک ورودی که رمزشده نگه داشته می‌شود. */
class StoredHistory(
    val itemId: String,
    val rev: Long,
    val savedAt: Long,
    val wrappedItemKey: ByteArray,
    val blob: ByteArray,
)

/** لایهٔ ذخیره؛ هیچ چیز دربارهٔ رمزنگاری نمی‌داند. */
interface ItemStore {
    suspend fun all(): List<StoredItem>
    suspend fun get(id: String): StoredItem?

    /** ذخیرهٔ اتمیک ورودی جدید و (اختیاری) نسخهٔ قبلی در تاریخچه؛ تاریخچه به [keepHistory] نسخه محدود می‌شود. */
    suspend fun save(item: StoredItem, archivedPrevious: StoredHistory?, keepHistory: Int)

    /** حذف نرم: blob خالی، tombstone می‌ماند تا همگام‌سازی حذف را منتشر کند.
     * تاریخچه حفظ می‌شود تا بازگردانی از سطل زباله ممکن باشد؛ اگر [archivedPrevious] داده شود
     * (نسخهٔ فعلیِ در حال حذف) هم به تاریخچه اضافه می‌شود. */
    suspend fun delete(id: String, deletedAt: Long, newRev: Long, archivedPrevious: StoredHistory? = null)

    /** حذف دائمی ردیف و تاریخچه (خالی کردن سطل زباله / پاک‌سازی tombstone منقضی). */
    suspend fun purge(id: String)

    suspend fun history(itemId: String): List<StoredHistory>
    suspend fun wipeAll()
}
