package app.solina.refuge.core.notes

import app.solina.refuge.core.crypto.VaultCrypto
import app.solina.refuge.core.formatJalali
import app.solina.refuge.core.gregorianToJalali
import app.solina.refuge.core.jalaliToGregorian
import app.solina.refuge.core.storage.CheckItem
import app.solina.refuge.core.storage.ItemKind
import app.solina.refuge.core.storage.ItemPayload
import app.solina.refuge.core.storage.TagHelper
import app.solina.refuge.core.storage.TaskPriority
import app.solina.refuge.core.storage.TaskRepeat
import app.solina.refuge.core.storage.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class NotesTasksTest {
    private val utc = ZoneOffset.UTC
    // ۱۴۰۵/۰۸/۲۹ = 2026-11-20 (UTC)
    private val now = 1795132800000L // 2026-11-20T00:00:00Z

    private fun note(title: String = "خرید", body: String = "خرید\nشیر و نان") =
        VaultItem("n1", 1, ItemPayload(kind = ItemKind.QuickNote, title = title, body = body, tags = listOf("بانک")))

    private fun task(title: String, dueAt: Long = 0L, done: Boolean = false, pinned: Boolean = false, repeat: TaskRepeat = TaskRepeat.None) =
        VaultItem("t-$title", 1, ItemPayload(kind = ItemKind.Task, title = title, dueAt = dueAt, done = done, pinned = pinned, repeat = repeat))

    // ── رمزنگاری: هیچ متن‌بازی در دیتابیس ──

    @Test fun noteEncryptRoundTrip() {
        val key = ByteArray(32) { it.toByte() }
        val payload = ItemPayload(
            kind = ItemKind.QuickNote,
            title = "عنوان محرمانهٔ XYZ",
            body = "بدنهٔ محرمانهٔ ABC با جزئیات بانکی",
            checklist = listOf(CheckItem("آیتم محرمانهٔ QQQ", false)),
            tags = listOf("بانک"),
            linkedItemId = "vault-1",
        )
        val plain = payload.toBytes()
        val sealed = VaultCrypto.sealItem(key, "n1", 1, ItemPayload.SCHEMA, plain)
        val raw = String(sealed.blob, Charsets.ISO_8859_1) + String(sealed.wrappedItemKey, Charsets.ISO_8859_1)
        assertFalse(raw.contains("عنوان محرمانه"))
        assertFalse(raw.contains("بدنهٔ محرمانه"))
        assertFalse(raw.contains("آیتم محرمانه"))
        assertFalse(raw.contains("بانک"))
        assertFalse(raw.contains("vault-1"))
        val opened = VaultCrypto.openItem(key, "n1", 1, ItemPayload.SCHEMA, sealed)
        val back = ItemPayload.fromBytes(opened)
        assertEquals(payload.title, back.title)
        assertEquals(payload.body, back.body)
        assertEquals(payload.checklist, back.checklist)
        assertEquals(payload.tags, back.tags)
        assertEquals(payload.linkedItemId, back.linkedItemId)
    }

    @Test fun taskEncryptRoundTrip() {
        val key = ByteArray(32) { (it * 7).toByte() }
        val payload = ItemPayload(
            kind = ItemKind.Task, title = "پرداخت قسط محرمانه", dueAt = now + 86_400_000L,
            priority = TaskPriority.High, repeat = TaskRepeat.Monthly, linkedNoteId = "n1",
        )
        val sealed = VaultCrypto.sealItem(key, "t1", 1, ItemPayload.SCHEMA, payload.toBytes())
        val raw = String(sealed.blob, Charsets.ISO_8859_1)
        assertFalse(raw.contains("پرداخت قسط"))
        val back = ItemPayload.fromBytes(VaultCrypto.openItem(key, "t1", 1, ItemPayload.SCHEMA, sealed))
        assertEquals(payload, back.copy(createdAt = back.createdAt, editedAt = back.editedAt))
        assertEquals(TaskPriority.High, back.priority)
        assertEquals(TaskRepeat.Monthly, back.repeat)
        assertEquals(now + 86_400_000L, back.dueAt)
    }

    @Test fun oldPayloadStillReads() {
        // سازگاری به عقب: JSON قدیمی بدون فیلدهای جدید خوانده می‌شود.
        val back = ItemPayload.fromBytes(
            org.json.JSONObject().put("kind", "login").put("title", "قدیمی").toString().toByteArray(Charsets.UTF_8),
        )
        assertEquals("قدیمی", back.title)
        assertEquals(emptyList<CheckItem>(), back.checklist)
        assertEquals(TaskPriority.Medium, back.priority)
        assertEquals(TaskRepeat.None, back.repeat)
        assertEquals(0L, back.dueAt)
        assertFalse(back.done)
    }

    // ── عنوان خودکار ──

    @Test fun deriveTitleFromFirstLine() {
        assertEquals("شیر بخر", deriveTitle("  شیر بخر\nنان هم\n"))
        assertEquals("", deriveTitle("\n\n"))
        assertEquals("تک‌خط", deriveTitle("تک‌خط"))
    }

    // ── تکرار ──

    @Test fun nextOccurrenceDailyWeeklyMonthly() {
        val base = 1795132800000L // 2026-11-20T00:00Z
        assertEquals(base + 86_400_000L, nextOccurrence(TaskRepeat.Daily, base, utc))
        assertEquals(base + 7 * 86_400_000L, nextOccurrence(TaskRepeat.Weekly, base, utc))
        // ماهانه: ۲۰ نوامبر ← ۲۰ دسامبر
        val dec20 = java.time.LocalDateTime.of(2026, 12, 20, 0, 0).toInstant(utc).toEpochMilli()
        assertEquals(dec20, nextOccurrence(TaskRepeat.Monthly, base, utc))
        // ماهانه از ۳۱ ژانویه به ۲۸ فوریه می‌چسبد (بدون پرش به مارس)
        val jan31 = java.time.LocalDateTime.of(2026, 1, 31, 10, 0).toInstant(utc).toEpochMilli()
        val feb28 = java.time.LocalDateTime.of(2026, 2, 28, 10, 0).toInstant(utc).toEpochMilli()
        assertEquals(feb28, nextOccurrence(TaskRepeat.Monthly, jan31, utc))
        assertEquals(0L, nextOccurrence(TaskRepeat.None, base, utc))
    }

    @Test fun completeRepeatingTaskCreatesNext() {
        val item = task("قسط", dueAt = now, repeat = TaskRepeat.Monthly)
        val (done, next) = completeTask(item, now, utc)
        assertTrue(done.done)
        assertTrue(next != null)
        assertFalse(next!!.done)
        assertEquals(nextOccurrence(TaskRepeat.Monthly, now, utc), next.dueAt)
        val (done2, next2) = completeTask(task("ساده", dueAt = now), now, utc)
        assertTrue(done2.done)
        assertEquals(null, next2)
    }

    // ── نماها ──

    @Test fun overdueNeverLost() {
        val items = listOf(
            task("عقب‌افتاده", dueAt = now - 86_400_000L),
            task("امروز", dueAt = now + 3_600_000L),
            task("انجام‌شدهٔ قدیمی", dueAt = now - 86_400_000L, done = true),
            task("سنجاق بدون موعد", pinned = true),
        )
        assertEquals(1, overdueCount(items, now))
        assertEquals(listOf("عقب‌افتاده"), tasksOverdue(items, now).map { it.payload.title })
        assertEquals(listOf("امروز", "سنجاق بدون موعد"), tasksToday(items, now, utc).map { it.payload.title })
        assertTrue(tasksToday(items, now, utc).none { it.payload.title == "عقب‌افتاده" })
    }

    @Test fun upcomingGroupedByJalaliDay() {
        val items = listOf(
            task("فردا", dueAt = now + 86_400_000L),
            task("پس‌فردا", dueAt = now + 2 * 86_400_000L),
            task("فردا دوم", dueAt = now + 86_400_000L + 3_600_000L),
        )
        val groups = tasksUpcoming(items, now, utc)
        assertEquals(2, groups.size)
        assertEquals(formatJalali(now + 86_400_000L, utc), groups[0].first)
        assertEquals(listOf("فردا", "فردا دوم"), groups[0].second.map { it.payload.title })
    }

    @Test fun snoozeTargets() {
        val tomorrow = snoozeTomorrow(now, utc)
        assertEquals(Triple(1405, 8, 30), gregorianToJalali(2026, 11, 21))
        assertEquals(java.time.LocalDateTime.of(2026, 11, 21, 9, 0).toInstant(utc).toEpochMilli(), tomorrow)
        val nextWeek = snoozeNextWeek(now, utc)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 27, 9, 0).toInstant(utc).toEpochMilli(), nextWeek)
    }

    // ── جست‌وجو و برچسب مشترک ──

    @Test fun oneTagFindsVaultNoteAndTaskTogether() {
        val vault = VaultItem("v1", 1, ItemPayload(kind = ItemKind.Login, title = "بانک ملی", tags = listOf("بانک")))
        val n = note()
        val t = VaultItem("t1", 1, ItemPayload(kind = ItemKind.Task, title = "تماس با بانک", tags = listOf("بانک")))
        val found = itemsWithTag(listOf(vault, n, t), "بانک")
        assertEquals(listOf("v1", "n1", "t1"), found.map { it.id })
        assertTrue(TagHelper.matchesTag(n.payload, "بانک"))
        val searched = searchNotesTasks(listOf(n), listOf(t), "#بانک")
        assertEquals(2, searched.size)
        val byText = searchNotesTasks(listOf(n), listOf(t), "شیر")
        assertEquals(listOf("n1"), byText.map { it.id })
    }

    // ── شمسی ↔ میلادی ──

    @Test fun jalaliGregorianRoundTrip() {
        val samples = listOf(
            Triple(2024, 3, 20) to Triple(1403, 1, 1),
            Triple(2000, 1, 1) to Triple(1378, 10, 11),
            Triple(2024, 3, 19) to Triple(1402, 12, 29),
            Triple(2021, 3, 20) to Triple(1399, 12, 30),
            Triple(2021, 3, 21) to Triple(1400, 1, 1),
            Triple(2026, 11, 20) to Triple(1405, 8, 29),
        )
        for ((g, j) in samples) {
            assertEquals(j, gregorianToJalali(g.first, g.second, g.third))
            assertEquals(g, jalaliToGregorian(j.first, j.second, j.third))
        }
        // رفت‌وبرگشت روزانه برای سه سال کامل
        var date = java.time.LocalDate.of(2024, 1, 1)
        val end = java.time.LocalDate.of(2027, 1, 1)
        while (date < end) {
            val j = gregorianToJalali(date.year, date.monthValue, date.dayOfMonth)
            assertEquals(Triple(date.year, date.monthValue, date.dayOfMonth), jalaliToGregorian(j.first, j.second, j.third))
            date = date.plusDays(1)
        }
    }
}
