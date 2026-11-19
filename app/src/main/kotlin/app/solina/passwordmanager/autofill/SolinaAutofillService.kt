package app.solina.passwordmanager.autofill

import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import app.solina.passwordmanager.SolinaApplication
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * سرویس Autofill. هیچ رمزی را خودش نگه نمی‌دارد: اگر خزانه باز باشد از فهرست در حافظهٔ نشست می‌خواند،
 * وگرنه فقط گزینهٔ «باز کردن» نشان می‌دهد. در حالت باز، ذخیرهٔ ورودی جدید از طریق onSaveRequest نیز پشتیبانی می‌شود.
 */
class SolinaAutofillService : AutofillService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val container = (application as SolinaApplication).container
        val structure = request.fillContexts.lastOrNull()?.structure
        val parsed = structure?.let { AutofillParser.parse(it, packageName) }
        if (parsed == null) {
            callback.onSuccess(null)
            return
        }
        container.session.enforceTimeout(container.securityPrefs.autoLockMillis())
        val response = if (container.session.unlocked.value) {
            AutofillResponses.datasets(this, container.repository.items.value, parsed)
        } else {
            AutofillResponses.locked(this, parsed)
        }
        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val container = (application as SolinaApplication).container
        container.session.enforceTimeout(container.securityPrefs.autoLockMillis())
        if (!container.session.unlocked.value) {
            callback.onFailure("برای ذخیرهٔ رمز جدید ابتدا خزانهٔ Solina را باز کنید.")
            return
        }
        val structure = request.fillContexts.lastOrNull()?.structure
        val parsed = structure?.let { AutofillParser.parse(it, packageName) }
        if (parsed == null || parsed.passwordValue.isBlank()) {
            callback.onSuccess()
            return
        }
        val website = parsed.webHost?.let { "https://$it" } ?: parsed.packageName.orEmpty()
        val title = parsed.webHost ?: parsed.packageName?.substringAfterLast('.') ?: "حساب جدید"
        serviceScope.launch {
            runCatching {
                val repo = container.repository
                val existing = repo.items.value.firstOrNull { item ->
                    item.payload.kind == ItemKind.Login &&
                        item.payload.username.equals(parsed.usernameValue, ignoreCase = true) &&
                        (
                            (!parsed.webHost.isNullOrBlank() && DomainMatcher.hostMatches(item.payload.website, parsed.webHost)) ||
                                (parsed.webHost.isNullOrBlank() && item.payload.website == website)
                            )
                }
                if (existing != null) {
                    repo.update(existing.id, existing.payload.copy(password = parsed.passwordValue))
                } else {
                    repo.add(
                        ItemPayload(
                            kind = ItemKind.Login,
                            title = title,
                            username = parsed.usernameValue,
                            password = parsed.passwordValue,
                            website = website,
                        ),
                    )
                }
            }
            callback.onSuccess()
        }
    }
}
