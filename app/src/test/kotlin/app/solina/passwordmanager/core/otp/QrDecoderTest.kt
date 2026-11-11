package app.solina.passwordmanager.core.otp

import app.solina.passwordmanager.scan.QrDecoder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {
    private fun render(text: String, size: Int, rowStride: Int): ByteArray {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val y = ByteArray(rowStride * size) { 0x80.toByte() }   // حاشیهٔ padding با مقدار دلخواه
        for (r in 0 until size) for (c in 0 until size) y[r * rowStride + c] = if (m[c, r]) 0 else 255.toByte()
        return y
    }

    @Test fun decodesRenderedQrWithRowPadding() {
        val text = "otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        assertEquals(text, QrDecoder.decodeLuminance(render(text, 320, 320), 320, 320, 320))
        assertEquals(text, QrDecoder.decodeLuminance(render(text, 320, 352), 320, 320, 352))
    }

    @Test fun blankFrameGivesNull() {
        assertNull(QrDecoder.decodeLuminance(ByteArray(200 * 200) { 255.toByte() }, 200, 200))
    }
}
