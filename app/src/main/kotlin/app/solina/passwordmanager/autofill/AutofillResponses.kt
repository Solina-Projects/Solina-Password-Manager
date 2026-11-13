package app.solina.passwordmanager.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import app.solina.passwordmanager.R
import app.solina.passwordmanager.core.storage.VaultItem

@Suppress("DEPRECATION")
object AutofillResponses {
    const val EXTRA_USERNAME_ID = "solina.autofill.username"
    const val EXTRA_PASSWORD_ID = "solina.autofill.password"
    const val EXTRA_HOST = "solina.autofill.host"
    const val EXTRA_PACKAGE = "solina.autofill.package"

    private fun row(ctx: Context, title: String, subtitle: String) =
        RemoteViews(ctx.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.subtitle, subtitle)
        }

    /** null اگر ورودی مطابقی نیست. */
    fun datasets(ctx: Context, items: List<VaultItem>, p: ParsedRequest, packageCertSha256: String? = null): FillResponse? {
        val matches = AutofillMatcher.match(items, p.webHost, p.packageName, packageCertSha256)
        if (matches.isEmpty()) return null
        val b = FillResponse.Builder()
        for (m in matches) {
            val d = Dataset.Builder(row(ctx, m.payload.title.ifBlank { m.payload.website }, m.payload.username))
            p.usernameId?.let { d.setValue(it, AutofillValue.forText(m.payload.username)) }
            p.passwordId?.let { d.setValue(it, AutofillValue.forText(m.payload.password)) }
            b.addDataset(d.build())
        }
        return b.build()
    }

    /** خزانه قفل است: فقط یک گزینهٔ «باز کردن» که کاربر را به صفحهٔ باز کردن می‌برد. */
    fun locked(ctx: Context, p: ParsedRequest): FillResponse {
        val intent = Intent(ctx, AutofillAuthActivity::class.java)
            .putExtra(EXTRA_USERNAME_ID, p.usernameId)
            .putExtra(EXTRA_PASSWORD_ID, p.passwordId)
            .putExtra(EXTRA_HOST, p.webHost)
            .putExtra(EXTRA_PACKAGE, p.packageName)
        // سیستم باید بتواند داده‌های اضافه به intent بیفزاید، پس در اندروید ۱۲+ باید mutable باشد (مقصد صریح است).
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val sender = PendingIntent.getActivity(ctx, System.currentTimeMillis().toInt(), intent, flags).intentSender
        val ids: Array<AutofillId> = listOfNotNull(p.usernameId, p.passwordId).toTypedArray()
        return FillResponse.Builder()
            .setAuthentication(ids, sender, row(ctx, "باز کردن Solina", "برای پر کردن خودکار، خزانه را باز کنید"))
            .build()
    }
}
