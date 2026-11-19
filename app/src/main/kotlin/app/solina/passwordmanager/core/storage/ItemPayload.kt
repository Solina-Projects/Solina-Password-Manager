package app.solina.passwordmanager.core.storage

import app.solina.passwordmanager.core.OtpType
import org.json.JSONObject

enum class ItemKind(val code: String) {
    Login("login"),
    Card("card"),
    Identity("identity"),
    Note("note");

    companion object {
        fun fromCode(code: String): ItemKind = entries.firstOrNull { it.code == code } ?: Login
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
) {
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
            )
        }
    }
}

data class VaultItem(val id: String, val rev: Long, val payload: ItemPayload)

data class HistoryEntry(val rev: Long, val savedAt: Long, val payload: ItemPayload)
