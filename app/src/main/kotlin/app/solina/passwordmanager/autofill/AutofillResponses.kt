package app.solina.passwordmanager.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import app.solina.passwordmanager.R
import app.solina.passwordmanager.core.storage.VaultItem
import java.util.concurrent.atomic.AtomicInteger

@Suppress("DEPRECATION")
object AutofillResponses {
    const val EXTRA_USERNAME_ID = "solina.autofill.username"
    const val EXTRA_PASSWORD_ID = "solina.autofill.password"
    const val EXTRA_HOST = "solina.autofill.host"
    const val EXTRA_PACKAGE = "solina.autofill.package"

    private val requestCodeCounter = AtomicInteger(10_000)

    private fun row(ctx: Context, title: String, subtitle: String) =
        RemoteViews(ctx.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.subtitle, subtitle)
        }

    private fun buildSaveInfo(p: ParsedRequest): SaveInfo? {
        val required = listOfNotNull(p.passwordId).toTypedArray()
        if (required.isEmpty()) return null
        var type = SaveInfo.SAVE_DATA_TYPE_PASSWORD
        if (p.usernameId != null) type = type or SaveInfo.SAVE_DATA_TYPE_USERNAME
        val sb = SaveInfo.Builder(type, required)
        p.usernameId?.let { sb.setOptionalIds(arrayOf(it)) }
        return sb.build()
    }

    /**
     * پاسخ پر کردن خودکار به همراه SaveInfo برای ذخیرهٔ ورودی‌های جدید.
     * null اگر نه ورودی مطابقی هست و نه فیلد رمزی برای ذخیره وجود دارد.
     */
    fun datasets(ctx: Context, items: List<VaultItem>, p: ParsedRequest, includeSaveInfo: Boolean = true): FillResponse? {
        val matches = AutofillMatcher.match(items, p.webHost, p.packageName)
        val saveInfo = if (includeSaveInfo) buildSaveInfo(p) else null
        if (matches.isEmpty() && saveInfo == null) return null
        val b = FillResponse.Builder()
        for (m in matches) {
            val d = Dataset.Builder(row(ctx, m.payload.title.ifBlank { m.payload.website }, m.payload.username))
            p.usernameId?.let { d.setValue(it, AutofillValue.forText(m.payload.username)) }
            p.passwordId?.let { d.setValue(it, AutofillValue.forText(m.payload.password)) }
            b.addDataset(d.build())
        }
        saveInfo?.let { b.setSaveInfo(it) }
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
        val reqCode = requestCodeCounter.incrementAndGet() and 0x7fff
        val sender = PendingIntent.getActivity(ctx, reqCode, intent, flags).intentSender
        val ids: Array<AutofillId> = listOfNotNull(p.usernameId, p.passwordId).toTypedArray()
        return FillResponse.Builder()
            .setAuthentication(ids, sender, row(ctx, "باز کردن Solina", "برای پر کردن خودکار، خزانه را باز کنید"))
            .build()
    }
}
