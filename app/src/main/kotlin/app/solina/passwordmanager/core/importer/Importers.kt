package app.solina.passwordmanager.core.importer

import app.solina.passwordmanager.core.Base32
import app.solina.passwordmanager.core.otp.OtpImport
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import org.json.JSONException
import org.json.JSONObject

class ImportException(message: String) : Exception(message)

class ImportResult(val items: List<ItemPayload>, val source: String, val skipped: Int = 0)

/** ایمپورت از فایل‌های متنی رایج. فایل‌های رمزشدهٔ دیگر برنامه‌ها پشتیبانی نمی‌شوند (باید ابتدا بدون رمز خروجی گرفت). */
object Importers {
    fun parse(textIn: String): ImportResult {
        val text = textIn.removePrefix("﻿").trim()
        if (text.isEmpty()) throw ImportException("فایل خالی است.")
        return when {
            text.startsWith("{") -> parseJson(text)
            text.lineSequence().any { it.trim().startsWith("otpauth", ignoreCase = true) } ->
                ImportResult(OtpImport.parse(text) ?: throw ImportException("کد TOTP معتبری در فایل نبود."), "otpauth")
            else -> parseCsv(text)
        }
    }

    // ───── JSON ─────

    private fun parseJson(text: String): ImportResult {
        val o = try { JSONObject(text) } catch (e: JSONException) { throw ImportException("فایل JSON خراب است.") }
        return when {
            o.has("items") && (o.has("folders") || o.has("encrypted")) -> bitwarden(o)
            o.has("db") && o.has("header") -> aegis(o)
            else -> throw ImportException("قالب این فایل شناخته نشد. فعلاً Bitwarden (JSON) ، Aegis (JSON بدون رمز) و CSV پشتیبانی می‌شود.")
        }
    }

    private fun totpFields(raw: String): ItemPayload? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        if (v.startsWith("otpauth://", ignoreCase = true)) return OtpImport.parseUri(v)
        val s = v.replace(" ", "").uppercase()
        return if (Base32.decode(s) != null) ItemPayload(totpSecret = s) else null
    }

    private fun bitwarden(o: JSONObject): ImportResult {
        if (o.optBoolean("encrypted")) throw ImportException("این خروجی Bitwarden رمزشده است. در Bitwarden خروجی «.json (بدون رمز)» بگیرید.")
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

    private fun aegis(o: JSONObject): ImportResult {
        val header = o.optJSONObject("header")
        if (header != null && !header.isNull("slots")) throw ImportException("این خروجی Aegis رمزشده است. در Aegis خروجی «JSON بدون رمز» بگیرید.")
        val entries = o.getJSONObject("db").getJSONArray("entries")
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
    private val USER = listOf("username", "login_username", "login_name", "user", "email")
    private val PASS = listOf("password", "login_password")
    private val NOTE = listOf("note", "notes", "extra")
    private val TOTP = listOf("totp", "login_totp", "otpauth")

    private fun host(url: String): String = app.solina.passwordmanager.autofill.DomainMatcher.normalizeHost(url)

    private fun parseCsv(text: String): ImportResult {
        val rows = csvRows(text)
        if (rows.size < 2) throw ImportException("فایل CSV داده‌ای ندارد یا قالبش شناخته نشد.")
        val head = rows[0].map { it.trim().lowercase() }
        fun col(names: List<String>) = head.indexOfFirst { it in names }
        val cTitle = col(TITLE); val cUrl = col(URL); val cUser = col(USER); val cPass = col(PASS)
        val cNote = col(NOTE); val cTotp = col(TOTP); val cType = col(listOf("type"))
        if (cPass < 0 && cUser < 0) throw ImportException("ستون‌های نام کاربری/رمز در فایل پیدا نشد. قالب‌های پشتیبانی‌شده: Chrome/Edge/Firefox، Bitwarden و 1Password (CSV).")

        fun cell(r: List<String>, c: Int) = if (c in r.indices) r[c].trim() else ""
        val out = ArrayList<ItemPayload>()
        for (r in rows.drop(1)) {
            val url = cell(r, cUrl)
            val user = cell(r, cUser)
            val title = cell(r, cTitle).ifEmpty { host(url) }.ifEmpty { user }
            if (title.isEmpty() && cell(r, cPass).isEmpty()) continue
            val t = totpFields(cell(r, cTotp))
            if (cell(r, cType).equals("note", ignoreCase = true) || cell(r, cType).equals("securenote", ignoreCase = true)) {
                out += ItemPayload(ItemKind.Note, title, note = cell(r, cNote))
                continue
            }
            out += ItemPayload(
                ItemKind.Login, title, user, cell(r, cPass), url, cell(r, cNote), t?.totpSecret.orEmpty(),
                totpDigits = t?.totpDigits ?: 6, totpPeriod = t?.totpPeriod ?: 30, totpAlgorithm = t?.totpAlgorithm ?: "SHA1",
            )
        }
        if (out.isEmpty()) throw ImportException("هیچ ورودی قابل‌استفاده‌ای در فایل نبود.")
        return ImportResult(out, "CSV")
    }
}
