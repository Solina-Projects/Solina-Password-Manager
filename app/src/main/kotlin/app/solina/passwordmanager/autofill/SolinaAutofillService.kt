package app.solina.passwordmanager.autofill

import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import app.solina.passwordmanager.SolinaApplication
import app.solina.passwordmanager.core.crypto.toHex
import java.security.MessageDigest

/**
 * سرویس Autofill. هیچ رمزی را خودش نگه نمی‌دارد: اگر خزانه باز باشد از فهرست در حافظهٔ نشست می‌خواند،
 * وگرنه فقط گزینهٔ «باز کردن» نشان می‌دهد. ذخیرهٔ ورودی جدید از طریق Autofill هنوز پشتیبانی نمی‌شود.
 */
class SolinaAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val container = (application as SolinaApplication).container
        val structure = request.fillContexts.lastOrNull()?.structure
        val parsed = structure?.let { AutofillParser.parse(it, packageName) }
        if (parsed == null) {
            callback.onSuccess(null)
            return
        }
        container.session.enforceTimeout()
        val response = if (container.session.unlocked.value) {
            val certSha256 = parsed.packageName?.let(::signingCertSha256)
            AutofillResponses.datasets(this, container.repository.items.value, parsed, certSha256)
        } else {
            AutofillResponses.locked(this, parsed)
        }
        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onFailure("ذخیرهٔ خودکار رمز جدید هنوز در Solina پشتیبانی نمی‌شود")
    }

    @Suppress("DEPRECATION")
    private fun signingCertSha256(pkg: String): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return null
            val sigs = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
            sigs?.firstOrNull()?.toByteArray() ?: return null
        } else {
            val info = packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            info.signatures?.firstOrNull()?.toByteArray() ?: return null
        }
        md.digest(bytes).toHex()
    }.getOrNull()
}
