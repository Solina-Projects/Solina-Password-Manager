package app.solina.passwordmanager.core.importer

import app.solina.passwordmanager.core.Base32
import app.solina.passwordmanager.core.otp.OtpImport
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import org.json.JSONException
import org.json.JSONObject

open class ImportException(message: String) : Exception(message)

/** فایل رمزشده است و رمز لازم دارد؛ رابط باید رمز را بپرسد و دوباره تلاش کند. */
class ImportPasswordRequired(val source: String) : ImportException("این فایل ($source) رمزشده است؛ رمز آن را وارد کنید.")

class ImportResult(val items: List<ItemPayload>, val source: String, val skipped: Int = 0)

/** ایمپورت از خروجی برنامه‌های دیگر؛ شامل بعضی خروجی‌های رمزشده (Aegis، 2FAS، Bitwarden با رمز). */
object Importers {
    /** نشانهٔ ترتیب بایت UTF-8 که بعضی برنامه‌ها اول CSV می‌گذارند. */
    private val BOM = 0xFEFF.toChar()

    /** بایت‌های فایل را می‌خواند: ZIP (مثل خروجی Proton Pass) باز می‌شود و PGP با پیام راهنما رد می‌شود. */
    fun parseBytes(bytes: ByteArray, password: CharArray? = null): ImportResult {
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2].toInt() == 3 && bytes[3].toInt() == 4) {
            return parse(unzipFirstText(bytes), password)
        }
        val text = String(bytes, Charsets.UTF_8)
        if (text.trimStart().startsWith("-----BEGIN PGP MESSAGE-----")) throw pgpUnsupported()
        return parse(text, password)
    }

    private fun pgpUnsupported() = ImportException(
        "این فایل با PGP رمز شده (خروجی رمزشدهٔ Proton Pass) و هنوز پشتیبانی نمی‌شود. در Proton Pass خروجی «بدون رمز (ZIP)» یا CSV بگیرید.",
    )

    private fun unzipFirstText(bytes: ByteArray): String {
        val limit = 20 * 1024 * 1024
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zin ->
            var entry = zin.nextEntry
            var sawPgp = false
            var count = 0
            while (entry != null && count++ < 50) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory && (name.endsWith(".json") || name.endsWith(".csv") || name.endsWith(".txt"))) {
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zin.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > limit) throw ImportException("فایل داخل ZIP بیش از حد بزرگ است.")
                    }
                    return String(out.toByteArray(), Charsets.UTF_8)
                }
                if (name.endsWith(".pgp")) sawPgp = true
                entry = zin.nextEntry
            }
            if (sawPgp) throw pgpUnsupported()
        }
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
        val a = try { org.json.JSONArray(text) } catch (e: JSONException) { throw ImportException("فایل JSON خراب است.") }
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

    private fun otpLogin(issuer: String, account: String, secretRaw: String, algoRaw: String, digits: Int, period: Int, note: String = "", fav: Boolean = false): ItemPayload? {
        val secret = secretRaw.replace(" ", "").uppercase()
        val algo = algoRaw.uppercase().replace("-", "").ifEmpty { "SHA1" }
        if (Base32.decode(secret) == null || algo !in ALGOS || digits !in 6..8 || period !in 1..300) return null
        return ItemPayload(
            ItemKind.Login, issuer.ifEmpty { account }, if (issuer.isNotEmpty()) account else "", note = note,
            totpSecret = secret, totpDigits = digits, totpPeriod = period, totpAlgorithm = algo, favorite = fav,
        )
    }

    private fun andOtp(a: org.json.JSONArray): ImportResult {
        val out = ArrayList<ItemPayload>(); var skipped = 0
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            val p = if (e.optString("type", "TOTP").equals("TOTP", true))
                otpLogin(e.optString("issuer"), e.optString("label"), e.optString("secret"), e.optString("algorithm", "SHA1"), e.optInt("digits", 6), e.optInt("period", 30))
            else null
            if (p == null) skipped++ else out += p
        }
        if (out.isEmpty()) throw ImportException("هیچ کد TOTP قابل‌استفاده‌ای در فایل نبود.")
        return ImportResult(out, "andOTP", skipped)
    }

    private fun raivo(a: org.json.JSONArray): ImportResult {
        val out = ArrayList<ItemPayload>(); var skipped = 0
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            val p = if (e.optString("kind", "TOTP").equals("TOTP", true))
                otpLogin(e.optString("issuer"), e.optString("account"), e.optString("secret"), e.optString("algorithm", "SHA1"), e.optString("digits", "6").toIntOrNull() ?: 6, e.optString("timer", "30").toIntOrNull() ?: 30)
            else null
            if (p == null) skipped++ else out += p
        }
        if (out.isEmpty()) throw ImportException("هیچ کد TOTP قابل‌استفاده‌ای در فایل نبود.")
        return ImportResult(out, "Raivo", skipped)
    }

    /** پشتیبان آخر برای JSON ناشناخته: هر رشتهٔ otpauth:// در هر جای فایل (مثل Proton Authenticator و Ente) خوانده می‌شود. */
    private fun collectOtpauth(root: Any): ImportResult? {
        val uris = ArrayList<String>()
        val stack = ArrayDeque<Any>(); stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < 200_000) {
            when (val n = stack.removeLast()) {
                is JSONObject -> for (k in n.keys()) n.opt(k)?.let { stack.add(it) }
                is org.json.JSONArray -> for (i in 0 until n.length()) n.opt(i)?.let { stack.add(it) }
                is String -> if (n.startsWith("otpauth://", ignoreCase = true) || n.startsWith("otpauth-migration://", ignoreCase = true)) uris += n
            }
        }
        if (uris.isEmpty()) return null
        val items = OtpImport.parse(uris.joinToString("\n")) ?: throw ImportException("کد TOTP معتبری در فایل نبود.")
        return ImportResult(items, "otpauth (JSON)", (uris.size - items.size).coerceAtLeast(0))
    }

    // ───── 2FAS ─────

    private fun twoFas(o: JSONObject, password: CharArray?): ImportResult {
        val enc = o.optString("servicesEncrypted")
        val arr: org.json.JSONArray = if (enc.isNotEmpty() && (!o.has("services") || o.isNull("services"))) {
            if (password == null) throw ImportPasswordRequired("2FAS")
            val parts = enc.split(":")
            if (parts.size != 3) throw ImportException("ساختار فایل 2FAS خراب است.")
            val plain = try {
                val ct = ForeignCrypto.b64(parts[0]); val salt = ForeignCrypto.b64(parts[1]); val iv = ForeignCrypto.b64(parts[2])
                val key = ForeignCrypto.pbkdf2Sha256(password, salt, 10_000)
                try { ForeignCrypto.aesGcmDecrypt(key, iv, ct) } finally { key.fill(0) }
            } catch (e: IllegalArgumentException) { throw ImportException("ساختار فایل 2FAS خراب است.") }
            try { org.json.JSONArray(String(plain, Charsets.UTF_8)) } catch (e: JSONException) { throw WrongImportPasswordException() }
        } else o.getJSONArray("services")
        val out = ArrayList<ItemPayload>(); var skipped = 0
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val otp = e.optJSONObject("otp")
            val type = otp?.optString("tokenType", "TOTP") ?: "TOTP"
            val p = if (type.equals("TOTP", true)) otpLogin(
                otp?.optString("issuer").orEmpty().ifEmpty { e.optString("name") },
                otp?.optString("account").orEmpty(),
                e.optString("secret"), otp?.optString("algorithm", "SHA1").orEmpty(),
                otp?.optInt("digits", 6) ?: 6, otp?.optInt("period", 30) ?: 30,
            ) else null
            if (p == null) skipped++ else out += p
        }
        if (out.isEmpty()) throw ImportException("هیچ کد TOTP قابل‌استفاده‌ای در فایل نبود.")
        return ImportResult(out, "2FAS", skipped)
    }

    // ───── Proton Pass (خروجی بدون رمز) ─────

    private fun protonPass(o: JSONObject): ImportResult {
        if (o.optBoolean("encrypted")) throw pgpUnsupported()
        val vaults = o.getJSONObject("vaults")
        val out = ArrayList<ItemPayload>(); var skipped = 0
        for (vk in vaults.keys()) {
            val items = vaults.getJSONObject(vk).optJSONArray("items") ?: continue
            for (i in 0 until items.length()) {
                val data = items.getJSONObject(i).optJSONObject("data")
                if (data == null) { skipped++; continue }
                val meta = data.optJSONObject("metadata")
                val name = meta?.optString("name").orEmpty()
                val note = meta?.optString("note").orEmpty()
                val c = data.optJSONObject("content")
                when (data.optString("type")) {
                    "login" -> {
                        val user = c?.optString("itemUsername").orEmpty().ifEmpty { c?.optString("itemEmail").orEmpty() }
                        val t = totpFields(c?.optString("totpUri").orEmpty())
                        out += ItemPayload(
                            ItemKind.Login, name, user, c?.optString("password").orEmpty(), c?.optJSONArray("urls")?.optString(0).orEmpty(), note,
                            t?.totpSecret.orEmpty(), totpDigits = t?.totpDigits ?: 6, totpPeriod = t?.totpPeriod ?: 30, totpAlgorithm = t?.totpAlgorithm ?: "SHA1",
                        )
                    }
                    "note" -> out += ItemPayload(ItemKind.Note, name, note = note)
                    "creditCard" -> {
                        val exp = c?.optString("expirationDate").orEmpty() // YYYY-MM
                        val mmYY = if (exp.length >= 7) exp.substring(5, 7) + "/" + exp.substring(2, 4) else exp
                        out += ItemPayload(
                            ItemKind.Card, name, note = note, cardHolder = c?.optString("cardholderName").orEmpty(),
                            cardNumber = c?.optString("number").orEmpty(), cardExpiry = mmYY, cardCvv = c?.optString("verificationNumber").orEmpty(),
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
        val s = v.replace(" ", "").uppercase()
        return if (Base32.decode(s) != null) ItemPayload(totpSecret = s) else null
    }

    private fun bitwardenDecrypt(o: JSONObject, password: CharArray?): JSONObject {
        if (!o.optBoolean("passwordProtected")) {
            throw ImportException("این خروجی Bitwarden با کلید حساب رمز شده و بیرون از Bitwarden قابل‌خواندن نیست. در Bitwarden خروجی «.json (رمزشده با رمز فایل)» یا «بدون رمز» بگیرید.")
        }
        if (password == null) throw ImportPasswordRequired("Bitwarden")
        val salt = o.getString("salt")
        val iterations = o.optInt("kdfIterations", 600_000).also { if (it !in 1..10_000_000) throw ImportException("پارامتر KDF فایل نامعتبر است.") }
        val master = when (o.optInt("kdfType", 0)) {
            0 -> ForeignCrypto.pbkdf2Sha256(password, salt.toByteArray(Charsets.UTF_8), iterations)
            1 -> {
                val mem = o.optInt("kdfMemory", 64); val par = o.optInt("kdfParallelism", 4)
                if (iterations !in 1..64 || mem !in 1..1024 || par !in 1..16) throw ImportException("پارامتر KDF فایل نامعتبر است.")
                ForeignCrypto.bitwardenArgon2(password, salt, iterations, mem, par)
            }
            else -> throw ImportException("نوع KDF این فایل Bitwarden پشتیبانی نمی‌شود.")
        }
        val encKey = ForeignCrypto.hkdfExpand32(master, "enc")
        val macKey = ForeignCrypto.hkdfExpand32(master, "mac")
        try {
            o.optString("encKeyValidation_DO_NOT_EDIT").takeIf { it.isNotEmpty() }?.let { ForeignCrypto.bitwardenDecrypt(it, encKey, macKey) }
            val plain = ForeignCrypto.bitwardenDecrypt(o.getString("data"), encKey, macKey)
            return try { JSONObject(String(plain, Charsets.UTF_8)) } catch (e: JSONException) { throw WrongImportPasswordException() }
        } finally { master.fill(0); encKey.fill(0); macKey.fill(0) }
    }

    private fun bitwarden(oIn: JSONObject, password: CharArray?): ImportResult {
        val o = if (oIn.optBoolean("encrypted")) bitwardenDecrypt(oIn, password) else oIn
        val arr = o.getJSONArray("items")
        val out = ArrayList<ItemPayload>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val it = arr.getJSONObject(i)
            val name = it.optString("name")
            val notes = it.optString("notes")
            val fav = it.optBoolean("favorite")
            when (it.optInt("type")) {
                1 -> {
                    val l = it.optJSONObject("login")
                    val uri = l?.optJSONArray("uris")?.optJSONObject(0)?.optString("uri").orEmpty()
                    val t = totpFields(l?.optString("totp").orEmpty())
                    out += ItemPayload(
                        ItemKind.Login, name, l?.optString("username").orEmpty(), l?.optString("password").orEmpty(),
                        uri, notes, t?.totpSecret.orEmpty(), fav,
                        totpDigits = t?.totpDigits ?: 6, totpPeriod = t?.totpPeriod ?: 30, totpAlgorithm = t?.totpAlgorithm ?: "SHA1",
                    )
                }
                2 -> out += ItemPayload(ItemKind.Note, name, note = notes, favorite = fav)
                3 -> {
                    val c = it.optJSONObject("card")
                    val mm = c?.optString("expMonth").orEmpty().padStart(2, '0')
                    val yy = c?.optString("expYear").orEmpty().takeLast(2)
                    out += ItemPayload(
                        ItemKind.Card, name, note = notes, favorite = fav,
                        cardHolder = c?.optString("cardholderName").orEmpty(), cardNumber = c?.optString("number").orEmpty(),
                        cardExpiry = if (c?.optString("expMonth").isNullOrEmpty()) "" else "$mm/$yy", cardCvv = c?.optString("code").orEmpty(),
                    )
                }
                else -> skipped++
            }
        }
        return ImportResult(out, "Bitwarden", skipped)
    }

    private fun aegisDecrypt(o: JSONObject, header: JSONObject, password: CharArray?): JSONObject {
        if (password == null) throw ImportPasswordRequired("Aegis")
        val slots = header.getJSONArray("slots")
        var master: ByteArray? = null
        for (i in 0 until slots.length()) {
            val sl = slots.getJSONObject(i)
            if (sl.optInt("type") != 1) continue
            val n = sl.getInt("n"); val r = sl.getInt("r"); val p = sl.getInt("p")
            // سقف پارامترها برای جلوگیری از فایل مخرب که حافظهٔ زیادی بخواهد
            if (n !in 2..(1 shl 20) || n and (n - 1) != 0 || r !in 1..32 || p !in 1..16 || 128L * n * r > 512L * 1024 * 1024) throw ImportException("پارامترهای scrypt فایل نامعتبر است.")
            val kp = sl.getJSONObject("key_params")
            val wrapper = ForeignCrypto.scrypt(password, ForeignCrypto.hex(sl.getString("salt")), n, r, p)
            try {
                master = ForeignCrypto.aesGcmDecrypt(wrapper, ForeignCrypto.hex(kp.getString("nonce")), ForeignCrypto.hex(sl.getString("key")) + ForeignCrypto.hex(kp.getString("tag")))
                break
            } catch (e: WrongImportPasswordException) {
                // اسلات دیگری را امتحان می‌کنیم
            } finally { wrapper.fill(0) }
        }
        if (master == null) throw WrongImportPasswordException()
        try {
            val params = header.getJSONObject("params")
            val plain = ForeignCrypto.aesGcmDecrypt(
                master, ForeignCrypto.hex(params.getString("nonce")),
                ForeignCrypto.b64(o.getString("db")) + ForeignCrypto.hex(params.getString("tag")),
            )
            return try { JSONObject(String(plain, Charsets.UTF_8)) } catch (e: JSONException) { throw WrongImportPasswordException() }
        } finally { master.fill(0) }
    }

    private fun aegis(o: JSONObject, password: CharArray?): ImportResult {
        val header = o.optJSONObject("header")
        val db = if (header != null && !header.isNull("slots")) aegisDecrypt(o, header, password) else o.getJSONObject("db")
        val entries = db.getJSONArray("entries")
        val out = ArrayList<ItemPayload>()
        var skipped = 0
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val info = e.optJSONObject("info")
            val secret = info?.optString("secret").orEmpty().replace(" ", "").uppercase()
            val algo = info?.optString("algo", "SHA1")?.uppercase().orEmpty().ifEmpty { "SHA1" }
            val digits = info?.optInt("digits", 6) ?: 6
            val period = info?.optInt("period", 30) ?: 30
            if (e.optString("type").lowercase() != "totp" || Base32.decode(secret) == null ||
                algo !in setOf("SHA1", "SHA256", "SHA512") || digits !in 6..8 || period !in 1..300
            ) { skipped++; continue }
            val issuer = e.optString("issuer")
            val name = e.optString("name")
            out += ItemPayload(
                ItemKind.Login, issuer.ifEmpty { name }, if (issuer.isNotEmpty()) name else "", note = e.optString("note"),
                totpSecret = secret, totpDigits = digits, totpPeriod = period, totpAlgorithm = algo, favorite = e.optBoolean("favorite"),
            )
        }
        return ImportResult(out, "Aegis", skipped)
    }

    // ───── CSV ─────

    /** پارسر CSV مطابق RFC 4180: کوتیشن، "" برای کوتیشن، و خط جدید داخل کوتیشن. */
    fun csvRows(text: String): List<List<String>> {
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
                ch == ',' -> endCell()
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

    private fun host(url: String): String = app.solina.passwordmanager.autofill.DomainMatcher.normalizeHost(url)

    private fun parseCsv(text: String): ImportResult {
        val rows = csvRows(text)
        if (rows.size < 2) throw ImportException("فایل CSV داده‌ای ندارد یا قالبش شناخته نشد.")
        val head = rows[0].map { it.trim().lowercase() }
        // اولویت با ترتیب نام‌ها در فهرست است (مثلاً «username» بر «email» مقدم است)، نه ترتیب ستون‌ها در فایل.
        fun col(names: List<String>) = names.firstNotNullOfOrNull { n -> head.indexOf(n).takeIf { it >= 0 } } ?: -1
        val cTitle = col(TITLE); val cUrl = col(URL); val cUser = col(USER); val cPass = col(PASS)
        val cNote = col(NOTE); val cTotp = col(TOTP); val cType = col(listOf("type"))
        if (cPass < 0 && cUser < 0) throw ImportException("ستون‌های نام کاربری/رمز در فایل پیدا نشد. قالب‌های CSV پشتیبانی‌شده: Google/Chrome/Edge/Firefox، Bitwarden، 1Password، LastPass، KeePass، Dashlane، NordPass و Proton Pass.")

        fun cell(r: List<String>, c: Int) = if (c in r.indices) r[c].trim() else ""
        val out = ArrayList<ItemPayload>()
        for (r in rows.drop(1)) {
            val url = cell(r, cUrl)
            val user = cell(r, cUser)
            val title = cell(r, cTitle).ifEmpty { host(url) }.ifEmpty { user }
            if (title.isEmpty() && cell(r, cPass).isEmpty()) continue
            val t = totpFields(cell(r, cTotp))
            val type = cell(r, cType).lowercase().replace("_", "").replace(" ", "")
            if (type == "note" || type == "securenote") {
                out += ItemPayload(ItemKind.Note, title, note = cell(r, cNote))
                continue
            }
            // نوع‌های دیگر (کارت، هویت، alias و ...) در CSV پشتیبانی نمی‌شوند
            if (type.isNotEmpty() && type != "login" && type != "password") continue
            out += ItemPayload(
                ItemKind.Login, title, user, cell(r, cPass), url, cell(r, cNote), t?.totpSecret.orEmpty(),
                totpDigits = t?.totpDigits ?: 6, totpPeriod = t?.totpPeriod ?: 30, totpAlgorithm = t?.totpAlgorithm ?: "SHA1",
            )
        }
        if (out.isEmpty()) throw ImportException("هیچ ورودی قابل‌استفاده‌ای در فایل نبود.")
        return ImportResult(out, "CSV")
    }
}
