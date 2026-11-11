package app.solina.passwordmanager.scan

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/** رمزگشایی QR با ZXing (کتابخانهٔ جاوای خالص). */
object QrDecoder {
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )

    private fun decode(source: LuminanceSource): String? {
        val reader = QRCodeReader()
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: Exception) {
            // NotFound/Checksum/Format: کادر فعلی QR ندارد.
            null
        } finally {
            reader.reset()
        }
    }

    /** از صفحهٔ روشنایی (Y) یک فریم دوربین؛ [rowStride] ممکن است از [width] بزرگ‌تر باشد. */
    fun decodeLuminance(y: ByteArray, width: Int, height: Int, rowStride: Int = width): String? =
        decode(PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false))

    fun decodePixels(pixels: IntArray, width: Int, height: Int): String? =
        decode(RGBLuminanceSource(width, height, pixels))

    /** QR داخل یک عکس (مثلاً اسکرین‌شات). بزرگ‌ها کوچک می‌شوند تا حافظه پر نشود. */
    fun decodeImage(resolver: ContentResolver, uri: Uri, maxSide: Int = 1600): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp: Bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        try {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            return decodePixels(px, bmp.width, bmp.height)
        } finally {
            bmp.recycle()
        }
    }
}
