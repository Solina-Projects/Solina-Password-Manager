package app.solina.refuge.core

import app.solina.refuge.core.importer.Importers
import app.solina.refuge.core.otp.OtpImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Steam Guard (فاز ۴): کد ۵حرفی با الفبای مخصوص.
 *
 * بردارهای آزمون با پیاده‌سازی مستقل پایتون (hmac/hashlib استاندارد) محاسبه شده‌اند،
 * نه با همین کد؛ پس خطا در الگوریتم را لو می‌دهند:
 * راز = بایت‌های ASCII «12345678901234567890»، دورهٔ ۳۰ ثانیه.
 * - t=0 → GG5F5 ، t=30000 → PV9M4 ، t=1234567890000 → VHHQY
 * - shared_secret همان راز به base64: MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=
 */
class SteamTotpTest {
    private val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test fun steamCodeMatchesIndependentVector() {
        assertEquals("GG5F5", Totp.generateSteam(secret, 0L))
        assertEquals("PV9M4", Totp.generateSteam(secret, 30_000L))
        assertEquals("VHHQY", Totp.generateSteam(secret, 1_234_567_890_000L))
    }

    @Test fun steamCodeIsFiveLettersFromSteamAlphabet() {
        // زمان پایه دقیقاً ابتدای یک پنجرهٔ ۳۰ثانیه‌ای است (مضرب ۳۰٬۰۰۰)، تا ‎+29٬999ms
        // در همان پنجره بماند و ‎+30٬000ms پنجرهٔ بعد باشد.
        val windowStart = 1_699_999_980_000L // 56_666_666 × 30_000
        val code = Totp.generateSteam(secret, windowStart)
        assertEquals(5, code.length)
        assertTrue(code.all { it in Totp.STEAM_ALPHABET })
        // در همان پنجرهٔ ۳۰ثانیه‌ای کد ثابت است، در پنجرهٔ بعد عوض می‌شود.
        assertEquals(code, Totp.generateSteam(secret, windowStart + 29_999L))
        assertTrue(code != Totp.generateSteam(secret, windowStart + 30_000L))
    }

    @Test fun steamDiffersFromNumericTotp() {
        val numeric = Totp.generate(secret, 0L, 30, 6, "HmacSHA1")
        assertEquals(6, numeric.length)
        assertTrue(numeric.all { it.isDigit() })
        assertEquals("GG5F5", Totp.generateSteam(secret, 0L))
    }

    @Test fun otpauthSteamUriParses() {
        val uri = "otpauth://steam/Steam:myaccount?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Steam"
        val p = OtpImport.parseUri(uri)
        assertNotNull(p)
        assertEquals(OtpType.STEAM, p!!.otpType)
        assertEquals(5, p.totpDigits)
        assertEquals("Steam", p.title)
        // الگوریتم غیر-SHA1 برای استیم نامعتبر است.
        assertNull(OtpImport.parseUri(uri.replace("issuer=Steam", "issuer=Steam&algorithm=SHA256")))
    }

    @Test fun steamMaFileImports() {
        val json = """{
            "shared_secret": "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
            "serial_number": "STEAM1234567890",
            "account_name": "myaccount",
            "revocation_code": "R12345",
            "uri": "otpauth://totp/Steam:myaccount?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Steam",
            "server_time": "1700000000",
            "token_gid": "1234567890abcdef",
            "identity_secret": "AAAAAAAAAAAAAAAAAAAAAA==",
            "secret_1": "BBBBBBBBBBBBBBBBBBBBBB==",
            "status": 1,
            "device_id": "android:12345678-1234-1234-1234-123456789012"
        }"""
        val result = Importers.parse(json)
        assertEquals("Steam (maFile)", result.source)
        assertEquals(1, result.items.size)
        val item = result.items[0]
        assertEquals(OtpType.STEAM, item.otpType)
        assertEquals("Steam", item.title)
        assertEquals("myaccount", item.username)
        assertEquals(5, item.totpDigits)
        // کد تولیدشده از راز واردشده با بردار مستقل یکی است.
        val code = Totp.generateSteam(Base32.decode(item.totpSecret)!!, 0L)
        assertEquals("GG5F5", code)
    }

    @Test fun steamMaFileRejectsBadSecret() {
        val bad = """{"shared_secret": "!!!not-base64!!!", "account_name": "x"}"""
        try {
            Importers.parse(bad)
            assertTrue("باید ImportException بدهد", false)
        } catch (e: app.solina.refuge.core.importer.ImportException) {
            assertTrue(e.message!!.contains("Steam"))
        }
        val missing = """{"account_name": "x"}"""
        try {
            Importers.parse(missing)
            assertTrue("باید ImportException بدهد", false)
        } catch (e: app.solina.refuge.core.importer.ImportException) {
            // بدون shared_secret قالب شناخته نمی‌شود (یا خطای Steam می‌دهد)؛ هر دو قابل قبول است.
            assertTrue(true)
        }
    }

    @Test fun steamTypeSurvivesPayloadRoundTrip() {
        val p = app.solina.refuge.core.storage.ItemPayload(
            title = "Steam",
            totpSecret = "GEZDGNBVGY3TQOJQ",
            totpDigits = 5,
            otpType = OtpType.STEAM,
        )
        val back = app.solina.refuge.core.storage.ItemPayload.fromBytes(p.toBytes())
        assertEquals(OtpType.STEAM, back.otpType)
        assertEquals(5, back.totpDigits)
    }
}
