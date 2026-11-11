package app.solina.passwordmanager.core.storage

import org.json.JSONObject

enum class ItemKind(val code: String) {
    Login("login"), Card("card"), Note("note");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: Login
    }
}

/** محتوای رمزشدهٔ یک ورودی. تنها داخل blob رمزشده ذخیره می‌شود. */
data class ItemPayload(
    val kind: ItemKind = ItemKind.Login,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val note: String = "",
    /** راز TOTP به‌صورت Base32 (همان‌طور که کاربر وارد کرده). */
    val totpSecret: String = "",
    val favorite: Boolean = false,
    val cardHolder: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val createdAt: Long = 0,
    val editedAt: Long = 0,
) {
    fun toBytes(): ByteArray = JSONObject().apply {
        put("kind", kind.code)
        put("title", title)
        put("username", username)
        put("password", password)
        put("website", website)
        put("note", note)
        put("totp", totpSecret)
        put("fav", favorite)
        put("cardHolder", cardHolder)
        put("cardNumber", cardNumber)
        put("cardExpiry", cardExpiry)
        put("cardCvv", cardCvv)
        put("created", createdAt)
        put("edited", editedAt)
    }.toString().toByteArray(Charsets.UTF_8)

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
            )
        }
    }
}

data class VaultItem(val id: String, val rev: Long, val payload: ItemPayload)

data class HistoryEntry(val rev: Long, val savedAt: Long, val payload: ItemPayload)
