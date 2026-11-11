package app.solina.passwordmanager.autofill

import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import app.solina.passwordmanager.SolinaApplication

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
            AutofillResponses.datasets(this, container.repository.items.value, parsed)
        } else {
            AutofillResponses.locked(this, parsed)
        }
        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }
}
