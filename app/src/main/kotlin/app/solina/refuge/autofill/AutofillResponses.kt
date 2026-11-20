package app.solina.refuge.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.v1.InlineSuggestionUi
import app.solina.refuge.R
import app.solina.refuge.core.storage.VaultItem
import java.util.concurrent.atomic.AtomicInteger

@Suppress("DEPRECATION")
object AutofillResponses {
    const val EXTRA_USERNAME_ID = "solina.autofill.username"
    const val EXTRA_PASSWORD_ID = "solina.autofill.password"
    const val EXTRA_HOST = "solina.autofill.host"
    const val EXTRA_PACKAGE = "solina.autofill.package"

    private val requestCodeCounter = AtomicInteger(10_000)

    private fun nextReqCode(): Int = requestCodeCounter.incrementAndGet() and 0x7fff

    private fun row(ctx: Context, title: String, subtitle: String) =
        RemoteViews(ctx.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.subtitle, subtitle)
        }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("RestrictedApi")
    private fun inlineChip(
        ctx: Context,
        spec: InlinePresentationSpec,
        title: String,
        subtitle: String,
    ): InlinePresentation? = runCatching {
        val attribution = PendingIntent.getActivity(
            ctx,
            nextReqCode(),
            Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = InlineSuggestionUi.newContentBuilder(attribution).setTitle(title)
        if (subtitle.isNotBlank()) builder.setSubtitle(subtitle)
        InlinePresentation(builder.build().slice, spec, false)
    }.getOrNull()

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
     * پاسخ پر کردن خودکار (با پشتیبانی از Inline Autofill در اندروید ۱۱+ و Dropdown در همهٔ نسخه‌ها)
     * به همراه SaveInfo برای ذخیرهٔ ورودی‌های جدید.
     *
     * @param preselected فقط برای یادآوری چندمرحله‌ای: وقتی داده شود، به‌جای تطابق معمولی
     *   همین ورودی‌ها پیشنهاد می‌شوند. فراخواننده باید مبدأ را خودش بررسی کرده باشد
     *   (همان دامنه یا همان بسته)؛ هرگز ورودی دلخواه اینجا ندهید.
     */
    fun datasets(
        ctx: Context,
        items: List<VaultItem>,
        p: ParsedRequest,
        includeSaveInfo: Boolean = true,
        inlineRequest: InlineSuggestionsRequest? = null,
        preselected: List<VaultItem>? = null,
    ): FillResponse? {
        val matches = preselected ?: AutofillMatcher.match(items, p.webHost, p.packageName)
        val saveInfo = if (includeSaveInfo) buildSaveInfo(p) else null
        if (matches.isEmpty() && saveInfo == null) return null

        val specs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineRequest?.inlinePresentationSpecs.orEmpty()
        } else emptyList()

        val b = FillResponse.Builder()
        matches.forEachIndexed { idx, m ->
            val title = m.payload.title.ifBlank { m.payload.website }
            val subtitle = m.payload.username
            val dropdown = row(ctx, title, subtitle)
            val d = Dataset.Builder(dropdown)
            val inline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && specs.isNotEmpty()) {
                val spec = specs[idx.coerceAtMost(specs.lastIndex)]
                inlineChip(ctx, spec, title, subtitle)
            } else null

            p.usernameId?.let { id ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inline != null) {
                    d.setValue(id, AutofillValue.forText(m.payload.username), dropdown, inline)
                } else {
                    d.setValue(id, AutofillValue.forText(m.payload.username))
                }
            }
            p.passwordId?.let { id ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inline != null) {
                    d.setValue(id, AutofillValue.forText(m.payload.password), dropdown, inline)
                } else {
                    d.setValue(id, AutofillValue.forText(m.payload.password))
                }
            }
            b.addDataset(d.build())
        }
        saveInfo?.let { b.setSaveInfo(it) }
        return b.build()
    }

    /** خزانه قفل است: گزینهٔ «باز کردن Refuge» (هم به‌شکل Inline روی کیبورد و هم Dropdown). */
    fun locked(
        ctx: Context,
        p: ParsedRequest,
        inlineRequest: InlineSuggestionsRequest? = null,
    ): FillResponse {
        val intent = Intent(ctx, AutofillAuthActivity::class.java)
            .putExtra(EXTRA_USERNAME_ID, p.usernameId)
            .putExtra(EXTRA_PASSWORD_ID, p.passwordId)
            .putExtra(EXTRA_HOST, p.webHost)
            .putExtra(EXTRA_PACKAGE, p.packageName)
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val sender = PendingIntent.getActivity(ctx, nextReqCode(), intent, flags).intentSender
        val ids: Array<AutofillId> = listOfNotNull(p.usernameId, p.passwordId).toTypedArray()
        val dropdown = row(ctx, "باز کردن Refuge", "برای پر کردن خودکار، خزانه را باز کنید")
        val builder = FillResponse.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val spec = inlineRequest?.inlinePresentationSpecs?.firstOrNull()
            val inline = spec?.let { inlineChip(ctx, it, "Refuge", "باز کردن خزانه") }
            if (inline != null) {
                builder.setAuthentication(ids, sender, dropdown, inline)
                return builder.build()
            }
        }
        return builder.setAuthentication(ids, sender, dropdown).build()
    }
}
