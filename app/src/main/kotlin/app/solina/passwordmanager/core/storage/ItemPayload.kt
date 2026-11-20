package app.solina.passwordmanager.core.storage

import app.solina.passwordmanager.core.OtpType
import app.solina.passwordmanager.core.passkey.PasskeyRecord
import org.json.JSONObject

enum class ItemKind(val code: String) {
    Login("login"),
    Card("card"),
    Identity("identity"),
    Note("note"),
    /** یادداشت سریع بخش «یادداشت و کارها» (فاز ۳) — با Note خزانه فرق دارد و در تب خزانه نشان داده نمی‌شود. */
    QuickNote("quicknote"),
    /** کار بخش «یادداشت و کارها» (فاز ۳) — در تب خزانه نشان داده نمی‌شود. */
    Task("task");

    companion object {
        fun fromCode(code: String): ItemKind = entries.firstOrNull { it.code == code } ?: Login
    }
}

/** یک ردیف چک‌لیست داخل یادداشت (متن و وضعیت تیک، هر دو داخل blob رمز می‌شوند). */
data class CheckItem(val text: String = "", val done: Boolean = false)

/** یک فیلد سفارشی کاربر (برچسب، مقدار، پنهان‌بودن مقدار). حداکثر ۲۰ فیلد؛ برچسب ≤ ۶۴ و مقدار ≤ ۵۱۲ کاراکتر. */
data class CustomField(val label: String = "", val value: String = "", val hidden: Boolean = false) {
    companion object {
        const val MAX_FIELDS = 20
        const val MAX_LABEL = 64
        const val MAX_VALUE = 512

        fun sanitize(label: String, value: String, hidden: Boolean): CustomField? {
            val l = label.trim().take(MAX_LABEL)
            val v = value.take(MAX_VALUE)
            if (l.isEmpty() || v.isEmpty()) return null
            return CustomField(l, v, hidden)
        }
    }
}

/** اولویت کار: زیاد / متوسط / کم. */
enum class TaskPriority(val code: String) {
    High("high"), Medium("medium"), Low("low");
    companion object {
        fun fromCode(code: String): TaskPriority = entries.firstOrNull { it.code == code } ?: Medium
    }
}

/** تکرار کار: بدون تکرار / روزانه / هفتگی / ماهانه. */
enum class TaskRepeat(val code: String) {
    None("none"), Daily("daily"), Weekly("weekly"), Monthly("monthly");
    companion object {
        fun fromCode(code: String): TaskRepeat = entries.firstOrNull { it.code == code } ?: None
    }
}

/**
 * محتوای رمزگشایی‌شدهٔ یک ورودی خزانه. این مدل به‌شکل JSON درون blob رمز می‌شود،
 * بنابراین هیچ‌یک از فیلدهایش در دیتابیس یا فایل همگام‌سازی آشکار نیست.
 * با نسخهٔ قبلی (v1) کاملاً سازگار به عقب است.
 */
data class ItemPayload(
    val kind: ItemKind = ItemKind.Login,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val note: String = "",
    val totpSecret: String = "",
    val favorite: Boolean = false,
    // کارت بانکی
    val cardHolder: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val createdAt: Long = 0,
    val editedAt: Long = 0,
    // تنظیمات Authenticator (TOTP & HOTP)
    val totpDigits: Int = 6,
    val totpPeriod: Int = 30,
    val totpAlgorithm: String = "SHA1",
    val otpType: OtpType = OtpType.TOTP,
    val totpCounter: Long = 0L,
    // دسته‌بندی و آیکون اختصاصی
    val category: String = "",
    val icon: String = "",
    // برچسب‌های چندگانه (مکمل دسته‌بندی، نه جایگزین آن)
    val tags: List<String> = emptyList(),
    // فیلدهای تکمیلی کارت
    val bankName: String = "",
    val cardPin: String = "",
    // هویت و نشانی (Identity)
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val nationalId: String = "",
    val address: String = "",
    val city: String = "",
    val postalCode: String = "",
    val country: String = "",
    val company: String = "",
    // Passkeyها (فاز ۲) — داخل همین payload رمزگذاری‌شده می‌نشینند، نه در SharedPreferences.
    val passkeys: List<PasskeyRecord> = emptyList(),
    // ── یادداشت و کارها (فاز ۳) — همهٔ این فیلدها داخل blob رمز می‌شوند؛ هیچ‌کدام
    // در دیتابیس آشکار نیستند. تنها استثنا dueAt است که برای یادآوری در حالت قفل،
    // به‌صورت جداگانه و فقط به‌عنوان زمان (بدون عنوان/متن) در جدول task_due نگه داشته می‌شود.
    /** متن کامل یادداشت (بدنه)؛ عنوان از خط اول آن گرفته می‌شود. */
    val body: String = "",
    /** چک‌لیست داخل یادداشت. */
    val checklist: List<CheckItem> = emptyList(),
    /** اولویت کار. */
    val priority: TaskPriority = TaskPriority.Medium,
    /** موعد کار (epoch millis)؛ ۰ یعنی بدون موعد. */
    val dueAt: Long = 0L,
    /** تکرار کار. */
    val repeat: TaskRepeat = TaskRepeat.None,
    /** کار انجام شده است. */
    val done: Boolean = false,
    /** سنجاق‌شده (بالای فهرست و در «امروز» حتی بدون موعد). */
    val pinned: Boolean = false,
    /** بایگانی‌شده (از فهرست اصلی پنهان، قابل بازگردانی). */
    val archived: Boolean = false,
    /** شناسهٔ ورودی خزانهٔ مرتبط (برای یادداشت یا کار). */
    val linkedItemId: String = "",
    /** شناسهٔ یادداشت مرتبط (برای کار). */
    val linkedNoteId: String = "",
    /** فیلدهای سفارشی (فاز ۴) — برای همهٔ نوع‌های ورودی؛ داخل blob رمز می‌شوند. */
    val customFields: List<CustomField> = emptyList(),
) {
    /**
     * «فقط‌کد»: فقط راز OTP دارد و هیچ دادهٔ خزانه‌ای ندارد (نه رمز، نه وب‌سایت،
     * نه کارت، نه هویت، نه یادداشت امن، نه فیلد سفارشی، نه پس‌کی).
     * ورودی‌های ایمپورت‌شده از Aegis/2FAS/andOTP دقیقاً همین شکل‌اند (kind=Login،
     * فقط title/username + totpSecret). چنین ورودی‌ای متعلق به بخش «کدها» است و
     * هرگز نباید در فهرست، جست‌وجو یا جزئیات خزانه دیده شود.
     */
    fun isCodeOnly(): Boolean {
        if (totpSecret.isBlank()) return false
        if (password.isNotBlank() || website.isNotBlank() || cardNumber.isNotBlank() || bankName.isNotBlank()
            || cardHolder.isNotBlank() || cardExpiry.isNotBlank() || cardCvv.isNotBlank() || cardPin.isNotBlank()
            || fullName.isNotBlank() || email.isNotBlank() || phone.isNotBlank() || nationalId.isNotBlank()
            || address.isNotBlank() || city.isNotBlank() || postalCode.isNotBlank() || country.isNotBlank()
            || company.isNotBlank() || body.isNotBlank() || customFields.isNotEmpty() || passkeys.isNotEmpty()
        ) return false
        if (kind == ItemKind.Note && note.isNotBlank()) return false
        return true
    }

    fun toBytes(): ByteArray {
        val o = JSONObject()
        o.put("kind", kind.code)
        o.put("title", title)
        if (username.isNotEmpty()) o.put("username", username)
        if (password.isNotEmpty()) o.put("password", password)
        if (website.isNotEmpty()) o.put("website", website)
        if (note.isNotEmpty()) o.put("note", note)
        if (totpSecret.isNotEmpty()) o.put("totp", totpSecret)
        if (favorite) o.put("fav", true)
        if (cardHolder.isNotEmpty()) o.put("cardHolder", cardHolder)
        if (cardNumber.isNotEmpty()) o.put("cardNumber", cardNumber)
        if (cardExpiry.isNotEmpty()) o.put("cardExpiry", cardExpiry)
        if (cardCvv.isNotEmpty()) o.put("cardCvv", cardCvv)
        if (createdAt != 0L) o.put("created", createdAt)
        if (editedAt != 0L) o.put("edited", editedAt)
        if (totpDigits != 6) o.put("totpDigits", totpDigits)
        if (totpPeriod != 30) o.put("totpPeriod", totpPeriod)
        if (totpAlgorithm != "SHA1") o.put("totpAlgo", totpAlgorithm)
        if (otpType != OtpType.TOTP) o.put("otpType", otpType.code)
        if (totpCounter != 0L) o.put("totpCounter", totpCounter)
        if (category.isNotEmpty()) o.put("category", category)
        if (tags.isNotEmpty()) o.put("tags", org.json.JSONArray(tags))
        if (icon.isNotEmpty()) o.put("icon", icon)
        if (bankName.isNotEmpty()) o.put("bankName", bankName)
        if (cardPin.isNotEmpty()) o.put("cardPin", cardPin)
        if (fullName.isNotEmpty()) o.put("fullName", fullName)
        if (email.isNotEmpty()) o.put("email", email)
        if (phone.isNotEmpty()) o.put("phone", phone)
        if (nationalId.isNotEmpty()) o.put("nationalId", nationalId)
        if (address.isNotEmpty()) o.put("address", address)
        if (city.isNotEmpty()) o.put("city", city)
        if (postalCode.isNotEmpty()) o.put("postalCode", postalCode)
        if (country.isNotEmpty()) o.put("country", country)
        if (company.isNotEmpty()) o.put("company", company)
        if (passkeys.isNotEmpty()) o.put("passkeys", PasskeyRecord.listToJson(passkeys))
        if (body.isNotEmpty()) o.put("body", body)
        if (checklist.isNotEmpty()) {
            val arr = org.json.JSONArray()
            for (c in checklist) {
                val co = JSONObject()
                co.put("t", c.text)
                if (c.done) co.put("d", true)
                arr.put(co)
            }
            o.put("checklist", arr)
        }
        if (priority != TaskPriority.Medium) o.put("priority", priority.code)
        if (dueAt != 0L) o.put("dueAt", dueAt)
        if (repeat != TaskRepeat.None) o.put("repeat", repeat.code)
        if (done) o.put("done", true)
        if (pinned) o.put("pinned", true)
        if (archived) o.put("archived", true)
        if (linkedItemId.isNotEmpty()) o.put("linkedItemId", linkedItemId)
        if (linkedNoteId.isNotEmpty()) o.put("linkedNoteId", linkedNoteId)
        if (customFields.isNotEmpty()) {
            val arr = org.json.JSONArray()
            for (f in customFields.take(CustomField.MAX_FIELDS)) {
                val fo = JSONObject()
                fo.put("l", f.label)
                fo.put("v", f.value)
                if (f.hidden) fo.put("h", true)
                arr.put(fo)
            }
            o.put("customFields", arr)
        }
        return o.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        const val SCHEMA = 1

        fun fromBytes(bytes: ByteArray): ItemPayload {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            return ItemPayload(
                kind = ItemKind.fromCode(o.optString("kind")),
                title = o.optString("title"),
                username = o.optString("username"),
                password = o.optString("password"),
                website = o.optString("website"),
                note = o.optString("note"),
                totpSecret = o.optString("totp"),
                favorite = o.optBoolean("fav"),
                cardHolder = o.optString("cardHolder"),
                cardNumber = o.optString("cardNumber"),
                cardExpiry = o.optString("cardExpiry"),
                cardCvv = o.optString("cardCvv"),
                createdAt = o.optLong("created"),
                editedAt = o.optLong("edited"),
                totpDigits = o.optInt("totpDigits", 6),
                totpPeriod = o.optInt("totpPeriod", 30),
                totpAlgorithm = o.optString("totpAlgo", "SHA1"),
                otpType = OtpType.fromCode(o.optString("otpType", "totp")),
                totpCounter = o.optLong("totpCounter", 0L),
                category = o.optString("category"),
                tags = o.optJSONArray("tags")?.let { arr ->
                    List(arr.length()) { i -> arr.optString(i) }.mapNotNull { TagHelper.normalize(it) }
                } ?: emptyList(),
                icon = o.optString("icon"),
                bankName = o.optString("bankName"),
                cardPin = o.optString("cardPin"),
                fullName = o.optString("fullName"),
                email = o.optString("email"),
                phone = o.optString("phone"),
                nationalId = o.optString("nationalId"),
                address = o.optString("address"),
                city = o.optString("city"),
                postalCode = o.optString("postalCode"),
                country = o.optString("country"),
                company = o.optString("company"),
                passkeys = PasskeyRecord.listFromJson(o.optJSONArray("passkeys")),
                body = o.optString("body"),
                checklist = o.optJSONArray("checklist")?.let { arr ->
                    List(arr.length()) { i ->
                        val co = arr.optJSONObject(i) ?: return@let emptyList<CheckItem>()
                        CheckItem(co.optString("t"), co.optBoolean("d"))
                    }.filter { it.text.isNotBlank() }
                } ?: emptyList(),
                priority = TaskPriority.fromCode(o.optString("priority", "medium")),
                dueAt = o.optLong("dueAt", 0L),
                repeat = TaskRepeat.fromCode(o.optString("repeat", "none")),
                done = o.optBoolean("done"),
                pinned = o.optBoolean("pinned"),
                archived = o.optBoolean("archived"),
                linkedItemId = o.optString("linkedItemId"),
                linkedNoteId = o.optString("linkedNoteId"),
                customFields = o.optJSONArray("customFields")?.let { arr ->
                    List(arr.length()) { i ->
                        val fo = arr.optJSONObject(i) ?: return@let emptyList<CustomField>()
                        CustomField.sanitize(fo.optString("l"), fo.optString("v"), fo.optBoolean("h"))
                    }.filterNotNull().take(CustomField.MAX_FIELDS)
                } ?: emptyList(),
            )
        }
    }
}

data class VaultItem(val id: String, val rev: Long, val payload: ItemPayload)

data class HistoryEntry(val rev: Long, val savedAt: Long, val payload: ItemPayload)
