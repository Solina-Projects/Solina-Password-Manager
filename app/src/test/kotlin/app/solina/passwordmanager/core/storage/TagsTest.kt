package app.solina.passwordmanager.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** برچسب‌های چندگانه: JSON اختیاری با سازگاری عقب‌رو، و کمک‌تابع‌های TagHelper. */
class TagsTest {
    @Test fun payloadRoundTripWithTags() {
        val p = ItemPayload(title = "GitHub", category = "کار", tags = listOf("مهم", "کاری", "dev"))
        val back = ItemPayload.fromBytes(p.toBytes())
        assertEquals(listOf("مهم", "کاری", "dev"), back.tags)
        assertEquals("کار", back.category)
    }

    @Test fun oldPayloadWithoutTagsDefaultsToEmpty() {
        // JSON قدیمی بدون کلید tags
        val o = org.json.JSONObject().put("title", "Old").put("kind", "login")
        val back = ItemPayload.fromBytes(o.toString().toByteArray(Charsets.UTF_8))
        assertTrue(back.tags.isEmpty())
    }

    @Test fun parseTagsSplitsAndDedupes() {
        assertEquals(listOf("a", "b", "c"), TagHelper.parseTags("a, b،c , A"))
        assertEquals(listOf("مهم", "کاری"), TagHelper.parseTags("  مهم ، کاری\n"))
        assertTrue(TagHelper.parseTags(" , ، ").isEmpty())
    }

    @Test fun addAndRemoveTag() {
        val tags = TagHelper.addTag(listOf("a"), "  B ")
        assertEquals(listOf("a", "B"), tags)
        // تکراری (بی‌توجه به بزرگی/کوچکی) و خالی، فهرست را بدون تغییر برمی‌گردانند.
        assertEquals(listOf("a", "B"), TagHelper.addTag(tags, "b"))
        assertEquals(listOf("a", "B"), TagHelper.addTag(tags, ""))
        assertEquals(listOf("B"), TagHelper.removeTag(tags, "A"))
    }

    @Test fun allTagsAndMatches() {
        val items = listOf(
            VaultItem("1", 1, ItemPayload(title = "x", tags = listOf("dev", "مهم"))),
            VaultItem("2", 1, ItemPayload(title = "y", tags = listOf("DEV"))),
            VaultItem("3", 1, ItemPayload(title = "z")),
        )
        assertEquals(listOf("DEV", "dev", "مهم"), TagHelper.allTags(items))
        assertTrue(TagHelper.matchesTag(items[0].payload, "DEV"))
        assertTrue(TagHelper.matchesTag(items[1].payload, "dev"))
    }
}
