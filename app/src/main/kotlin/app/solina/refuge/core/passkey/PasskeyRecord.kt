package app.solina.refuge.core.passkey

import org.json.JSONArray
import org.json.JSONObject

/**
 * رکورد Passkey یک ورودی ورود (Login). این رکورد بخشی از ItemPayload است و مثل بقیهٔ
 * فیلدهای ورودی، داخل blob رمزگذاری‌شدهٔ ورودی ذخیره می‌شود؛ یعنی فقط با کلید خزانه
 * قابل خواندن است و هرگز در SharedPreferences یا متن خام نمی‌نشیند.
 *
 * کلید خصوصی کجاست؟ در جریان client (Credential Manager) کلید خصوصی هرگز به اپ
 * برنمی‌گردد؛ نزد ارائه‌دهندهٔ سیستمی (credential provider) می‌ماند و فقط
 * credentialId و userHandle در دسترس اپ است. پس فیلد [privateKeyPkcs8B64] در این
 * جریان همیشه خالی می‌ماند و فقط برای مدل provider آینده (CredentialProviderService)
 * رزرو شده؛ در آن مدل، خود Refuge ارائه‌دهنده است، جفت‌کلید را می‌سازد و کلید خصوصی
 * را رمزگذاری‌شده در همین رکورد نگه می‌دارد.
 *
 * با پاک‌شدن خزانه یا حذف ورودی، این رکورد هم پاک می‌شود؛ ولی credentialای که نزد
 * ارائه‌دهندهٔ سیستمی ساخته شده باشد با پاک‌شدن خزانه پاک نمی‌شود و کاربر باید آن را
 * از تنظیمات سیستم (Google Password Manager یا مشابه) حذف کند. با بازیابی بکاپ،
 * رکوردهای Passkey همراه ورودی‌ها برمی‌گردند.
 */
data class PasskeyRecord(
    /** شناسهٔ credential (rawId) به Base64url بدون padding. */
    val credentialIdB64: String,
    /** دامنهٔ Relying Party، مثل github.com. */
    val rpId: String,
    /** userHandle (شناسهٔ کاربر نزد RP) به Base64url. */
    val userHandleB64: String,
    val userName: String,
    val displayName: String,
    /** کلید عمومی؛ در جریان client خالی می‌ماند (در attestationObject با CBOR است). */
    val publicKeyB64: String = "",
    /**
     * کلید خصوصی PKCS#8 به Base64 — فقط برای مدل provider آینده؛ در جریان client
     * هرگز پر نمی‌شود. داخل payload رمزگذاری‌شده می‌نشیند، پس با کلید خزانه محافظت می‌شود.
     */
    val privateKeyPkcs8B64: String = "",
    val createdAt: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", credentialIdB64)
        put("rp", rpId)
        put("uh", userHandleB64)
        if (userName.isNotEmpty()) put("un", userName)
        if (displayName.isNotEmpty()) put("dn", displayName)
        if (publicKeyB64.isNotEmpty()) put("pk", publicKeyB64)
        if (privateKeyPkcs8B64.isNotEmpty()) put("sk", privateKeyPkcs8B64)
        if (createdAt != 0L) put("at", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): PasskeyRecord? {
            val id = o.optString("id")
            val rp = o.optString("rp")
            if (id.isEmpty() || rp.isEmpty()) return null
            return PasskeyRecord(
                credentialIdB64 = id,
                rpId = rp,
                userHandleB64 = o.optString("uh"),
                userName = o.optString("un"),
                displayName = o.optString("dn"),
                publicKeyB64 = o.optString("pk"),
                privateKeyPkcs8B64 = o.optString("sk"),
                createdAt = o.optLong("at"),
            )
        }

        fun listToJson(list: List<PasskeyRecord>): JSONArray =
            JSONArray().also { arr -> list.forEach { arr.put(it.toJson()) } }

        fun listFromJson(arr: JSONArray?): List<PasskeyRecord> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull {
                runCatching { fromJson(arr.getJSONObject(it)) }.getOrNull()
            }
        }
    }
}
