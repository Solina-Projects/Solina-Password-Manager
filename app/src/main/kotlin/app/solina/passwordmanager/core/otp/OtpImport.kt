package app.solina.passwordmanager.core.otp

import app.solina.passwordmanager.core.Base32
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * تبدیل متن QR یا فایل به ورودی‌های TOTP. پشتیبانی:
 * - `otpauth://totp/...` (قالب استاندارد، یک یا چند خط)
 * - `otpauth-migration://offline?data=...` (خروجی Google Authenticator)
 * HOTP و رازهای نامعتبر نادیده گرفته می‌شوند.
 */
object OtpImport {
    /** null یعنی هیچ ورودی معتبری پیدا نشد. */
    fun parse(text: String): List<ItemPayload>? {
        val out = ArrayList<ItemPayload>()
        for (line in text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
            when {
                line.startsWith("otpauth://", ignoreCase = true) -> parseUri(line)?.let(out::add)
                line.startsWith("otpauth-migration://", ignoreCase = true) -> out.addAll(parseMigration(line))
            }
        }
        return out.ifEmpty { null }
    }

    private fun percentDecode(s: String, plusIsSpace: Boolean): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) { bytes.write(hex); i += 3; continue }
            }
            if (ch == '+' && plusIsSpace) bytes.write(' '.code) else bytes.write(ch.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun queryParams(query: String): Map<String, String> =
        query.split('&').filter { it.contains('=') }.associate {
            percentDecode(it.substringBefore('='), true).lowercase() to percentDecode(it.substringAfter('='), true)
        }

    fun parseUri(uri: String): ItemPayload? {
        val rest = uri.substring("otpauth://".length)
        val type = rest.substringBefore('/').lowercase()
        if (type != "totp") return null
        val afterType = rest.substringAfter('/', "")
        val label = percentDecode(afterType.substringBefore('?'), false).trim()
        val q = queryParams(afterType.substringAfter('?', ""))

        val secret = q["secret"]?.replace(" ", "")?.uppercase() ?: return null
        if (Base32.decode(secret) == null) return null
        val labelIssuer = if (':' in label) label.substringBefore(':').trim() else ""
        val account = label.substringAfter(':').trim()
        val issuer = q["issuer"]?.trim().takeUnless { it.isNullOrEmpty() } ?: labelIssuer
        val algo = when (q["algorithm"]?.uppercase()) {
            null, "", "SHA1" -> "SHA1"
            "SHA256" -> "SHA256"
            "SHA512" -> "SHA512"
            else -> return null
        }
        val digits = (q["digits"]?.toIntOrNull() ?: 6).takeIf { it in 6..8 } ?: return null
        val period = (q["period"]?.toIntOrNull() ?: 30).takeIf { it in 1..300 } ?: return null
        return ItemPayload(
            kind = ItemKind.Login,
            title = issuer.ifEmpty { account }.ifEmpty { "TOTP" },
            username = account,
            totpSecret = secret,
            totpDigits = digits,
            totpPeriod = period,
            totpAlgorithm = algo,
        )
    }

    // ───── Google Authenticator migration (protobuf) ─────

    fun parseMigration(uri: String): List<ItemPayload> {
        val data = queryParams(uri.substringAfter('?', ""))["data"] ?: return emptyList()
        val bytes = try {
            val clean = data.trim().replace(' ', '+')
            val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
            Base64.getDecoder().decode(padded.replace('-', '+').replace('_', '/'))
        } catch (e: IllegalArgumentException) {
            return emptyList()
        }
        val result = ArrayList<ItemPayload>()
        try {
            val r = ProtoReader(bytes)
            while (r.hasMore()) {
                val (field, wire) = r.tag()
                if (field == 1 && wire == 2) parseOtpParameters(r.bytes())?.let(result::add) else r.skip(wire)
            }
        } catch (e: IllegalArgumentException) {
            // داده ناقص: همان مقدار که تا اینجا خوانده شد برگردانده می‌شود.
        }
        return result
    }

    private fun parseOtpParameters(msg: ByteArray): ItemPayload? {
        var secret: ByteArray? = null
        var name = ""
        var issuer = ""
        var algorithm = 1
        var digits = 1
        var type = 2
        val r = ProtoReader(msg)
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 2 -> secret = r.bytes()
                field == 2 && wire == 2 -> name = String(r.bytes(), Charsets.UTF_8)
                field == 3 && wire == 2 -> issuer = String(r.bytes(), Charsets.UTF_8)
                field == 4 && wire == 0 -> algorithm = r.varint().toInt()
                field == 5 && wire == 0 -> digits = r.varint().toInt()
                field == 6 && wire == 0 -> type = r.varint().toInt()
                else -> r.skip(wire)
            }
        }
        if (type != 2 || secret == null || secret.isEmpty()) return null // فقط TOTP
        val algo = when (algorithm) { 0, 1 -> "SHA1"; 2 -> "SHA256"; 3 -> "SHA512"; else -> return null }
        val account = if (':' in name && issuer.isNotEmpty()) name.substringAfter(':').trim() else name.trim()
        return ItemPayload(
            kind = ItemKind.Login,
            title = issuer.ifEmpty { account }.ifEmpty { "TOTP" },
            username = account,
            totpSecret = Base32.encode(secret),
            totpDigits = if (digits == 2) 8 else 6,
            totpAlgorithm = algo,
        )
    }

    private class ProtoReader(private val b: ByteArray) {
        private var pos = 0
        fun hasMore() = pos < b.size
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < b.size && shift < 64) { "truncated" }
                val x = b[pos++].toInt() and 0xff
                result = result or ((x and 0x7f).toLong() shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
            }
        }
        fun tag(): Pair<Int, Int> { val t = varint().toInt(); return (t ushr 3) to (t and 7) }
        fun bytes(): ByteArray {
            val len = varint().toInt()
            require(len >= 0 && pos + len <= b.size) { "truncated" }
            return b.copyOfRange(pos, pos + len).also { pos += len }
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> { require(pos + 8 <= b.size) { "truncated" }; pos += 8 }
                2 -> bytes()
                5 -> { require(pos + 4 <= b.size) { "truncated" }; pos += 4 }
                else -> throw IllegalArgumentException("bad wire type")
            }
        }
    }
}
