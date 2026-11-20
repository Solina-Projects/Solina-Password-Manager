package app.solina.passwordmanager.core.notes

import app.solina.passwordmanager.core.formatJalali
import app.solina.passwordmanager.core.gregorianToJalali
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import app.solina.passwordmanager.core.storage.TagHelper
import app.solina.passwordmanager.core.storage.TaskRepeat
import app.solina.passwordmanager.core.storage.VaultItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * منطق خالص بخش «یادداشت و کارها» (فاز ۳): بدون اندروید، بدون I/O، کاملاً قابل تست.
 * هیچ‌یک از این توابع به محتوای رمزگشایی‌نشده دسترسی ندارند؛ ورودی همیشه
 * payload رمزگشایی‌شده در حافظه است (جست‌وجو فقط بعد از بازشدن قفل).
 */

/** عنوان یادداشت از خط اول بدنه؛ حداکثر ۶۰ حرف. */
fun deriveTitle(body: String): String {
    val first = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    return if (first.length > 60) first.take(60) + "…" else first
}

/** وقوع بعدی یک کار تکراری از روی موعد فعلی (یا «الان» اگر موعد ندارد). */
fun nextOccurrence(repeat: TaskRepeat, fromMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    if (repeat == TaskRepeat.None || fromMillis <= 0) return 0L
    val at = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDateTime()
    val next = when (repeat) {
        TaskRepeat.Daily -> at.plusDays(1)
        TaskRepeat.Weekly -> at.plusDays(7)
        TaskRepeat.Monthly -> at.plusMonths(1)
        TaskRepeat.None -> return 0L
    }
    return next.atZone(zone).toInstant().toEpochMilli()
}

/** آیا این کار عقب‌افتاده است؟ (موعد گذشته و انجام‌نشده) */
fun isOverdue(payload: ItemPayload, now: Long): Boolean =
    payload.kind == ItemKind.Task && !payload.done && payload.dueAt > 0 && payload.dueAt < now

fun overdueCount(tasks: List<VaultItem>, now: Long): Int =
    tasks.count { isOverdue(it.payload, now) }

/** سه‌قلوی شمسی روزِ یک زمان (برای گروه‌بندی). */
fun jalaliDayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Triple<Int, Int, Int> {
    val d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
}

/** کارهای «امروز»: موعد امروز + کارهای سنجاق‌شدهٔ بدون موعد. عقب‌افتاده‌ها اینجا نیستند؛ نمای جدا دارند. */
fun tasksToday(tasks: List<VaultItem>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<VaultItem> {
    val today = jalaliDayOf(now, zone)
    return tasks.filter { item ->
        val p = item.payload
        p.kind == ItemKind.Task && !p.done && !p.archived && !isOverdue(p, now) &&
            (p.dueAt > 0 && jalaliDayOf(p.dueAt, zone) == today || p.dueAt <= 0 && p.pinned)
    }.sortedWith(compareBy<VaultItem> { it.payload.dueAt <= 0 }.thenBy { it.payload.dueAt })
}

/** کارهای عقب‌افتاده، قدیمی‌ترین موعد اول (که هیچ‌وقت گم نشوند). */
fun tasksOverdue(tasks: List<VaultItem>, now: Long): List<VaultItem> =
    tasks.filter { it.payload.kind == ItemKind.Task && !it.payload.done && !it.payload.archived && isOverdue(it.payload, now) }
        .sortedBy { it.payload.dueAt }

/** کارهای آینده (موعد بعد از امروز)، گروه‌بندی‌شده بر اساس روز شمسی، روزها به‌ترتیب. */
fun tasksUpcoming(tasks: List<VaultItem>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, List<VaultItem>>> {
    val today = jalaliDayOf(now, zone)
    val future = tasks.filter { item ->
        val p = item.payload
        p.kind == ItemKind.Task && !p.done && !p.archived && p.dueAt > 0 &&
            !isOverdue(p, now) && jalaliDayOf(p.dueAt, zone) != today
    }.sortedBy { it.payload.dueAt }
    return future.groupBy { formatJalali(it.payload.dueAt, zone) }.entries
        .map { it.key to it.value }.sortedBy { it.second.first().payload.dueAt }
}

/** تعویق یک‌ضربه‌ای: فردا ساعت ۹ صبح (به‌وقت دستگاه). */
fun snoozeTomorrow(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val at = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
    val next = LocalDateTime.of(at.toLocalDate().plusDays(1), java.time.LocalTime.of(9, 0))
    return next.atZone(zone).toInstant().toEpochMilli()
}

/** تعویق یک‌ضربه‌ای: هفتهٔ آینده، همین روز هفته، ساعت ۹ صبح. */
fun snoozeNextWeek(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val at = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
    val next = LocalDateTime.of(at.toLocalDate().plusDays(7), java.time.LocalTime.of(9, 0))
    return next.atZone(zone).toInstant().toEpochMilli()
}

/**
 * تیک «انجام شد»: کار فعلی انجام‌شده می‌شود؛ اگر تکراری باشد، نسخهٔ بعدی
 * (با موعد بعدی و تیک نخورده) هم ساخته می‌شود و به‌عنوان دوم برگردانده می‌شود.
 */
fun completeTask(item: VaultItem, now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<ItemPayload, ItemPayload?> {
    val done = item.payload.copy(done = true, editedAt = now)
    val next = if (item.payload.repeat != TaskRepeat.None) {
        val nextDue = nextOccurrence(item.payload.repeat, item.payload.dueAt.takeIf { it > 0 } ?: now, zone)
        if (nextDue > 0) item.payload.copy(done = false, dueAt = nextDue, createdAt = now, editedAt = now) else null
    } else null
    return done to next
}

/**
 * جست‌وجوی درون‌حافظه‌ای روی یادداشت‌ها و کارها (فقط بعد از بازشدن قفل).
 * پرس‌وجوی «#برچسب» فقط برچسب را می‌سنجد؛ بقیه عنوان، بدنه، برچسب و چک‌لیست را.
 */
fun searchNotesTasks(
    notes: List<VaultItem>,
    tasks: List<VaultItem>,
    query: String,
): List<VaultItem> {
    val q = query.trim()
    if (q.isEmpty()) return notes + tasks
    if (q.startsWith("#")) {
        val tag = q.drop(1)
        return (notes + tasks).filter { TagHelper.matchesTag(it.payload, tag) }
    }
    return (notes + tasks).filter { item ->
        val p = item.payload
        p.title.contains(q, ignoreCase = true) ||
            p.body.contains(q, ignoreCase = true) ||
            p.note.contains(q, ignoreCase = true) ||
            p.tags.any { it.contains(q, ignoreCase = true) } ||
            p.checklist.any { it.text.contains(q, ignoreCase = true) }
    }
}

/** آیا یک برچسب، ورودی خزانه + یادداشت + کار را با هم پیدا می‌کند؟ (برای تست یکپارچگی برچسب‌ها) */
fun itemsWithTag(items: List<VaultItem>, tag: String): List<VaultItem> =
    items.filter { TagHelper.matchesTag(it.payload, tag) }
