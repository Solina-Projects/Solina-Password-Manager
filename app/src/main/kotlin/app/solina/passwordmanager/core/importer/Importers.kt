package app.solina.passwordmanager.core.importer

import app.solina.passwordmanager.core.Base32
import app.solina.passwordmanager.core.OtpType
import app.solina.passwordmanager.core.otp.OtpImport
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

open class ImportException(message: String) : Exception(message)

/** فایل رمزشده است و رمز لازم دارد؛ رابط باید رمز را بپرسد و دوباره تلاش کند. */
class ImportPasswordRequired(
    val source: String,
    val hint: String? = null,
) : ImportException("این فایل ($source) رمزشده است؛ رمز آن را وارد کنید.")

class ImportResult(val items: List<ItemPayload>, val source: String, val skipped: Int = 0)

/**
 * ایمپورت از خروجی برنامه‌های دیگر؛ شامل خروجی‌های بدون رمز و رمزشده:
 * Aegis (عادی و رمزشده، از جمله اسلات‌های بکاپ و issue #95)، 2FAS (v3/v4 عادی و رمزشده)،
 * Bitwarden (JSON عادی/رمزشده PBKDF2 و Argon2id، و CSV)، Proton Pass (ZIP/JSON/CSV)،
 * Proton Authenticator (عادی و رمزشده)، andOTP (عادی و رمزشده .aes)، Raivo، Ente Auth (متن ساده)،
 * Google Authenticator، CSV مرورگرها و مدیران رمز، و لینک‌های otpauth.
 */
object Importers {
    /** نشانهٔ ترتیب بایت UTF-8 که بعضی برنامه‌ها اول CSV یا JSON می‌گذارند. */
    private val BOM = 0xFEFF.toChar()

    private const val PROTON_AUTH_AAD = "proton.authenticator.export.v1"

    // ───── کمکی‌های امن برای org.json اندروید (جلوگیری از بازگشت رشتهٔ "null" به‌جای خالی) ─────

    private fun JSONObject.str(key: String, fallback: String = ""): String =
        if (isNull(key)) fallback else optString(key, fallback)

    private fun JSONObject.intOr(key: String, fallback: Int): Int =
        if (isNull(key)) fallback else optInt(key, fallback)

    private fun JSONObject.longOr(key: String, fallback: Long): Long =
        if (isNull(key)) fallback else optLong(key, fallback)

    private fun JSONArray.str(index: Int): String =
        if (isNull(index)) "" else optString(index, "")

    /**
     * بایت‌های فایل را می‌خواند:
     * - ZIP (مثل خروجی Proton Pass یا Raivo) باز می‌شود و ZIP رمزشده یا PGP با پیام روشن توضیح داده می‌شود.
     * - فایل‌های باینری رمزشدهٔ andOTP (‎.aes) رمزگشایی می‌شوند.
     * - بقیه به‌عنوان متن UTF-8 (JSON / CSV / otpauth) پردازش می‌شوند.
     */
    fun parseBytes(bytes: ByteArray, password: CharArray? = null, fileName: String? = null): ImportResult {
        if (bytes.isEmpty()) throw ImportException("فایل خالی است.")
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2].toInt() == 3 && bytes[3].toInt() == 4) {
            return parse(unzipFirstText(bytes), password)
        }
        val isAesName = fileName?.endsWith(".aes", ignoreCase = true) == true
        val text = String(bytes, Charsets.UTF_8)
        val trimmedStart = text.removePrefix(BOM.toString()).trimStart()
        if (trimmedStart.startsWith("-----BEGIN PGP MESSAGE-----")) throw pgpUnsupported()
        // فایل رمزشدهٔ andOTP (.aes) باینری خام است و قالب JSON/متنی ندارد
        if (isAesName && !trimmedStart.startsWith("{") && !trimmedStart.startsWith("[")) {
            return andOtpDecrypt(bytes, password)
        }
        return parse(text, password)
    }

    private fun pgpUnsupported() = ImportException(
        "این فایل با PGP رمز شده (خروجی رمزشدهٔ Proton Pass) و هنوز پشتیبانی نمی‌شود. در Proton Pass خروجی «بدون رمز (ZIP)» یا CSV بگیرید.",
    )

    private fun unzipFirstText(bytes: ByteArray): String {
        val limit = 20 * 1024 * 1024
        var bestText: String? = null
        var bestRank = Int.MAX_VALUE
        var sawPgp = false
        try {
            ZipInputStream(bytes.inputStream()).use { zin ->
                var entry = zin.nextEntry
                var count = 0
                while (entry != null && count++ < 100) {
                    val rawName = entry.name
                    val name = rawName.lowercase()
                    val base = name.substringAfterLast('/')
                    val skipMeta = entry.isDirectory || name.startsWith("__macosx/") || base.startsWith(".")
                    if (!skipMeta) {
                        val rank = when {
                            name.endsWith(".json") -> 0
                            name.endsWith(".csv") -> 1
                            name.endsWith(".txt") -> 2
                            else -> Int.MAX_VALUE
                        }
                        if (rank < bestRank) {
                            val out = ByteArrayOutputStream()
                            val buf = ByteArray(8192)
                            while (true) {
                                val n = zin.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                if (out.size() > limit) throw ImportException("فایل داخل ZIP بیش از حد بزرگ است.")
                            }
                            bestText = String(out.toByteArray(), Charsets.UTF_8)
                            bestRank = rank
                            if (bestRank == 0) break
                        } else if (name.endsWith(".pgp")) {
                            sawPgp = true
                        }
                    }
                    entry = zin.nextEntry
                }
            }
        } catch (e: ZipException) {
            throw ImportException("این فایل ZIP رمزگذاری شده یا خراب است؛ ابتدا فایل JSON یا CSV داخل آن را استخراج کنید و سپس وارد نمایید.")
        }
        if (bestText != null) return bestText!!
        if (sawPgp) throw pgpUnsupported()
        throw ImportException("داخل ZIP فایل JSON یا CSV پیدا نشد.")
    }

    fun parse(textIn: String, password: CharArray? = null): ImportResult {
        val text = textIn.removePrefix(BOM.toString()).trim()
        if (text.isEmpty()) throw ImportException("فایل خالی است.")
        return when {
            text.startsWith("{") -> parseJson(text, password)
            text.startsWith("[") -> parseJsonArray(text)
            text.lineSequence().any { it.trim().startsWith("otpauth", ignoreCase = true) } ->
                ImportResult(OtpImport.parse(text) ?: throw ImportException("کد TOTP معتبری در فایل نبود."), "otpauth")
            else -> parseCsv(text)
        }
    }

    // ───── JSON ─────

    /** عمق تودرتویی JSON را بدون بازگشت می‌شمارد؛ org.json بازگشتی است و ورودی بسیار عمیق StackOverflow می‌دهد. */
    private fun jsonTooDeep(text: String, limit: Int = 64): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (ch in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
            } else when (ch) {
                '"' -> inString = true
                '{', '[' -> if (++depth > limit) return true
                '}', ']' -> depth--
            }
        }
        return false
    }

    private fun parseJson(text: String, password: CharArray?): ImportResult {
        if (jsonTooDeep(text)) throw ImportException("ساختار JSON بیش از حد تودرتو است.")
        val o = try { JSONObject(text) } catch (e: JSONException) { throw ImportException("فایل JSON خراب است.") }
        return try {
            when {
                (o.has("items") && (o.has("folders") || o.has("encrypted"))) || (o.optBoolean("encrypted") && o.has("data")) -> bitwarden(o, password)
                o.has("db") && o.has("header") -> aegis(o, password)
                o.has("services") || o.has("servicesEncrypted") -> twoFas(o, password)
                o.has("vaults") -> protonPass(o)
                o.has("salt") && o.has("content") && o.has("version") -> protonAuthenticator(o, password)
                o.has("kdfParams") && o.has("encryptedData") ->
                    throw ImportException("خروجی رمزشدهٔ Ente Auth هنوز پشتیبانی نمی‌شود. در Ente Auth از «Export» خروجی متنی ساده (otpauth) بگیرید؛ آن پشتیبانی می‌شود.")
                else -> collectOtpauth(o)
            }
        } catch (e: JSONException) {
            throw ImportException("ساختار فایل با قالب انتظار‌رفته نمی‌خواند.")
        } ?: throw ImportException("قالب این فایل شناخته نشد.")
    }

    private fun parseJsonArray(text: String): ImportResult {
        if (jsonTooDeep(text)) throw ImportException("ساختار JSON بیش از حد تودرتو است.")
        val a = try { JSONArray(text) } catch (e: JSONException) { throw ImportException("فایل JSON خراب است.") }
        val first = a.optJSONObject(0)
        return try {
            when {
                first != null && first.has("label") && first.has("secret") -> andOtp(a)
                first != null && first.has("kind") && first.has("secret") -> raivo(a)
                else -> collectOtpauth(a)
            }
        } catch (e: JSONException) {
            throw ImportException("ساختار فایل با قالب انتظار‌رفته نمی‌خواند.")
        } ?: throw ImportException("قالب این فایل شناخته نشد.")
    }

    private val ALGOS = setOf("SHA1", "SHA256", "SHA512")

    private fun otpLogin(
        issuer: String,
        account: String,
        secretRaw: String,
        algoRaw: String,
        digits: Int,
        period: Int,
        note: String = "",
        fav: Boolean = false,
        otpType: OtpType = OtpType.TOTP,
        counter: Long = 0L,
        category: String = "",
    ): ItemPayload? {
        val secret = secretRaw.replace(" ", "").replace("-", "").trimEnd('=').uppercase()
        val algo = algoRaw.uppercase().replace("-", "").ifEmpty { "SHA1" }
        val effectivePeriod = if (otpType == OtpType.HOTP) {
            if (period in 1..300) period else 30
        } else {
            period
        }
        if (Base32.decode(secret) == null || algo !in ALGOS || digits !in 6..8 || effectivePeriod !in 1..300 || counter < 0L) return null
        return ItemPayload(
            ItemKind.Login,
            issuer.ifEmpty { account },
            if (issuer.isNotEmpty()) account else "",
            note = note,
            totpSecret = secret,
            totpDigits = digits,
            totpPeriod = effectivePeriod,
            totpAlgorithm = algo,
            favorite = fav,
            otpType = otpType,
            totpCounter = if (otpType == OtpType.HOTP) counter else 0L,
            category = category,
        )
    }

    // ───── andOTP (عادی و رمزشده .aes) ─────

    private fun andOtp(a: JSONArray): ImportResult {
        val out = ArrayList<ItemPayload>()
        var skipped = 0
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: run { skipped++; continue }
            val typeStr = e.str("type", "TOTP").uppercase()
            val otpType = when (typeStr) {
                "TOTP" -> OtpType.TOTP
                "HOTP" -> OtpType.HOTP
                else -> null
            }
            var issuer = e.str("issuer")
            var account = e.str("label")
            if (issuer.isEmpty() && account.contains(" - ")) {
                issuer = account.substringBefore(" - ").trim()
                account = account.substringAfter(" - ").trim()
            }
            val firstTag = e.optJSONArray("tags")?.let { tags ->
                if (tags.length() > 0) tags.str(0) else ""
            }.orEmpty()
            val p = if (otpType != null) {
                otpLogin(
                    issuer,
                    account,
                    e.str("secret"),
                    e.str("algorithm", "SHA1"),
                    e.intOr("digits", 6),
                    e.intOr("period", 30),
                    otpType = otpType,
                    counter = e.longOr("counter", 0L),
                    category = firstTag,
                )
            } else null
            if (p == null) skipped++ else out += p
        }
        if (out.isEmpty()) throw ImportException("هیچ کد OTP قابل‌استفاده‌ای در فایل نبود.")
        return ImportResult(out, "andOTP", skipped)
    }

    private fun andOtpDecrypt(bytes: ByteArray, password: CharArray?): ImportResult {
        if (bytes.size < 29) throw ImportException("فایل رمزشدهٔ andOTP ناقص یا خراب است.")
        if (password == null) throw ImportPasswordRequired("andOTP")
        val candidates = ForeignCrypto.passwordCandidates(password)
        try {
            var plain: ByteArray? = null
            // ۱) قالب جدید andOTP: [4B iterations][12B salt][12B nonce][ct + 16B tag] با PBKDF2WithHmacSHA1
            if (bytes.size >= 44) {
                val iterations = ByteBuffer.wrap(bytes, 0, 4).int
                if (iterations in 1..10_000_000) {
                    val salt = bytes.copyOfRange(4, 16)
                    val nonce = bytes.copyOfRange(16, 28)
                    val ctAndTag = bytes.copyOfRange(28, bytes.size)
                    for (cand in candidates) {
                        val key = ForeignCrypto.pbkdf2Sha1(cand, salt, iterations, 32)
                        try {
                            plain = ForeignCrypto.aesGcmDecrypt(key, nonce, ctAndTag)
                            break
                        } catch (e: WrongImportPasswordException) {
                            // نامزد بعدی یا قالب قدیمی
                        } finally {
                            key.fill(0)
                        }
                    }
                }
            }
            // ۲) قالب قدیمی andOTP: [12B nonce][ct + 16B tag] با کلید SHA-256(password)
            if (plain == null) {
                val nonce = bytes.copyOfRange(0, 12)
                val ctAndTag = bytes.copyOfRange(12, bytes.size)
                for (cand in candidates) {
                    val pwBytes = ForeignCrypto.toUtf8Bytes(cand)
                    val key = try { ForeignCrypto.sha256(pwBytes) } finally { pwBytes.fill(0) }
                    try {
                        plain = ForeignCrypto.aesGcmDecrypt(key, nonce, ctAndTag)
                        break
                    } catch (e: WrongImportPasswordException) {
                        // نامزد بعدی
                    } finally {
                        key.fill(0)
                    }
                }
            }
            if (plain == null) throw WrongImportPasswordException()
            val text = String(plain, Charsets.UTF_8).removePrefix(BOM.toString()).trim()
            if (jsonTooDeep(text)) throw ImportException("ساختار JSON بیش از حد تودرتو است.")
            val arr = try { JSONArray(text) } catch (e: JSONException) {
                throw ImportException("رمز درست بود، اما محتوای فایل andOTP خراب است.")
            }
            return andOtp(arr)
        } finally {
            candidates.forEach { it.fill('\u0000') }
        }
    }

    // ───── Raivo OTP ─────

    private fun raivo(a: JSONArray): ImportResult {
        val out = ArrayList<ItemPayload>()
        var skipped = 0
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: run { skipped++; continue }
            val kind = e.str("kind", "TOTP").uppercase()
            val otpType = when (kind) {
                "TOTP" -> OtpType.TOTP
                "HOTP" -> OtpType.HOTP
                else -> null
            }
            val digits = e.str("digits", "6").toIntOrNull() ?: e.intOr("digits", 6)
            val timer = e.str("timer", "30").toIntOrNull() ?: e.intOr("timer", 30)
            val counter = e.str("counter", "0").toLongOrNull() ?: e.longOr("counter", 0L)
            val p = if (otpType != null) {
                otpLogin(
                    e.str("issuer"),
                    e.str("account"),
                    e.str("secret"),
                    e.str("algorithm", "SHA1"),
                    digits,
                    timer,
                    otpType = otpType,
                    counter = counter,
                )
            } else null
            if (p == null) skipped++ else out += p
        }
        if (out.isEmpty()) throw ImportException("هیچ کد OTP قابل‌استفاده‌ای در فایل نبود.")
        return ImportResult(out, "Raivo", skipped)
    }

    // ───── Proton Authenticator (رمزشده و ساده) ─────

    private fun protonAuthenticator(o: JSONObject, password: CharArray?): ImportResult {
        val version = o.intOr("version", -1)
        if (version != 1) throw ImportException("نسخهٔ این فایل Proton Authenticator پشتیبانی نمی‌شود.")
        if (password == null) throw ImportPasswordRequired("Proton Authenticator")
        val salt = try { ForeignCrypto.b64(o.str("salt")) } catch (e: IllegalArgumentException) {
            throw ImportException("ساختار فایل Proton Authenticator خراب است.")
        }
        val content = try { ForeignCrypto.b64(o.str("content")) } catch (e: IllegalArgumentException) {
            throw ImportException("ساختار فایل Proton Authenticator خراب است.")
        }
        if (content.size < 12 + 16) throw ImportException("ساختار فایل Proton Authenticator خراب است.")
        val nonce = content.copyOfRange(0, 12)
        val ctAndTag = content.copyOfRange(12, content.size)
        val aad = PROTON_AUTH_AAD.toByteArray(Charsets.UTF_8)
        val candidates = ForeignCrypto.passwordCandidates(password)
        try {
            var plain: ByteArray? = null
            for (cand in candidates) {
                val key = ForeignCrypto.argon2id(cand, salt, iterations = 2, memoryKiB = 19 * 1024, parallelism = 1, keyBytes = 32)
                try {
                    plain = ForeignCrypto.aesGcmDecrypt(key, nonce, ctAndTag, aad)
                    break
                } catch (e: WrongImportPasswordException) {
                    // نامزد بعدی
                } finally {
                    key.fill(0)
                }
            }
            if (plain == null) throw WrongImportPasswordException()
            val text = String(plain, Charsets.UTF_8).removePrefix(BOM.toString()).trim()
            if (jsonTooDeep(text)) throw ImportException("ساختار JSON بیش از حد تودرتو است.")
            val root = try { JSONObject(text) } catch (e: JSONException) {
                throw ImportException("رمز درست بود، اما بدنهٔ فایل Proton Authenticator خراب است.")
            }
            val entries = root.optJSONArray("entries")
            if (entries != null) {
                val out = ArrayList<ItemPayload>()
                var skipped = 0
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    val contentObj = entry.optJSONObject("content") ?: continue
                    val uri = contentObj.str("uri")
                    val name = contentObj.str("name")
                    val parsed = if (uri.startsWith("otpauth://", ignoreCase = true)) OtpImport.parseUri(uri) else null
                    if (parsed == null) {
                        skipped++
                    } else {
                        out += if (name.isNotEmpty() && parsed.title.isEmpty()) parsed.copy(title = name) else parsed
                    }
                }
                if (out.isEmpty()) throw ImportException("هیچ کد OTP قابل‌استفاده‌ای در فایل Proton Authenticator نبود.")
                return ImportResult(out, "Proton Authenticator", skipped)
            }
            val fallback = collectOtpauth(root) ?: throw ImportException("هیچ کد OTP قابل‌استفاده‌ای در فایل Proton Authenticator نبود.")
            return ImportResult(fallback.items, "Proton Authenticator", fallback.skipped)
        } finally {
            candidates.forEach { it.fill('\u0000') }
        }
    }

    /** پشتیبان آخر برای JSON ناشناخته: هر رشتهٔ otpauth:// در هر جای فایل (مثل Proton Authenticator ساده و Ente) خوانده می‌شود. */
    private fun collectOtpauth(root: Any): ImportResult? {
        val uris = ArrayList<String>()
        val stack = ArrayDeque<Any>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < 200_000) {
            when (val n = stack.removeLast()) {
                is JSONObject -> for (k in n.keys()) n.opt(k)?.let { stack.add(it) }
                is JSONArray -> for (i in 0 until n.length()) n.opt(i)?.let { stack.add(it) }
                is String -> if (n.startsWith("otpauth://", ignoreCase = true) || n.startsWith("otpauth-migration://", ignoreCase = true)) uris += n
            }
        }
        if (uris.isEmpty()) return null
        val items = OtpImport.parse(uris.joinToString("\n")) ?: throw ImportException("کد TOTP معتبری در فایل نبود.")
        return ImportResult(items, "otpauth (JSON)", (uris.size - items.size).coerceAtLeast(0))
    }

    // ───── 2FAS (v3/v4 عادی و رمزشده) ─────

    private fun twoFas(o: JSONObject, password: CharArray?): ImportResult {
        val enc = o.str("servicesEncrypted")
        // در خروجی‌های رمزشدهٔ 2FAS (v3 و v4)، فیلد "services" معمولاً آرایهٔ خالی [] است و داده‌ها داخل "servicesEncrypted" قرار دارند.
        val arr: JSONArray = if (enc.isNotEmpty()) {
            if (password == null) throw ImportPasswordRequired("2FAS")
            val parts = enc.split(":")
            if (parts.size < 3) throw ImportException("ساختار فایل 2FAS خراب است.")
            val ct: ByteArray
            val salt: ByteArray
            val iv: ByteArray
            try {
                ct = ForeignCrypto.b64(parts[0])
                salt = ForeignCrypto.b64(parts[1])
                iv = ForeignCrypto.b64(parts[2])
            } catch (e: IllegalArgumentException) {
                throw ImportException("ساختار فایل 2FAS خراب است.")
            }
            val candidates = ForeignCrypto.passwordCandidates(password)
            val plain = try {
                var decrypted: ByteArray? = null
                for (cand in candidates) {
                    val key = ForeignCrypto.pbkdf2Sha256(cand, salt, 10_000)
                    try {
                        decrypted = ForeignCrypto.aesGcmDecrypt(key, iv, ct)
                        break
                    } catch (e: WrongImportPasswordException) {
                        // نامزد بعدی
                    } finally {
                        key.fill(0)
                    }
                }
                decrypted ?: throw WrongImportPasswordException()
            } finally {
                candidates.forEach { it.fill('\u0000') }
            }
            try {
                JSONArray(String(plain, Charsets.UTF_8).removePrefix(BOM.toString()).trim())
            } catch (e: JSONException) {
                throw ImportException("رمز درست بود، اما محتوای فایل 2FAS خراب است.")
            }
        } else {
            o.optJSONArray("services") ?: throw ImportException("ساختار فایل 2FAS خراب است.")
        }

        val groupNames = HashMap<String, String>()
        o.optJSONArray("groups")?.let { gs ->
            for (i in 0 until gs.length()) {
                val g = gs.optJSONObject(i) ?: continue
                val id = g.str("id")
                val gname = g.str("name")
                if (id.isNotEmpty() && gname.isNotEmpty()) groupNames[id] = gname
            }
        }

        val out = ArrayList<ItemPayload>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: run { skipped++; continue }
            val otp = e.optJSONObject("otp")
            val type = otp?.str("tokenType", "TOTP")?.ifEmpty { "TOTP" } ?: "TOTP"
            val otpType = when (type.uppercase()) {
                "TOTP" -> OtpType.TOTP
                "HOTP" -> OtpType.HOTP
                else -> null
            }
            val issuer = otp?.str("issuer").orEmpty().ifEmpty { e.str("name") }
            val account = otp?.str("account").orEmpty()
            val groupId = e.str("groupId")
            val category = if (groupId.isNotEmpty()) groupNames[groupId].orEmpty() else ""
            val p = if (otpType != null) {
                otpLogin(
                    issuer,
                    account,
                    e.str("secret"),
                    otp?.str("algorithm", "SHA1").orEmpty(),
                    otp?.intOr("digits", 6) ?: 6,
                    otp?.intOr("period", 30) ?: 30,
                    otpType = otpType,
                    counter = otp?.longOr("counter", 0L) ?: 0L,
                    category = category,
                )
            } else null
            if (p == null) skipped++ else out += p
        }
        if (out.isEmpty()) throw ImportException("هیچ کد OTP قابل‌استفاده‌ای در فایل 2FAS نبود.")
        return ImportResult(out, "2FAS", skipped)
    }

    // ───── Proton Pass (خروجی بدون رمز ZIP/JSON) ─────

    private fun protonPass(o: JSONObject): ImportResult {
        if (o.optBoolean("encrypted")) throw pgpUnsupported()
        val vaults = o.getJSONObject("vaults")
        val out = ArrayList<ItemPayload>()
        var skipped = 0
        for (vk in vaults.keys()) {
            val vault = vaults.optJSONObject(vk) ?: continue
            val vaultName = vault.str("name")
            val items = vault.optJSONArray("items") ?: continue
            for (i in 0 until items.length()) {
                val data = items.optJSONObject(i)?.optJSONObject("data")
                if (data == null) { skipped++; continue }
                val meta = data.optJSONObject("metadata")
                val name = meta?.str("name").orEmpty()
                val note = meta?.str("note").orEmpty()
                val c = data.optJSONObject("content")
                when (data.str("type")) {
                    "login" -> {
                        val user = c?.str("itemUsername").orEmpty().ifEmpty { c?.str("itemEmail").orEmpty() }
                        val t = totpFields(c?.str("totpUri").orEmpty())
                        out += ItemPayload(
                            ItemKind.Login,
                            name,
                            user,
                            c?.str("password").orEmpty(),
                            c?.optJSONArray("urls")?.let { u -> if (u.length() > 0) u.str(0) else "" }.orEmpty(),
                            note,
                            t?.totpSecret.orEmpty(),
                            totpDigits = t?.totpDigits ?: 6,
                            totpPeriod = t?.totpPeriod ?: 30,
                            totpAlgorithm = t?.totpAlgorithm ?: "SHA1",
                            otpType = t?.otpType ?: OtpType.TOTP,
                            totpCounter = t?.totpCounter ?: 0L,
                            category = vaultName,
                        )
                    }
                    "note" -> out += ItemPayload(ItemKind.Note, name, note = note, category = vaultName)
                    "creditCard" -> {
                        val exp = c?.str("expirationDate").orEmpty() // YYYY-MM
                        val mmYY = if (exp.length >= 7) exp.substring(5, 7) + "/" + exp.substring(2, 4) else exp
                        out += ItemPayload(
                            ItemKind.Card,
                            name,
                            note = note,
                            cardHolder = c?.str("cardholderName").orEmpty(),
                            cardNumber = c?.str("number").orEmpty(),
                            cardExpiry = mmYY,
                            cardCvv = c?.str("verificationNumber").orEmpty(),
                            cardPin = c?.str("pin").orEmpty(),
                            category = vaultName,
                        )
                    }
                    "identity" -> {
                        val fullName = c?.str("fullName").orEmpty().ifEmpty {
                            listOf(c?.str("firstName").orEmpty(), c?.str("middleName").orEmpty(), c?.str("lastName").orEmpty())
                                .filter { it.isNotEmpty() }.joinToString(" ")
                        }
                        out += ItemPayload(
                            ItemKind.Identity,
                            name.ifEmpty { fullName }.ifEmpty { "هویت" },
                            note = note,
                            fullName = fullName,
                            email = c?.str("email").orEmpty(),
                            phone = c?.str("phoneNumber").orEmpty(),
                            nationalId = c?.str("socialSecurityNumber").orEmpty().ifEmpty { c?.str("passportNumber").orEmpty() },
                            address = c?.str("streetAddress").orEmpty(),
                            city = c?.str("city").orEmpty(),
                            postalCode = c?.str("zipOrPostalCode").orEmpty(),
                            country = c?.str("countryOrRegion").orEmpty(),
                            company = c?.str("company").orEmpty(),
                            category = vaultName,
                        )
                    }
                    else -> skipped++
                }
            }
        }
        if (out.isEmpty()) throw ImportException("هیچ ورودی قابل‌استفاده‌ای در فایل Proton نبود.")
        return ImportResult(out, "Proton Pass", skipped)
    }

    private fun totpFields(raw: String): ItemPayload? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        if (v.startsWith("otpauth://", ignoreCase = true)) return OtpImport.parseUri(v)
        val s = v.replace(" ", "").replace("-", "").trimEnd('=').uppercase()
        return if (Base32.decode(s) != null) ItemPayload(totpSecret = s) else null
    }

    // ───── Bitwarden (عادی و رمزشده PBKDF2 / Argon2id) ─────

    private fun bitwardenDecrypt(o: JSONObject, password: CharArray?): JSONObject {
        if (!o.optBoolean("passwordProtected")) {
            throw ImportException("این خروجی Bitwarden با کلید حساب رمز شده و بیرون از Bitwarden قابل‌خواندن نیست. در Bitwarden خروجی «.json (رمزشده با رمز فایل)» یا «بدون رمز» بگیرید.")
        }
        if (password == null) throw ImportPasswordRequired("Bitwarden")
        val salt = o.str("salt")
        if (salt.isEmpty()) throw ImportException("ساختار فایل Bitwarden خراب است.")
        val kdfType = o.intOr("kdfType", 0)
        val iterations = o.intOr("kdfIterations", 600_000).also {
            if (it !in 1..10_000_000) throw ImportException("پارامتر KDF فایل نامعتبر است.")
        }
        val mem = o.intOr("kdfMemory", 64)
        val par = o.intOr("kdfParallelism", 4)
        if (kdfType == 1 && (iterations !in 1..64 || mem !in 1..1024 || par !in 1..16)) {
            throw ImportException("پارامتر KDF فایل نامعتبر است.")
        }
        if (kdfType != 0 && kdfType != 1) throw ImportException("نوع KDF این فایل Bitwarden پشتیبانی نمی‌شود.")

        val validation = o.str("encKeyValidation_DO_NOT_EDIT")
        val encData = o.str("data")
        if (encData.isEmpty()) throw ImportException("ساختار فایل Bitwarden خراب است.")

        val candidates = ForeignCrypto.passwordCandidates(password)
        try {
            var plain: ByteArray? = null
            for (cand in candidates) {
                val master = when (kdfType) {
                    0 -> ForeignCrypto.pbkdf2Sha256(cand, salt.toByteArray(Charsets.UTF_8), iterations)
                    else -> ForeignCrypto.bitwardenArgon2(cand, salt, iterations, mem, par)
                }
                val encKey = ForeignCrypto.hkdfExpand32(master, "enc")
                val macKey = ForeignCrypto.hkdfExpand32(master, "mac")
                try {
                    if (validation.isNotEmpty()) ForeignCrypto.bitwardenDecrypt(validation, encKey, macKey)
                    plain = ForeignCrypto.bitwardenDecrypt(encData, encKey, macKey)
                    break
                } catch (e: WrongImportPasswordException) {
                    // نامزد بعدی
                } finally {
                    master.fill(0)
                    encKey.fill(0)
                    macKey.fill(0)
                }
            }
            if (plain == null) throw WrongImportPasswordException()
            val text = String(plain, Charsets.UTF_8).removePrefix(BOM.toString()).trim()
            if (jsonTooDeep(text)) throw ImportException("ساختار JSON بیش از حد تودرتو است.")
            return try { JSONObject(text) } catch (e: JSONException) {
                throw ImportException("رمز درست بود، اما محتوای فایل Bitwarden خراب است.")
            }
        } finally {
            candidates.forEach { it.fill('\u0000') }
        }
    }

    private fun bitwarden(oIn: JSONObject, password: CharArray?): ImportResult {
        val o = if (oIn.optBoolean("encrypted")) bitwardenDecrypt(oIn, password) else oIn
        val folderNames = HashMap<String, String>()
        o.optJSONArray("folders")?.let { fs ->
            for (i in 0 until fs.length()) {
                val f = fs.optJSONObject(i) ?: continue
                val id = f.str("id")
                val fname = f.str("name")
                if (id.isNotEmpty() && fname.isNotEmpty()) folderNames[id] = fname
            }
        }
        val arr = o.getJSONArray("items")
        val out = ArrayList<ItemPayload>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val it = arr.optJSONObject(i) ?: run { skipped++; continue }
            val name = it.str("name")
            val notes = it.str("notes")
            val fav = it.optBoolean("favorite")
            val folderId = it.str("folderId")
            val category = if (folderId.isNotEmpty()) folderNames[folderId].orEmpty() else ""
            when (it.intOr("type", 0)) {
                1 -> {
                    val l = it.optJSONObject("login")
                    val uri = l?.optJSONArray("uris")?.optJSONObject(0)?.str("uri").orEmpty()
                    val t = totpFields(l?.str("totp").orEmpty())
                    out += ItemPayload(
                        ItemKind.Login,
                        name,
                        l?.str("username").orEmpty(),
                        l?.str("password").orEmpty(),
                        uri,
                        notes,
                        t?.totpSecret.orEmpty(),
                        fav,
                        totpDigits = t?.totpDigits ?: 6,
                        totpPeriod = t?.totpPeriod ?: 30,
                        totpAlgorithm = t?.totpAlgorithm ?: "SHA1",
                        otpType = t?.otpType ?: OtpType.TOTP,
                        totpCounter = t?.totpCounter ?: 0L,
                        category = category,
                    )
                }
                2 -> out += ItemPayload(ItemKind.Note, name, note = notes, favorite = fav, category = category)
                3 -> {
                    val c = it.optJSONObject("card")
                    val expMonth = c?.str("expMonth").orEmpty()
                    val expYear = c?.str("expYear").orEmpty()
                    val mm = expMonth.padStart(2, '0')
                    val yy = expYear.takeLast(2)
                    out += ItemPayload(
                        ItemKind.Card,
                        name,
                        note = notes,
                        favorite = fav,
                        cardHolder = c?.str("cardholderName").orEmpty(),
                        cardNumber = c?.str("number").orEmpty(),
                        cardExpiry = if (expMonth.isEmpty()) "" else "$mm/$yy",
                        cardCvv = c?.str("code").orEmpty(),
                        bankName = c?.str("brand").orEmpty(),
                        category = category,
                    )
                }
                4 -> {
                    val id = it.optJSONObject("identity")
                    if (id == null) {
                        skipped++
                    } else {
                        val fullName = listOf(id.str("firstName"), id.str("middleName"), id.str("lastName"))
                            .filter { s -> s.isNotEmpty() }.joinToString(" ")
                        val address = listOf(id.str("address1"), id.str("address2"), id.str("address3"))
                            .filter { s -> s.isNotEmpty() }.joinToString("، ")
                        val nationalId = id.str("ssn").ifEmpty { id.str("passportNumber") }.ifEmpty { id.str("licenseNumber") }
                        out += ItemPayload(
                            ItemKind.Identity,
                            name.ifEmpty { fullName }.ifEmpty { "هویت" },
                            note = notes,
                            favorite = fav,
                            fullName = fullName,
                            email = id.str("email"),
                            phone = id.str("phone"),
                            nationalId = nationalId,
                            address = address,
                            city = id.str("city"),
                            postalCode = id.str("postalCode"),
                            country = id.str("country"),
                            company = id.str("company"),
                            category = category,
                        )
                    }
                }
                else -> skipped++
            }
        }
        return ImportResult(out, "Bitwarden", skipped)
    }

    // ───── Aegis Authenticator (v1/v2/v3 عادی و رمزشده، اسلات بکاپ و issue #95) ─────

    private data class AegisSlot(
        val n: Int,
        val r: Int,
        val p: Int,
        val salt: ByteArray,
        val key: ByteArray,
        val nonce: ByteArray,
        val tag: ByteArray,
        val repaired: Boolean,
        val isBackup: Boolean,
    )

    private fun aegisDecrypt(o: JSONObject, header: JSONObject, password: CharArray?): JSONObject {
        val slotsArr = header.optJSONArray("slots")
            ?: throw ImportException("ساختار هدر فایل Aegis ناقص است (فیلد slots وجود ندارد).")

        val rawPasswordSlots = ArrayList<JSONObject>()
        var hasBiometricSlot = false
        for (i in 0 until slotsArr.length()) {
            val sl = slotsArr.optJSONObject(i) ?: continue
            when (sl.intOr("type", -1)) {
                1 -> rawPasswordSlots += sl
                2 -> hasBiometricSlot = true
            }
        }

        if (rawPasswordSlots.isEmpty()) {
            if (hasBiometricSlot) {
                throw ImportException(
                    "این فایل Aegis فقط با اثرانگشت (Biometric) رمزگذاری شده و اسلات رمز عبور ندارد. در Aegis از خروجی رمزشده با رمز عبور یا خروجی بدون رمز استفاده کنید.",
                )
            }
            throw ImportException("این فایل Aegis هیچ اسلات رمز عبوری (نوع ۱) ندارد.")
        }

        if (password == null) {
            val backupOnly = rawPasswordSlots.all { it.optBoolean("is_backup", false) }
            val hint = if (backupOnly) {
                "این بکاپ Aegis با «رمز بکاپ» (Backup Password) رمزگذاری شده است؛ اگر در تنظیمات Aegis برای بکاپ رمز جداگانه‌ای گذاشته‌اید، همان رمز بکاپ را وارد کنید."
            } else {
                null
            }
            throw ImportPasswordRequired("Aegis", hint)
        }

        val parsedSlots = rawPasswordSlots.map { sl ->
            val n = sl.getInt("n")
            val r = sl.getInt("r")
            val p = sl.getInt("p")
            // سقف پارامترها برای جلوگیری از فایل مخرب که حافظهٔ زیادی بخواهد
            if (n !in 2..(1 shl 20) || n and (n - 1) != 0 || r !in 1..32 || p !in 1..16 || 128L * n * r > 512L * 1024 * 1024) {
                throw ImportException("پارامترهای scrypt فایل Aegis نامعتبر است.")
            }
            val kp = sl.getJSONObject("key_params")
            try {
                AegisSlot(
                    n = n,
                    r = r,
                    p = p,
                    salt = ForeignCrypto.hex(sl.str("salt")),
                    key = ForeignCrypto.hex(sl.str("key")),
                    nonce = ForeignCrypto.hex(kp.str("nonce")),
                    tag = ForeignCrypto.hex(kp.str("tag")),
                    repaired = sl.optBoolean("repaired", false),
                    isBackup = sl.optBoolean("is_backup", false),
                )
            } catch (e: IllegalArgumentException) {
                throw ImportException("ساختار کلید در فایل Aegis خراب است.")
            }
        }

        val candidates = ForeignCrypto.passwordCandidates(password)
        var master: ByteArray? = null
        try {
            // مرحله ۱: امتحان هر نامزد رمز (ابتدا دقیقاً همان رمز واردشده) روی همهٔ اسلات‌های رمز
            outer@ for (cand in candidates) {
                for (sl in parsedSlots) {
                    val wrapper = ForeignCrypto.scrypt(cand, sl.salt, sl.n, sl.r, sl.p)
                    try {
                        master = ForeignCrypto.aesGcmDecrypt(wrapper, sl.nonce, sl.key + sl.tag)
                        break@outer
                    } catch (e: WrongImportPasswordException) {
                        // اسلات بعدی یا بررسی باگ قدیمی issue #95
                    } finally {
                        wrapper.fill(0)
                    }

                    // مرحله ۲ (سازگاری با Aegis issue #95): در اسلات‌های ترمیم‌نشده (repaired=false)،
                    // رمزهای بلندتر با ByteBuffer.array() دارای بایت‌های صفرِ انتهایی مشتق شده‌اند.
                    if (!sl.repaired) {
                        val legacyList = ForeignCrypto.aegisLegacyPasswordBytes(cand)
                        try {
                            for (legacyBytes in legacyList) {
                                val oldWrapper = ForeignCrypto.scryptBytes(legacyBytes, sl.salt, sl.n, sl.r, sl.p)
                                try {
                                    master = ForeignCrypto.aesGcmDecrypt(oldWrapper, sl.nonce, sl.key + sl.tag)
                                    break@outer
                                } catch (e: WrongImportPasswordException) {
                                    // تلاش بعدی
                                } finally {
                                    oldWrapper.fill(0)
                                }
                            }
                        } finally {
                            legacyList.forEach { it.fill(0) }
                        }
                    }
                }
            }
        } finally {
            candidates.forEach { it.fill('\u0000') }
        }

        if (master == null) throw WrongImportPasswordException()

        // از اینجا به بعد کلید اصلی با برچسب GCM اسلات احراز شده است، پس رمز قطعاً درست بوده است؛
        // اگر بدنهٔ db خراب باشد پیام دقیق خرابی فایل می‌دهیم نه پیام «رمز اشتباه».
        try {
            val params = header.optJSONObject("params")
                ?: throw ImportException("رمز درست بود، اما پارامترهای رمزگشایی بدنه در فایل Aegis وجود ندارد.")
            val dbNonce: ByteArray
            val dbCt: ByteArray
            val dbTag: ByteArray
            try {
                dbNonce = ForeignCrypto.hex(params.str("nonce"))
                dbTag = ForeignCrypto.hex(params.str("tag"))
                dbCt = ForeignCrypto.b64(o.str("db"))
            } catch (e: IllegalArgumentException) {
                throw ImportException("رمز درست بود، اما بدنهٔ فایل Aegis خراب یا ناقص است.")
            }
            val plain = try {
                ForeignCrypto.aesGcmDecrypt(master, dbNonce, dbCt + dbTag)
            } catch (e: WrongImportPasswordException) {
                throw ImportException("رمز درست بود، اما برچسب اصالت بدنهٔ فایل Aegis خراب یا دست‌کاری شده است.")
            }
            val dbText = String(plain, Charsets.UTF_8).removePrefix(BOM.toString()).trim()
            if (jsonTooDeep(dbText)) throw ImportException("ساختار JSON داخل Aegis بیش از حد تودرتو است.")
            return try {
                JSONObject(dbText)
            } catch (e: JSONException) {
                throw ImportException("رمز درست بود، اما محتوای داخل فایل Aegis خراب است.")
            }
        } finally {
            master.fill(0)
        }
    }

    private fun aegis(o: JSONObject, password: CharArray?): ImportResult {
        val header = o.optJSONObject("header")
        val dbRaw = o.opt("db")
        val db: JSONObject = when (dbRaw) {
            is JSONObject -> dbRaw
            is String -> {
                if (header == null) throw ImportException("ساختار هدر فایل Aegis ناقص است.")
                aegisDecrypt(o, header, password)
            }
            else -> throw ImportException("ساختار فیلد db در فایل Aegis نامعتبر است.")
        }

        val groupNames = HashMap<String, String>()
        db.optJSONArray("groups")?.let { gs ->
            for (i in 0 until gs.length()) {
                val g = gs.optJSONObject(i) ?: continue
                val uuid = g.str("uuid")
                val gname = g.str("name")
                if (uuid.isNotEmpty() && gname.isNotEmpty()) groupNames[uuid] = gname
            }
        }
        val entries = db.getJSONArray("entries")
        val out = ArrayList<ItemPayload>()
        var skipped = 0
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: run { skipped++; continue }
            val info = e.optJSONObject("info")
            val otpType = when (e.str("type").lowercase()) {
                "totp" -> OtpType.TOTP
                "hotp" -> OtpType.HOTP
                else -> null
            }
            val issuer = e.str("issuer")
            val name = e.str("name")
            val firstGroup = e.optJSONArray("groups")?.let { ga ->
                if (ga.length() > 0) {
                    val raw = ga.str(0)
                    groupNames[raw] ?: if (groupNames.isEmpty()) raw else ""
                } else ""
            }.orEmpty()
            val p = if (otpType != null && info != null) {
                otpLogin(
                    issuer = issuer,
                    account = name,
                    secretRaw = info.str("secret"),
                    algoRaw = info.str("algo", "SHA1"),
                    digits = info.intOr("digits", 6),
                    period = info.intOr("period", 30),
                    note = e.str("note"),
                    fav = e.optBoolean("favorite"),
                    otpType = otpType,
                    counter = info.longOr("counter", 0L),
                    category = firstGroup,
                )
            } else null
            if (p == null) skipped++ else out += p
        }
        return ImportResult(out, "Aegis", skipped)
    }

    // ───── CSV ─────

    /** پارسر CSV مطابق RFC 4180: کوتیشن، "" برای کوتیشن، و خط جدید داخل کوتیشن؛ پشتیبانی از جداکنندهٔ «,» یا «;». */
    fun csvRows(text: String, delimiter: Char = ','): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endCell() { row.add(cell.toString()); cell.setLength(0) }
        fun endRow() { endCell(); if (row.any { it.isNotEmpty() }) rows.add(row); row = ArrayList() }
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                } else cell.append(ch)
                ch == '"' && cell.isEmpty() -> inQuotes = true
                ch == delimiter -> endCell()
                ch == '\r' -> { if (i + 1 < text.length && text[i + 1] == '\n') i++; endRow() }
                ch == '\n' -> endRow()
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    private val TITLE = listOf("name", "title")
    private val URL = listOf("url", "login_uri", "website", "web site", "uri")
    private val USER = listOf("username", "login_username", "login_name", "user", "user name", "email", "login")
    private val PASS = listOf("password", "login_password")
    private val NOTE = listOf("note", "notes", "extra")
    private val TOTP = listOf("totp", "login_totp", "otpauth", "otpsecret", "otp", "2fa")
    private val CATEGORY = listOf("folder", "grouping", "vault", "category", "group")
    private val FAV = listOf("favorite", "fav", "starred")

    private fun host(url: String): String = app.solina.passwordmanager.autofill.DomainMatcher.normalizeHost(url)

    private fun parseCsv(text: String): ImportResult {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = if (';' in firstLine && ',' !in firstLine) ';' else ','
        val rows = csvRows(text, delimiter)
        if (rows.size < 2) throw ImportException("فایل CSV داده‌ای ندارد یا قالبش شناخته نشد.")
        val head = rows[0].map { it.trim().lowercase() }
        // اولویت با ترتیب نام‌ها در فهرست است (مثلاً «username» بر «email» مقدم است)، نه ترتیب ستون‌ها در فایل.
        fun col(names: List<String>) = names.firstNotNullOfOrNull { n -> head.indexOf(n).takeIf { it >= 0 } } ?: -1
        val cTitle = col(TITLE); val cUrl = col(URL); val cUser = col(USER); val cPass = col(PASS)
        val cNote = col(NOTE); val cTotp = col(TOTP); val cType = col(listOf("type"))
        val cCategory = col(CATEGORY); val cFav = col(FAV)
        if (cPass < 0 && cUser < 0) throw ImportException("ستون‌های نام کاربری/رمز در فایل پیدا نشد. قالب‌های CSV پشتیبانی‌شده: Google/Chrome/Edge/Firefox، Bitwarden، 1Password، LastPass، KeePass، Dashlane، NordPass و Proton Pass.")

        fun cell(r: List<String>, c: Int) = if (c in r.indices) r[c].trim() else ""
        val out = ArrayList<ItemPayload>()
        for (r in rows.drop(1)) {
            val url = cell(r, cUrl)
            val user = cell(r, cUser)
            val title = cell(r, cTitle).ifEmpty { host(url) }.ifEmpty { user }
            if (title.isEmpty() && cell(r, cPass).isEmpty()) continue
            val t = totpFields(cell(r, cTotp))
            val category = cell(r, cCategory)
            val favVal = cell(r, cFav).lowercase()
            val favorite = favVal == "1" || favVal == "true" || favVal == "yes"
            val type = cell(r, cType).lowercase().replace("_", "").replace(" ", "")
            if (type == "note" || type == "securenote") {
                out += ItemPayload(ItemKind.Note, title, note = cell(r, cNote), favorite = favorite, category = category)
                continue
            }
            // نوع‌های دیگر (کارت، هویت، alias و ...) در CSV پشتیبانی نمی‌شوند
            if (type.isNotEmpty() && type != "login" && type != "password") continue
            out += ItemPayload(
                ItemKind.Login,
                title,
                user,
                cell(r, cPass),
                url,
                cell(r, cNote),
                t?.totpSecret.orEmpty(),
                favorite = favorite,
                totpDigits = t?.totpDigits ?: 6,
                totpPeriod = t?.totpPeriod ?: 30,
                totpAlgorithm = t?.totpAlgorithm ?: "SHA1",
                otpType = t?.otpType ?: OtpType.TOTP,
                totpCounter = t?.totpCounter ?: 0L,
                category = category,
            )
        }
        if (out.isEmpty()) throw ImportException("هیچ ورودی قابل‌استفاده‌ای در فایل نبود.")
        return ImportResult(out, "CSV")
    }
}
