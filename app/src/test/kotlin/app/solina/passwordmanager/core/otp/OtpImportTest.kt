package app.solina.passwordmanager.core.otp

import app.solina.passwordmanager.core.Base32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

class OtpImportTest {
    @Test fun base32EncodeRoundTrip() {
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
        val raw = byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x21, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        assertEquals("JBSWY3DPEHPK3PXP", Base32.encode(raw))
        assertEquals(raw.toList(), Base32.decode("JBSWY3DPEHPK3PXP")!!.toList())
    }

    @Test fun standardUri() {
        val p = OtpImport.parse("otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example")!!.single()
        assertEquals("Example", p.title); assertEquals("alice@google.com", p.username)
        assertEquals("JBSWY3DPEHPK3PXP", p.totpSecret)
        assertEquals(6, p.totpDigits); assertEquals(30, p.totpPeriod); assertEquals("SHA1", p.totpAlgorithm)
    }

    @Test fun uriWithParamsAndEncoding() {
        val p = OtpImport.parse("otpauth://totp/My%20Bank:user%40mail.com?secret=jbswy3dp ehpk3pxp&algorithm=SHA256&digits=8&period=60&issuer=My+Bank")!!.single()
        assertEquals("My Bank", p.title); assertEquals("user@mail.com", p.username)
        assertEquals("JBSWY3DPEHPK3PXP", p.totpSecret)
        assertEquals(8, p.totpDigits); assertEquals(60, p.totpPeriod); assertEquals("SHA256", p.totpAlgorithm)
    }

    @Test fun rejectsBadInput() {
        assertNull(OtpImport.parse("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=1"))
        assertNull(OtpImport.parse("otpauth://totp/x?secret=!!!"))
        assertNull(OtpImport.parse("otpauth://totp/x"))
        assertNull(OtpImport.parse("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=12"))
        assertNull(OtpImport.parse("https://example.com"))
    }

    @Test fun multipleLines() {
        val text = "otpauth://totp/A?secret=JBSWY3DPEHPK3PXP\n\nnot a link\notpauth://totp/B:b?secret=MZXW6YTBOI"
        assertEquals(listOf("A", "B"), OtpImport.parse(text)!!.map { it.title })
    }

    // ───── migration ─────
    private fun varint(out: ByteArrayOutputStream, v0: Long) { var v = v0; while (v >= 0x80) { out.write(((v and 0x7f) or 0x80).toInt()); v = v ushr 7 }; out.write(v.toInt()) }
    private fun lenField(out: ByteArrayOutputStream, field: Int, data: ByteArray) { varint(out, ((field shl 3) or 2).toLong()); varint(out, data.size.toLong()); out.write(data) }
    private fun intField(out: ByteArrayOutputStream, field: Int, v: Int) { varint(out, (field shl 3).toLong()); varint(out, v.toLong()) }

    private fun otpParams(secret: ByteArray, name: String, issuer: String, algo: Int, digits: Int, type: Int): ByteArray {
        val o = ByteArrayOutputStream()
        lenField(o, 1, secret); lenField(o, 2, name.toByteArray()); lenField(o, 3, issuer.toByteArray())
        intField(o, 4, algo); intField(o, 5, digits); intField(o, 6, type)
        return o.toByteArray()
    }

    @Test fun googleAuthenticatorMigration() {
        val secret = byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x21, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        val payload = ByteArrayOutputStream()
        lenField(payload, 1, otpParams(secret, "Example:alice", "Example", 1, 1, 2))
        lenField(payload, 1, otpParams(secret, "hotp-one", "X", 1, 1, 1))      // HOTP نادیده گرفته می‌شود
        lenField(payload, 1, otpParams(secret, "bob", "Bank", 2, 2, 2))
        intField(payload, 2, 1) // فیلد ناشناخته (version) نباید خطا بدهد
        val b64 = Base64.getEncoder().encodeToString(payload.toByteArray())
        val uri = "otpauth-migration://offline?data=" + b64.replace("+", "%2B").replace("/", "%2F").replace("=", "%3D")
        val items = OtpImport.parse(uri)!!
        assertEquals(2, items.size)
        assertEquals("Example", items[0].title); assertEquals("alice", items[0].username)
        assertEquals("JBSWY3DPEHPK3PXP", items[0].totpSecret)
        assertEquals("Bank", items[1].title); assertEquals("SHA256", items[1].totpAlgorithm); assertEquals(8, items[1].totpDigits)
    }

    @Test fun truncatedMigrationDoesNotCrash() {
        assertNull(OtpImport.parse("otpauth-migration://offline?data=CgQ"))
        assertNotNull(OtpImport.parse("otpauth://totp/x?secret=MZXW6YTBOI"))
    }
}
