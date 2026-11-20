package app.solina.refuge.core.passkey

import android.content.Context
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import app.solina.refuge.core.crypto.randomBytes
import org.json.JSONObject
import java.util.Base64

/**
 * Passkey با Credential Manager — وضعیت فاز ۲ و دلیل تعویق فراخوانی زنده:
 *
 * ۱) جریان client (همین کلاس) برای ساخت Passkey به یک RP ID نیاز دارد که دامنه‌ای
 *    باشد و اپ با Digital Asset Links به آن وصل شده باشد؛ Refuge هیچ وب‌سایت و
 *    دامنهٔ مرتبطی ندارد، پس ساخت client-side از سوی ارائه‌دهنده رد می‌شود.
 *
 * ۲) مدل درست برای یک مدیر رمز عبور، سمت provider است (CredentialProviderService):
 *    خود Refuge ارائه‌دهنده می‌شود، جفت‌کلید را می‌سازد، کلید خصوصی را رمزگذاری‌شده
 *    در PasskeyRecord نگه می‌دارد و assertion را امضا می‌کند. آن پیاده‌سازی،
 *    سرویس جدا + رمزنگاری FIDO2 (COSE/CBOR و امضای ES256) + تست روی دستگاه واقعی
 *    می‌خواهد و بیرون از این فاز است.
 *
 * پس در این فاز: ذخیره‌سازی ([PasskeyRecord] در ItemPayload) و رابط کاربری کامل است،
 * و فراخوانی‌های واقعی Credential Manager نوشته شده‌اند ولی پشت پرچم
 * [PasskeyGate.LIVE_ENABLED] خاموش‌اند تا وقتی (۱) یا (۲) حل شد، با همان کد روشن شوند.
 * روی اندروید زیر ۱۴ همه‌چیز با پیام فارسی «پشتیبانی نمی‌شود» بسته می‌شود و minSdk
 * تغییر نکرده است (۲۶).
 */
object PasskeyGate {
    /** فراخوانی زندهٔ Credential Manager؛ false تا وقتی RP/دامنه یا سرویس provider آماده شود. */
    const val LIVE_ENABLED = false

    /** ساخت و استفاده از Passkey در Credential Manager از اندروید ۱۴ (API 34) است. */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}

sealed interface PasskeyResult {
    data object UnsupportedDevice : PasskeyResult
    /** فراخوانی زنده پشت پرچم خاموش است (تعویق فاز ۲؛ دلیل در سربرگ فایل). */
    data object Deferred : PasskeyResult
    data class Created(val record: PasskeyRecord) : PasskeyResult
    data class Asserted(val credentialIdB64: String) : PasskeyResult
    data class Failed(val message: String) : PasskeyResult
    data object Cancelled : PasskeyResult
}

/** ساخت JSON های WebAuthn و تجزیهٔ پاسخ‌ها؛ خالص و قابل تست (بدون اندروید). */
object PasskeyRequests {
    private fun b64url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun b64urlOrEmpty(s: String): ByteArray =
        runCatching { Base64.getUrlDecoder().decode(s) }.getOrDefault(ByteArray(0))

    fun buildCreateRequestJson(
        rpId: String,
        userHandle: ByteArray,
        userName: String,
        displayName: String,
        challenge: ByteArray,
    ): String = JSONObject().apply {
        put("rp", JSONObject().put("id", rpId).put("name", rpId))
        put(
            "user",
            JSONObject()
                .put("id", b64url(userHandle))
                .put("name", userName)
                .put("displayName", displayName),
        )
        put("challenge", b64url(challenge))
        put(
            "pubKeyCredParams",
            org.json.JSONArray().put(JSONObject().put("type", "public-key").put("alg", -7)),
        )
        put("timeout", 180_000)
        put("attestation", "none")
        put(
            "authenticatorSelection",
            JSONObject().put("residentKey", "required").put("userVerification", "required"),
        )
    }.toString()

    fun buildGetRequestJson(rpId: String, challenge: ByteArray, credentialIds: List<String>): String =
        JSONObject().apply {
            put("rpId", rpId)
            put("challenge", b64url(challenge))
            put(
                "allowCredentials",
                org.json.JSONArray().also { arr ->
                    credentialIds.forEach { id ->
                        arr.put(JSONObject().put("type", "public-key").put("id", id))
                    }
                },
            )
            put("userVerification", "required")
            put("timeout", 180_000)
        }.toString()

    /**
     * تجزیهٔ registrationResponseJson؛ کلید عمومی در attestationObject (CBOR) است و اینجا
     * خوانده نمی‌شود، پس publicKeyB64 خالی می‌ماند (نگاه کنید به PasskeyRecord).
     */
    fun parseCreateResponse(
        registrationResponseJson: String,
        rpId: String,
        userHandle: ByteArray,
        userName: String,
        displayName: String,
        now: Long = System.currentTimeMillis(),
    ): PasskeyRecord? = runCatching {
        val root = JSONObject(registrationResponseJson)
        val id = root.optString("id").takeIf { it.isNotEmpty() }
            ?: root.optString("rawId").takeIf { it.isNotEmpty() }
            ?: return null
        PasskeyRecord(
            credentialIdB64 = id,
            rpId = rpId,
            userHandleB64 = b64url(userHandle),
            userName = userName,
            displayName = displayName,
            createdAt = now,
        )
    }.getOrNull()

    /** شناسهٔ credential از authenticationResponseJson. */
    fun parseGetResponse(authenticationResponseJson: String): String? = runCatching {
        val root = JSONObject(authenticationResponseJson)
        root.optString("id").takeIf { it.isNotEmpty() }
            ?: root.optString("rawId").takeIf { it.isNotEmpty() }
    }.getOrNull()

    internal fun decodeB64url(s: String): ByteArray = b64urlOrEmpty(s)
}

class PasskeyManager(context: Context) {
    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    /**
     * ساخت Passkey برای یک ورودی ورود.
     * @param activity کانتکست Activity (برای نمایش UI انتخاب ارائه‌دهنده لازم است).
     */
    suspend fun createPasskey(
        activity: Context,
        rpId: String,
        userName: String,
        displayName: String,
    ): PasskeyResult {
        if (!PasskeyGate.isSupported()) return PasskeyResult.UnsupportedDevice
        if (!PasskeyGate.LIVE_ENABLED) return PasskeyResult.Deferred
        val userHandle = randomBytes(32)
        val challenge = randomBytes(32)
        val requestJson = PasskeyRequests.buildCreateRequestJson(rpId, userHandle, userName, displayName, challenge)
        return try {
            val request = CreatePublicKeyCredentialRequest(requestJson)
            val response = credentialManager.createCredential(activity, request)
                as CreatePublicKeyCredentialResponse
            val record = PasskeyRequests.parseCreateResponse(
                response.registrationResponseJson, rpId, userHandle, userName, displayName,
            ) ?: return PasskeyResult.Failed("پاسخ ارائه‌دهنده قابل خواندن نبود.")
            PasskeyResult.Created(record)
        } catch (e: CreateCredentialCancellationException) {
            PasskeyResult.Cancelled
        } catch (e: CreateCredentialException) {
            PasskeyResult.Failed(e.message ?: "ساخت Passkey ناموفق بود.")
        }
    }

    /**
     * ورود با Passkey ذخیره‌شده؛ assertion را برمی‌گرداند (تأیید آن با سرور RP است و
     * در این اپِ آفلاین انجام نمی‌شود — نتیجه فقط شناسهٔ credential است).
     */
    suspend fun signInWithPasskey(activity: Context, record: PasskeyRecord): PasskeyResult {
        if (!PasskeyGate.isSupported()) return PasskeyResult.UnsupportedDevice
        if (!PasskeyGate.LIVE_ENABLED) return PasskeyResult.Deferred
        val challenge = randomBytes(32)
        val requestJson = PasskeyRequests.buildGetRequestJson(record.rpId, challenge, listOf(record.credentialIdB64))
        return try {
            val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson)))
            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential as? PublicKeyCredential
                ?: return PasskeyResult.Failed("پاسخ ارائه‌دهنده Passkey نبود.")
            val id = PasskeyRequests.parseGetResponse(credential.authenticationResponseJson)
                ?: return PasskeyResult.Failed("پاسخ ارائه‌دهنده قابل خواندن نبود.")
            PasskeyResult.Asserted(id)
        } catch (e: GetCredentialCancellationException) {
            PasskeyResult.Cancelled
        } catch (e: GetCredentialException) {
            PasskeyResult.Failed(e.message ?: "ورود با Passkey ناموفق بود.")
        }
    }
}
