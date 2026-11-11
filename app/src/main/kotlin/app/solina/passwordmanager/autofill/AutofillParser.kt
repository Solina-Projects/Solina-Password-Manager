package app.solina.passwordmanager.autofill

import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.view.View
import android.view.autofill.AutofillId

/** نتیجهٔ تحلیل صفحه: فیلدهای ورود و مبدأ (دامنهٔ وب یا نام بستهٔ برنامه). */
class ParsedRequest(
    val webHost: String?,
    val packageName: String?,
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
)

object AutofillParser {
    private class Field(val id: AutofillId, val info: FieldInfo, val domain: String?)

    /** null اگر صفحه فیلد ورود ندارد یا از خود Solina است. */
    fun parse(structure: AssistStructure, ownPackage: String): ParsedRequest? {
        val pkg = structure.activityComponent?.packageName
        if (pkg == ownPackage) return null

        val fields = ArrayList<Field>()
        fun visit(n: ViewNode, inherited: String?) {
            // دامنه از همان گره (یا نزدیک‌ترین والد) می‌آید؛ نه اولین دامنهٔ صفحه، تا iframe بیگانه فریب ندهد.
            val domain = n.webDomain?.takeIf { it.isNotBlank() } ?: inherited
            val id = n.autofillId
            if (id != null && n.autofillType == View.AUTOFILL_TYPE_TEXT && n.visibility == View.VISIBLE) {
                val htmlType = n.htmlInfo?.attributes?.firstOrNull { it.first.equals("type", ignoreCase = true) }?.second
                fields += Field(
                    id,
                    FieldInfo(n.autofillHints?.toList().orEmpty(), n.inputType, n.idEntry, n.hint, htmlType),
                    domain,
                )
            }
            for (i in 0 until n.childCount) visit(n.getChildAt(i), domain)
        }
        for (w in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(w).rootViewNode, null)

        val picked = FieldClassifier.pickLoginFields(fields.map { FieldClassifier.classify(it.info) }) ?: return null
        val user = picked.first?.let { fields[it] }
        val pass = picked.second?.let { fields[it] }
        val host = (pass ?: user)?.domain
        return ParsedRequest(host, pkg, user?.id, pass?.id)
    }
}
