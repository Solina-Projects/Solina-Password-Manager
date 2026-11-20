package app.solina.refuge.core.auth

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

enum class OAuthProvider(
    val id: String,
    val displayName: String,
    val authEndpoint: String,
    val tokenEndpoint: String,
    val userInfoEndpoint: String,
    val scopes: String,
) {
    Google(
        id = "google",
        displayName = "Google / Gmail",
        authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenEndpoint = "https://oauth2.googleapis.com/token",
        userInfoEndpoint = "https://openidconnect.googleapis.com/v1/userinfo",
        scopes = "openid email profile",
    ),
    GitHub(
        id = "github",
        displayName = "GitHub",
        authEndpoint = "https://github.com/login/oauth/authorize",
        tokenEndpoint = "https://github.com/login/oauth/access_token",
        userInfoEndpoint = "https://api.github.com/user",
        scopes = "read:user user:email",
    );

    companion object {
        fun fromId(id: String): OAuthProvider? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * نمایهٔ حساب خارجی متصل‌شده (فقط هویت عمومی؛ هیچ رمز خزانه یا کلید محرمانه‌ای ذخیره یا ارسال نمی‌شود).
 */
data class LinkedExternalAccount(
    val id: String,
    val provider: OAuthProvider,
    val subject: String,
    val email: String,
    val displayName: String,
    val linkedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("provider", provider.id)
        .put("subject", subject)
        .put("email", email)
        .put("displayName", displayName)
        .put("linkedAt", linkedAt)

    companion object {
        fun fromJson(o: JSONObject): LinkedExternalAccount? {
            val prov = OAuthProvider.fromId(o.optString("provider")) ?: return null
            val id = o.optString("id").ifBlank { return null }
            return LinkedExternalAccount(
                id = id,
                provider = prov,
                subject = o.optString("subject"),
                email = o.optString("email"),
                displayName = o.optString("displayName"),
                linkedAt = o.optLong("linkedAt"),
            )
        }

        fun serializeList(list: List<LinkedExternalAccount>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun deserializeList(raw: String): List<LinkedExternalAccount> = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

data class OAuthPendingRequest(
    val provider: OAuthProvider,
    val clientId: String,
    val redirectUri: String,
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val createdAt: Long,
)

sealed interface OAuthCallbackResult {
    data class Code(val code: String, val state: String) : OAuthCallbackResult
    data class Error(val error: String) : OAuthCallbackResult
}

/**
 * پیاده‌سازی استاندارد OAuth 2.0 + PKCE (RFC 7636) و OIDC برای اتصال اختیاری Google و GitHub.
 */
object OAuthPkce {
    const val REDIRECT_URI = "refuge-auth://oauth/callback"

    private fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun generateCodeVerifier(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return base64UrlNoPad(bytes)
    }

    /** RFC 7636 §4.2: BASE64URL-ENCODE(SHA256(ASCII(code_verifier))). */
    fun computeCodeChallengeS256(codeVerifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return base64UrlNoPad(hash)
    }

    fun generateRandomToken(byteCount: Int = 24, random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return base64UrlNoPad(bytes)
    }

    fun createPendingRequest(
        provider: OAuthProvider,
        clientId: String,
        redirectUri: String = REDIRECT_URI,
        now: Long = System.currentTimeMillis(),
        random: SecureRandom = SecureRandom(),
    ): OAuthPendingRequest = OAuthPendingRequest(
        provider = provider,
        clientId = clientId.trim(),
        redirectUri = redirectUri,
        state = generateRandomToken(24, random),
        nonce = generateRandomToken(24, random),
        codeVerifier = generateCodeVerifier(random),
        createdAt = now,
    )

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    fun buildAuthorizationUrl(request: OAuthPendingRequest): String {
        require(request.clientId.isNotBlank()) { "OAuth client_id must not be blank" }
        val challenge = computeCodeChallengeS256(request.codeVerifier)
        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to request.clientId,
            "redirect_uri" to request.redirectUri,
            "scope" to request.provider.scopes,
            "state" to request.state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
        )
        if (request.provider == OAuthProvider.Google) {
            params["nonce"] = request.nonce
            params["prompt"] = "select_account"
        }
        val query = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        return "${request.provider.authEndpoint}?$query"
    }

    fun parseCallbackQuery(query: String?, expectedState: String): OAuthCallbackResult {
        if (query.isNullOrBlank()) return OAuthCallbackResult.Error("پاسخ OAuth خالی است.")
        val map = query.split('&').filter { '=' in it }.associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val err = map["error"]
        if (!err.isNullOrBlank()) return OAuthCallbackResult.Error(map["error_description"] ?: err)
        val state = map["state"]
        if (state.isNullOrBlank() || state != expectedState) {
            return OAuthCallbackResult.Error("عدم تطابق state امنیتی (احتمال CSRF).")
        }
        val code = map["code"]
        if (code.isNullOrBlank()) return OAuthCallbackResult.Error("کد احراز هویت دریافت نشد.")
        return OAuthCallbackResult.Code(code = code, state = state)
    }
}
