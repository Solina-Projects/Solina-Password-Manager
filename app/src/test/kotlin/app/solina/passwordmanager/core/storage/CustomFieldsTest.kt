package app.solina.passwordmanager.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** فیلدهای سفارشی (فاز ۴): JSON اختیاری، سازگاری عقب‌رو، و محدودیت‌های sanitize. */
class CustomFieldsTest {
    @Test fun payloadRoundTripWithCustomFields() {
        val p = ItemPayload(
            title = "GitHub",
            customFields = listOf(
                CustomField("پین بازیابی", "1234", hidden = true),
                CustomField("سؤال امنیتی", "نام اولین حیوان", hidden = false),
            ),
        )
        val back = ItemPayload.fromBytes(p.toBytes())
        assertEquals(p.customFields, back.customFields)
        assertEquals(true, back.customFields[0].hidden)
        assertEquals(false, back.customFields[1].hidden)
    }

    @Test fun oldPayloadWithoutCustomFieldsDefaultsToEmpty() {
        val o = org.json.JSONObject().put("title", "Old").put("kind", "login")
        val back = ItemPayload.fromBytes(o.toString().toByteArray(Charsets.UTF_8))
        assertTrue(back.customFields.isEmpty())
    }

    @Test fun sanitizeDropsEmptyLabelOrValue() {
        assertNull(CustomField.sanitize("", "x", false))
        assertNull(CustomField.sanitize("label", "", false))
        assertNull(CustomField.sanitize("   ", "x", true))
        assertEquals(CustomField("a", "b", true), CustomField.sanitize("  a ", "b", true))
    }

    @Test fun sanitizeTruncatesLongLabelAndValue() {
        val f = CustomField.sanitize("x".repeat(100), "y".repeat(600), false)!!
        assertEquals(CustomField.MAX_LABEL, f.label.length)
        assertEquals(CustomField.MAX_VALUE, f.value.length)
    }

    @Test fun roundTripCapsAtMaxFields() {
        val many = (1..25).map { CustomField("f$it", "v$it") }
        val back = ItemPayload.fromBytes(ItemPayload(title = "T", customFields = many).toBytes())
        assertEquals(CustomField.MAX_FIELDS, back.customFields.size)
    }

    @Test fun customFieldsWorkForAllKinds() {
        for (kind in ItemKind.entries) {
            val p = ItemPayload(kind = kind, title = "T", customFields = listOf(CustomField("k", "v")))
            assertEquals(listOf(CustomField("k", "v")), ItemPayload.fromBytes(p.toBytes()).customFields)
        }
    }
}
