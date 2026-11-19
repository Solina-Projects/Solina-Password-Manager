package app.solina.passwordmanager.auth

import android.content.Context
import android.net.Uri
import app.solina.passwordmanager.DiagLog
import app.solina.passwordmanager.core.auth.LinkedExternalAccount
import app.solina.passwordmanager.core.auth.OAuthCallbackResult
import app.solina.passwordmanager.core.auth.OAuthPendingRequest
import app.solina.passwordmanager.core.auth.OAuthPkce
import app.solina.passwordmanager.core.auth.OAuthProvider
import app.solina.passwordmanager.core.security.KeystoreSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed interface OAuthLinkResult {
    data class Linked(val account: LinkedExternalAccount) : OAuthLinkResult
    data class Failed(val reason: String) : OAuthLinkResult
}

/**
 * مدیریت اتصال اختیاری حساب‌های Google (OIDC) و GitHub (OAuth 2.0 + PKCE).
 *
 * تضمین‌های Zero-Knowledge:
 * - استفاده از حساب خارجی کاملاً اختیاری است و هرگز جایگزین رمز اصلی یا کلید بازیابی نمی‌شود.
 * - امکان اتصال هم‌زمان چندین حساب Google و GitHub وجود دارد.
 * - قطع اتصال هر حساب هیچ تأثیری بر اطلاعات خزانهٔ محلی ندارد.
 * - هیچ رمز عبور، کلید خزانه یا عبارت بازیابی به سرویس‌های خارجی ارسال نمی‌شود.
 */
class ExternalAccountManager(context: Context) {
    private val store = KeystoreSecretStore(context, "solina_external_accounts")
    private val _accounts = MutableStateFlow(loadAccounts())
    val accounts: StateFlow<List<LinkedExternalAccount>> = _accounts.asStateFlow()

    @Volatile
    private var pending: OAuthPendingRequest? = null

    private fun loadAccounts(): List<LinkedExternalAccount> {
        val raw = store.getString(KEY_ACCOUNTS) ?: return emptyList()
        return LinkedExternalAccount.deserializeList(raw)
    }

    private fun persistAccounts(list: List<LinkedExternalAccount>) {
        store.putString(KEY_ACCOUNTS, LinkedExternalAccount.serializeList(list))
        _accounts.value = list
    }

    fun savedClientId(provider: OAuthProvider): String =
        store.getString("client_id_${provider.id}").orEmpty()

    fun saveClientId(provider: OAuthProvider, clientId: String) {
        val clean = clientId.trim()
        if (clean.isEmpty()) store.remove("client_id_${provider.id}")
        else store.putString("client_id_${provider.id}", clean)
    }

    /** شروع جریان استاندارد OAuth 2.0 + PKCE و برگرداندن نشانی مرورگر برای احراز هویت. */
    fun beginSignIn(provider: OAuthProvider, clientId: String): String {
        val cleanId = clientId.trim()
        saveClientId(provider, cleanId)
        val req = OAuthPkce.createPendingRequest(provider = provider, clientId = cleanId)
        pending = req
        DiagLog.log("OAUTH", "begin provider=${provider.id}")
        return OAuthPkce.buildAuthorizationUrl(req)
    }

    /** پردازش بازگشت مرورگر (`solina-auth://oauth/callback?code=...&state=...`). */
    suspend fun completeCallback(callbackUri: Uri): OAuthLinkResult = withContext(Dispatchers.IO) {
        val req = pending ?: return@withContext OAuthLinkResult.Failed("درخواست ورود فعالی وجود ندارد.")
        pending = null
        val parsed = OAuthPkce.parseCallbackQuery(callbackUri.encodedQuery, req.state)
        val code = when (parsed) {
            is OAuthCallbackResult.Error -> return@withContext OAuthLinkResult.Failed(parsed.error)
            is OAuthCallbackResult.Code -> parsed.code
        }
        try {
            val accessToken = exchangeCodeForToken(req, code)
                ?: return@withContext OAuthLinkResult.Failed("تبادل توکن OAuth با ${req.provider.displayName} ناموفق بود.")
            val account = fetchProfile(req.provider, accessToken)
                ?: return@withContext OAuthLinkResult.Failed("دریافت نمایهٔ کاربری از ${req.provider.displayName} ناموفق بود.")
            val updated = _accounts.value.filterNot {
                it.provider == account.provider && it.subject == account.subject
            } + account
            persistAccounts(updated)
            DiagLog.log("OAUTH", "linked provider=${req.provider.id}")
            OAuthLinkResult.Linked(account)
        } catch (e: Exception) {
            DiagLog.logError("OAUTH", e, "completeCallback")
            OAuthLinkResult.Failed("اتصال به ${req.provider.displayName} انجام نشد؛ اتصال اینترنت و Client ID را بررسی کنید.")
        }
    }

    /** قطع اتصال یک حساب خارجی (بدون حذف هیچ ورودی از خزانه). */
    fun disconnect(accountId: String) {
        persistAccounts(_accounts.value.filterNot { it.id == accountId })
    }

    /** قطع اتصال همهٔ حساب‌های خارجی (هنگام خروج کامل یا پاک‌سازی امن). */
    fun clearAll() {
        pending = null
        store.clearAll()
        _accounts.value = emptyList()
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    private fun exchangeCodeForToken(req: OAuthPendingRequest, code: String): String? {
        val body = listOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to req.redirectUri,
            "client_id" to req.clientId,
            "code_verifier" to req.codeVerifier,
        ).joinToString("&") { "${enc(it.first)}=${enc(it.second)}" }

        val conn = (URL(req.provider.tokenEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "Solina-Password-Manager")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(raw)
            json.optString("access_token").takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchProfile(provider: OAuthProvider, accessToken: String): LinkedExternalAccount? {
        val conn = (URL(provider.userInfoEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Solina-Password-Manager")
        }
        val raw = try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val json = JSONObject(raw)
        val now = System.currentTimeMillis()
        return when (provider) {
            OAuthProvider.Google -> {
                val sub = json.optString("sub").ifBlank { return null }
                val email = json.optString("email")
                val name = json.optString("name").ifBlank { email.substringBefore('@') }
                LinkedExternalAccount("google:$sub", provider, sub, email, name, now)
            }
            OAuthProvider.GitHub -> {
                val sub = json.optString("id").ifBlank { return null }
                val login = json.optString("login")
                val name = json.optString("name").ifBlank { login }
                val email = json.optString("email").takeIf { it.isNotBlank() && it != "null" }
                    ?: fetchGitHubPrimaryEmail(accessToken).orEmpty()
                LinkedExternalAccount("github:$sub", provider, sub, email, name, now)
            }
        }
    }

    private fun fetchGitHubPrimaryEmail(accessToken: String): String? = runCatching {
        val conn = (URL("https://api.github.com/user/emails").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Solina-Password-Manager")
        }
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            val arr = JSONArray(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                if (e.optBoolean("primary")) return@runCatching e.optString("email")
            }
            if (arr.length() > 0) arr.getJSONObject(0).optString("email") else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    companion object {
        private const val KEY_ACCOUNTS = "accounts_json"
    }
}
