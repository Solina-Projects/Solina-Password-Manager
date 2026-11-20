package app.solina.refuge.core.storage

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

/**
 * فرادادهٔ متن‌باز موعد یک کار (فاز ۳): فقط شناسه و زمان موعد، بدون هیچ عنوان یا متنی.
 * یادآور WorkManager با همین جدول و درحالی‌که خزانه قفل است کار می‌کند؛ چون هیچ محتوایی
 * اینجا نیست، نوتیفیکیشن هم فقط پیام کلی نشان می‌دهد.
 */
class TaskDueMeta(val id: String, val dueAt: Long, val notifiedAt: Long)

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

    // ── فرادادهٔ موعد کارها (فاز ۳) ──
    // پیاده‌سازی پیش‌فرض خالی است تا ذخیره‌های آزمایشی قدیمی نشکنند؛ RoomItemStore آن را بازنویسی می‌کند.
    /** ثبت/به‌روزرسانی موعد متن‌باز یک کار؛ فقط برای کارهای ناتمامِ دارای موعد صدا زده می‌شود. */
    suspend fun saveTaskDue(id: String, dueAt: Long, notifiedAt: Long = 0L) {}
    /** حذف ردیف موعد (کار انجام شد، موعد برداشته شد، یا ورودی حذف شد). */
    suspend fun deleteTaskDue(id: String) {}
    /** همهٔ موعدهای متن‌باز (برای یادآور پس‌زمینه در حالت قفل). */
    suspend fun allTaskDues(): List<TaskDueMeta> = emptyList()
}
