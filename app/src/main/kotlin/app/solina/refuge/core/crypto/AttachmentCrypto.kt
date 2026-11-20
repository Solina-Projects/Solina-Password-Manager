package app.solina.refuge.core.crypto

/**
 * پیوست رمزگذاری‌شده (فاز ۴، آیتم ۶) — لایهٔ رمزنگاری خالص، آمادهٔ استفاده؛
 * هنوز به خزانه (Room/UI) و بکاپ وصل نشده است و وصل‌کردنش در این فاز تعمداً انجام نشد.
 * دلیل را در بخش «وضعیت اتصال» پایین بخوانید.
 *
 * ══════════════════════════ طراحی (ثابت‌شده با تست) ══════════════════════════
 *
 * ۱) سلسله‌مراتب کلید (هیچ تغییری در KDF اصلی خزانه نیست):
 *    - کلید خزانه (vaultKey، همان ۳۲ بایت AES که VaultCrypto می‌سازد) عوض نمی‌شود.
 *    - هر پیوست یک DEK تصادفی ۳۲ بایتیِ خودش دارد (per-file DEK).
 *    - DEK با کلید خزانه و AES-256-GCM بسته می‌شود (wrappedDek)؛ AAD آن
 *      "refuge/attachment/dek/v1" + شناسهٔ فایل است، پس DEK یک فایل با فایل دیگر عوض نمی‌شود.
 *    - محتوای فایل با DEK و AES-256-GCM بسته می‌شود (sealedData)؛ AAD آن
 *      "refuge/attachment/data/v1" + شناسهٔ فایل است.
 *    - نتیجه: اگر یک DEK لو برود فقط همان یک فایل لو می‌رود؛ اگر کلید خزانه عوض شود
 *      (تغییر رمز اصلی) فقط wrappedDekها باید دوباره بسته شوند، نه کل داده‌ها.
 *
 * ۲) شناسهٔ فایل (fileId): رشته‌ای یکتا که فراخواننده می‌سازد (مثلاً UUID)؛
 *    در AAD هر دو لایه بسته شده است، پس جابه‌جایی فایل‌ها شناسایی می‌شود.
 *
 * ۳) محدودیت اندازه: حداکثر ۱۰ مگابایت برای هر فایل (MAX_ATTACHMENT_BYTES).
 *    بیشتر از آن رد می‌شود تا حافظهٔ گوشی پر نشود و بکاپ غول‌پیکر نسازد.
 *
 * ۴) بکاپ (تصمیم امنیتی مهم): پیوست‌ها در بکاپ RFGB v1 نیستند و Backup.encode دست
 *    نخورده است — پس بکاپ‌های v1 دقیقاً مثل قبل خوانده می‌شوند (سازگاری عقب‌رو کامل).
 *    دلیل: گنجاندن پیوست در بکاپ یعنی تغییر قالب به v2 (هدر/نسخه/JSON جدید)؛
 *    آن تغییر در این فاز بدون بازبینی مستقل قالب، امن شمرده نشد. تا وقتی پیوست به
 *    بکاپ وصل شود، کاربر باید بداند: «بازیابی از بکاپ، پیوست‌ها را برنمی‌گرداند»
 *    و این جمله باید در UI همان‌جا که پیوست اضافه می‌شود نوشته شود.
 *
 * ══════════════════════════ وضعیت اتصال (واقعیت امروز) ══════════════════════════
 *
 * - این فایل فقط رمز/رمزگشایی می‌کند و تست رفت‌وبرگشت + دست‌کاری دارد.
 * - ذخیره‌سازی (Room: جدول جدید + مهاجرت 2→3)، اتصال به ورودی خزانه، UI افزودن/
 *   دانلود پیوست، و ورود به بکاپ (v2) انجام نشده است — چون هرکدام بدون مهاجرت
 *   امن Room و قالب بکاپ v2، ریسک از دست رفتن داده داشت.
 * - قدم بعدی (فاز بعد): جدول attachments با ستون‌های (fileId, itemId, mime,
 *   size, wrappedDek, sealedData) + مهاجرت 2→3 + تست مهاجرت + جملهٔ هشدار بکاپ در UI.
 */
object AttachmentCrypto {
    /** حداکثر اندازهٔ هر پیوست: ۱۰ مگابایت. */
    const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024

    const val FORMAT_VERSION = 1

    private val AAD_DEK_PREFIX = "refuge/attachment/dek/v1/".toByteArray(Charsets.US_ASCII)
    private val AAD_DATA_PREFIX = "refuge/attachment/data/v1/".toByteArray(Charsets.US_ASCII)

    /** پیوست بسته‌شده: DEKِ بسته‌شده با کلید خزانه + دادهٔ بسته‌شده با DEK. */
    data class SealedAttachment(
        val wrappedDek: ByteArray,
        val sealedData: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SealedAttachment) return false
            return wrappedDek.contentEquals(other.wrappedDek) && sealedData.contentEquals(other.sealedData)
        }

        override fun hashCode(): Int {
            var result = wrappedDek.contentHashCode()
            result = 31 * result + sealedData.contentHashCode()
            return result
        }
    }

    private fun aad(prefix: ByteArray, fileId: String): ByteArray =
        prefix + fileId.toByteArray(Charsets.UTF_8)

    /**
     * رمزگذاری یک فایل.
     * @param vaultKey کلید ۳۲ بایتی خزانه (هرگز ذخیره یا لاگ نشود).
     * @param fileId شناسهٔ یکتای فایل (مثلاً UUID)؛ خالی نباشد.
     * @param data محتوای فایل؛ حداکثر [MAX_ATTACHMENT_BYTES].
     * @throws IllegalArgumentException اگر کلید ۳۲ بایت نباشد، fileId خالی باشد یا فایل بیش از حد بزرگ باشد.
     */
    fun seal(vaultKey: ByteArray, fileId: String, data: ByteArray): SealedAttachment {
        require(vaultKey.size == Aead.KEY_SIZE) { "vaultKey must be 32 bytes" }
        require(fileId.isNotBlank()) { "fileId must not be blank" }
        require(data.size <= MAX_ATTACHMENT_BYTES) { "attachment exceeds 10 MB limit" }
        val dek = randomBytes(Aead.KEY_SIZE)
        try {
            val wrappedDek = Aead.seal(vaultKey, dek, aad(AAD_DEK_PREFIX, fileId))
            val sealedData = Aead.seal(dek, data, aad(AAD_DATA_PREFIX, fileId))
            return SealedAttachment(wrappedDek, sealedData)
        } finally {
            dek.wipe()
        }
    }

    /**
     * رمزگشایی یک فایل.
     * @throws AuthenticationFailedException اگر کلید، شناسهٔ فایل یا هر بایت از
     * دادهٔ بسته‌شده عوض شده باشد (دست‌کاری شناسایی می‌شود).
     */
    fun open(vaultKey: ByteArray, fileId: String, sealed: SealedAttachment): ByteArray {
        require(vaultKey.size == Aead.KEY_SIZE) { "vaultKey must be 32 bytes" }
        require(fileId.isNotBlank()) { "fileId must not be blank" }
        val dek = Aead.open(vaultKey, sealed.wrappedDek, aad(AAD_DEK_PREFIX, fileId))
        try {
            return Aead.open(dek, sealed.sealedData, aad(AAD_DATA_PREFIX, fileId))
        } finally {
            dek.wipe()
        }
    }
}
