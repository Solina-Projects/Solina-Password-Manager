package app.solina.passwordmanager.autofill

import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.os.Build
import android.view.View
import android.view.autofill.AutofillId

/** نتیجهٔ تحلیل صفحه: فیلدهای ورود، مقادیر پرشده (برای Save) و مبدأ (دامنهٔ وب معتبر یا نام بستهٔ برنامه). */
class ParsedRequest(
    val webHost: String?,
    val packageName: String?,
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val usernameValue: String = "",
    val passwordValue: String = "",
)

object AutofillParser {
    private class Field(
        val id: AutofillId,
        val info: FieldInfo,
        val domain: String?,
        val scheme: String?,
        val value: String,
    )

    /** null اگر صفحه فیلد ورود ندارد، از خود Solina است یا مبدأ وب ناامن (HTTP) دارد. */
    fun parse(
        structure: AssistStructure,
        ownPackage: String,
        isTrustedBrowser: (String?) -> Boolean = TrustedBrowsers::isTrusted,
    ): ParsedRequest? {
        val pkg = structure.activityComponent?.packageName
        if (pkg == ownPackage) return null
        val trustedBrowser = isTrustedBrowser(pkg)

        val fields = ArrayList<Field>()
        fun visit(n: ViewNode, inheritedDomain: String?, inheritedScheme: String?) {
            // تنها مرورگرهای معتبر حق گزارش webDomain دارند؛ دامنه از همان گره یا نزدیک‌ترین والد می‌آید.
            val nodeDomain = if (trustedBrowser) n.webDomain?.takeIf { it.isNotBlank() } else null
            val nodeScheme = if (trustedBrowser && Build.VERSION.SDK_INT >= 28) n.webScheme?.takeIf { it.isNotBlank() } else null
            val domain = nodeDomain ?: inheritedDomain
            val scheme = if (nodeDomain != null) nodeScheme else (nodeScheme ?: inheritedScheme)
            val id = n.autofillId
            if (id != null && n.autofillType == View.AUTOFILL_TYPE_TEXT && n.visibility == View.VISIBLE) {
                val htmlType = n.htmlInfo?.attributes?.firstOrNull { it.first.equals("type", ignoreCase = true) }?.second
                val textVal = n.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                    ?: n.text?.toString()
                    ?: ""
                fields += Field(
                    id,
                    FieldInfo(n.autofillHints?.toList().orEmpty(), n.inputType, n.idEntry, n.hint, htmlType),
                    domain,
                    scheme,
                    textVal,
                )
            }
            for (i in 0 until n.childCount) visit(n.getChildAt(i), domain, scheme)
        }
        for (w in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(w).rootViewNode, null, null)

        val picked = FieldClassifier.pickLoginFields(fields.map { FieldClassifier.classify(it.info) }) ?: return null
        val user = picked.first?.let { fields[it] }
        val pass = picked.second?.let { fields[it] }
        val anchor = pass ?: user
        val host = anchor?.domain
        val scheme = anchor?.scheme
        // صفحهٔ وب روی HTTP ناامن (به جز localhost) نباید به‌صورت خودکار پر شود.
        if (host != null && !DomainMatcher.isSafeWebOrigin(scheme, host)) return null
        return ParsedRequest(
            webHost = host,
            packageName = pkg,
            usernameId = user?.id,
            passwordId = pass?.id,
            usernameValue = user?.value.orEmpty(),
            passwordValue = pass?.value.orEmpty(),
        )
    }
}
