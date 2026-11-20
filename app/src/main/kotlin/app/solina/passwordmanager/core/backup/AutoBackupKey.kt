package app.solina.passwordmanager.core.backup

import app.solina.passwordmanager.core.crypto.Hkdf
import app.solina.passwordmanager.core.crypto.wipe
import java.util.Base64

/**
 * رمز بکاپ خودکار، مشتق از کلید خزانه.
 *
 * اسنپ‌شات زمان‌بندی‌شده همان قالب SLNB v1 بکاپ دستی است (بدون تغییر قالب و بدون
 * مهاجرت)؛ فقط «رمز بکاپ» آن از کلید خزانه مشتق می‌شود:
 * HKDF-SHA256(VaultKey, info «solina/autobackup/v1») → ۳۲ بایت → Base64url.
 * پس فایل عملاً با کلید خزانه رمز شده است: فقط دستگاهی که همان خزانه را دارد
 * می‌تواند آن را باز کند (با همان [Backup.decode] و همین رمز مشتق‌شده) و کاربر
 * هیچ رمز تازه‌ای برای به‌خاطر سپردن ندارد. بکاپ دستی با رمز انتخابی کاربر
 * سر جایش می‌ماند و تغییر نکرده است.
 */
object AutoBackupKey {
    private val INFO = "solina/autobackup/v1".toByteArray(Charsets.US_ASCII)

    /** ۴۳ نویسهٔ Base64url؛ فراخواننده بعد از استفاده باید wipe کند. */
    fun backupPasswordChars(vaultKey: ByteArray): CharArray {
        val derived = Hkdf.derive(vaultKey, null, INFO)
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(derived).toCharArray()
        } finally {
            derived.wipe()
        }
    }
}
