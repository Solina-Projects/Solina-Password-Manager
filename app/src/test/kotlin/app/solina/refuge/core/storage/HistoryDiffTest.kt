package app.solina.refuge.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** تفاوت سطح فیلد بین نسخه‌های تاریخچه؛ رمز عبور فقط با نام فیلد گزارش می‌شود. */
class HistoryDiffTest {
    private val base = ItemPayload(
        title = "GitHub", username = "u", password = "pw-old-xxxxxxxx",
        website = "https://github.com", category = "کار", tags = listOf("dev"),
    )

    @Test fun detectsChangedFields() {
        val changed = base.copy(title = "GitHub 2", password = "pw-new-yyyyyyyy", tags = listOf("dev", "مهم"))
        assertEquals(
            listOf(ChangedField.Title, ChangedField.Password, ChangedField.Tags),
            HistoryDiff.diffFields(base, changed),
        )
    }

    @Test fun identicalPayloadsHaveNoDiff() {
        assertTrue(HistoryDiff.diffFields(base, base.copy()).isEmpty())
    }

    @Test fun passwordChangeReportsFieldNameOnly() {
        val diff = HistoryDiff.diffFields(base, base.copy(password = "totally-different-pw-1"))
        assertEquals(listOf(ChangedField.Password), diff)
        // diff فقط enum فیلد است؛ هیچ مقداری از رمز در آن نیست.
        assertTrue(diff.none { it.name.contains("pw") })
    }

    @Test fun groupsCardAndIdentityFields() {
        val card = HistoryDiff.diffFields(base, base.copy(cardNumber = "60379911"))
        assertEquals(listOf(ChangedField.Card), card)
        val id = HistoryDiff.diffFields(base, base.copy(fullName = "علی", phone = "0916"))
        assertEquals(listOf(ChangedField.Identity), id)
        val totp = HistoryDiff.diffFields(base, base.copy(totpSecret = "JBSWY3DP"))
        assertEquals(listOf(ChangedField.Totp), totp)
    }
}
