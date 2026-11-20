package app.solina.passwordmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.core.fa
import app.solina.passwordmanager.core.formatJalali
import app.solina.passwordmanager.core.gregorianToJalali
import app.solina.passwordmanager.core.jalaliMonthLength
import app.solina.passwordmanager.core.jalaliToGregorian
import app.solina.passwordmanager.core.notes.deriveTitle
import app.solina.passwordmanager.core.notes.isOverdue
import app.solina.passwordmanager.core.notes.searchNotesTasks
import app.solina.passwordmanager.core.notes.snoozeNextWeek
import app.solina.passwordmanager.core.notes.snoozeTomorrow
import app.solina.passwordmanager.core.notes.tasksOverdue
import app.solina.passwordmanager.core.notes.tasksToday
import app.solina.passwordmanager.core.notes.tasksUpcoming
import app.solina.passwordmanager.core.storage.CheckItem
import app.solina.passwordmanager.core.storage.HistoryEntry
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import app.solina.passwordmanager.core.storage.TagHelper
import app.solina.passwordmanager.core.storage.TaskPriority
import app.solina.passwordmanager.core.storage.TaskRepeat
import app.solina.passwordmanager.core.storage.VaultItem
import app.solina.passwordmanager.ui.components.AnimatedFilterRow
import app.solina.passwordmanager.ui.components.Card
import app.solina.passwordmanager.ui.components.IconAction
import app.solina.passwordmanager.ui.components.Pill
import app.solina.passwordmanager.ui.components.SIcon
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay

private val JALALI_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

private fun jalaliToMillis(jy: Int, jm: Int, jd: Int, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
    val (gy, gm, gd) = jalaliToGregorian(jy, jm, jd)
    return LocalDateTime.of(gy, gm, gd, hour, minute).atZone(zone).toInstant().toEpochMilli()
}

private data class JalaliParts(val jy: Int, val jm: Int, val jd: Int, val hour: Int, val minute: Int)

private fun millisToJalaliParts(millis: Long, zone: ZoneId = ZoneId.systemDefault()): JalaliParts {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val d = at.toLocalDate()
    val (jy, jm, jd) = gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
    return JalaliParts(jy, jm, jd, at.hour, at.minute)
}

/**
 * بخش «یادداشت و کارها» (فاز ۳): یک تب با دو زیربخش یادداشت‌ها و کارها.
 * یادداشت‌ها با همان الگوی خزانه (فهرست + جست‌وجو + دکمهٔ افزودن) ساخته شده‌اند.
 * همهٔ متن‌ها فقط بعد از بازشدن قفل در حافظه رمزگشایی می‌شوند؛ جست‌وجو هم درون‌حافظه‌ای است.
 */
@Composable
fun NotesTasksScreen(
    items: List<VaultItem>,
    overdue: Int,
    newNoteSignal: Int,
    history: List<HistoryEntry>,
    onRequestHistory: (String) -> Unit,
    onRestoreHistory: (String, HistoryEntry) -> Unit,
    onSaveNote: (String?, ItemPayload, (String) -> Unit) -> Unit,
    onSaveTask: (String?, ItemPayload, (String) -> Unit) -> Unit,
    onDeleteItem: (String) -> Unit,
    onToggleDone: (VaultItem) -> Unit,
    onSnoozeTask: (VaultItem, Long) -> Unit,
    onOpenTrash: () -> Unit,
    onToast: (String) -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    var subTab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var taskView by remember { mutableStateOf(0) }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var creatingNote by remember { mutableStateOf(false) }
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    var creatingTask by remember { mutableStateOf(false) }
    var showDoneTasks by remember { mutableStateOf(false) }

    val notes = remember(items) { items.filter { it.payload.kind == ItemKind.QuickNote } }
    val tasks = remember(items) { items.filter { it.payload.kind == ItemKind.Task } }
    val linkables = remember(items) {
        items.filter {
            it.payload.kind == ItemKind.Login || it.payload.kind == ItemKind.Card ||
                it.payload.kind == ItemKind.Identity || it.payload.kind == ItemKind.Note
        }.sortedBy { it.payload.title }
    }
    val tags = remember(items) { TagHelper.allTags(items.filter { it.payload.kind == ItemKind.QuickNote || it.payload.kind == ItemKind.Task }) }
    val now = System.currentTimeMillis()

    // بازشدن ویرایشگر یادداشت سریع از ویجت خانه.
    LaunchedEffect(newNoteSignal) {
        if (newNoteSignal > 0) {
            subTab = 0
            creatingNote = true
            editingNoteId = null
        }
    }

    val editingNote = editingNoteId?.let { id -> notes.firstOrNull { it.id == id } }
    val editingTask = editingTaskId?.let { id -> tasks.firstOrNull { it.id == id } }

    if (creatingNote || editingNote != null) {
        NoteEditor(
            item = editingNote,
            linkables = linkables,
            history = history,
            onRequestHistory = onRequestHistory,
            onRestoreHistory = { entry -> editingNote?.let { onRestoreHistory(it.id, entry) }; creatingNote = false; editingNoteId = null },
            onSave = onSaveNote,
            onDelete = { id -> onDeleteItem(id); creatingNote = false; editingNoteId = null },
            onClose = { creatingNote = false; editingNoteId = null },
            onToast = onToast,
        )
        return
    }
    if (creatingTask || editingTask != null) {
        TaskEditor(
            item = editingTask,
            linkables = linkables,
            notes = notes,
            onSave = onSaveTask,
            onDelete = { id -> onDeleteItem(id); creatingTask = false; editingTaskId = null },
            onToggleDone = { item -> onToggleDone(item); creatingTask = false; editingTaskId = null },
            onSnooze = { item, due -> onSnoozeTask(item, due) },
            onClose = { creatingTask = false; editingTaskId = null },
            onToast = onToast,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Text(
                        str.tabNotesTasks,
                        color = c.text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.statusBarsPadding().padding(top = 12.dp).height(44.dp),
                    )
                }
                Box(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                    IconAction(SolinaIcons.Check, "سطل زباله", tint = c.text, background = c.container, onClick = onOpenTrash)
                }
            }
            AnimatedFilterRow(listOf(str.notesSub, str.tasksSub), subTab) { subTab = it }
            // جست‌وجو (درون‌حافظه‌ای؛ فقط بعد از بازشدن قفل)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(c.container)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SIcon(SolinaIcons.Search, c.muted, 20.dp)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(str.searchNotesTasks, color = c.muted, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (tags.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        val active = selectedTag == tag
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) c.primaryContainer else c.container)
                                .clickable(role = Role.RadioButton) { selectedTag = if (active) null else tag }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text("#$tag", color = if (active) c.onPrimaryContainer else c.muted, fontSize = 12.sp) }
                    }
                }
            }
            if (subTab == 0) {
                NotesList(
                    notes = notes, query = query, selectedTag = selectedTag, showArchived = showArchived,
                    onToggleArchived = { showArchived = !showArchived },
                    onOpen = { editingNoteId = it.id },
                    onAdd = { creatingNote = true },
                    onTogglePin = { item ->
                        onSaveNote(item.id, item.payload.copy(pinned = !item.payload.pinned, editedAt = System.currentTimeMillis())) {}
                    },
                )
            } else {
                TasksList(
                    tasks = tasks, query = query, selectedTag = selectedTag, view = taskView,
                    overdue = overdue, showDone = showDoneTasks,
                    onView = { taskView = it },
                    onToggleDoneView = { showDoneTasks = !showDoneTasks },
                    onOpen = { editingTaskId = it.id },
                    onAdd = { creatingTask = true },
                    onToggleDone = onToggleDone,
                    onSnooze = onSnoozeTask,
                )
            }
        }
        // دکمهٔ بزرگ «+» برای ثبت سریع (حداکثر ۲ ضربه تا ویرایشگر).
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 20.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(c.primary)
                .clickable(role = Role.Button) {
                    if (subTab == 0) creatingNote = true else creatingTask = true
                },
            contentAlignment = Alignment.Center,
        ) { SIcon(SolinaIcons.Plus, c.onPrimary, 28.dp) }
    }
}

@Composable
private fun NotesList(
    notes: List<VaultItem>,
    query: String,
    selectedTag: String?,
    showArchived: Boolean,
    onToggleArchived: () -> Unit,
    onOpen: (VaultItem) -> Unit,
    onAdd: () -> Unit,
    onTogglePin: (VaultItem) -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    val filtered = remember(notes, query, selectedTag, showArchived) {
        val base = searchNotesTasks(notes, emptyList(), query)
        val byTag = if (selectedTag == null) base else base.filter { TagHelper.matchesTag(it.payload, selectedTag) }
        byTag.filter { it.payload.archived == showArchived }
            .sortedWith(compareByDescending<VaultItem> { it.payload.pinned }.thenByDescending { it.payload.editedAt })
    }
    if (filtered.isEmpty() && !showArchived) {
        Column(
            Modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                SIcon(SolinaIcons.Note, c.onPrimaryContainer, 34.dp)
            }
            Text(str.notesEmpty, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(str.notesEmptyBody, color = c.muted, fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.primary)
                    .clickable(role = Role.Button, onClick = onAdd)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(str.newNote, color = c.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showArchived) str.archiveNote else "${filtered.size.fa()} یادداشت",
                color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            Text(
                if (showArchived) str.unarchive else str.archiveNote,
                color = c.primary, fontSize = 13.sp,
                modifier = Modifier.clickable { onToggleArchived() }.padding(6.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { item ->
                val p = item.payload
                val doneCount = p.checklist.count { it.done }
                Card(padding = 14.dp) {
                    Column(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(item) },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                p.title.ifBlank { deriveTitle(p.body).ifBlank { "یادداشت" } },
                                color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (p.pinned) "★ سنجاق‌شده" else "سنجاق",
                                color = if (p.pinned) c.primary else c.muted, fontSize = 12.sp,
                                modifier = Modifier.clickable { onTogglePin(item) }.padding(4.dp),
                            )
                        }
                        val preview = p.body.lineSequence().drop(1).filter { it.isNotBlank() }.take(2).joinToString(" / ")
                        if (preview.isNotBlank()) Text(preview, color = c.muted, fontSize = 13.sp, maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (p.checklist.isNotEmpty()) Pill("${doneCount.fa()}/${p.checklist.size.fa()} چک‌لیست", c.container, c.muted)
                            p.tags.take(3).forEach { Pill("#$it", c.container, c.muted) }
                            Spacer(Modifier.weight(1f))
                            Text(formatJalali(p.editedAt), color = c.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(84.dp)) }
        }
    }
}

@Composable
private fun TasksList(
    tasks: List<VaultItem>,
    query: String,
    selectedTag: String?,
    view: Int,
    overdue: Int,
    showDone: Boolean,
    onView: (Int) -> Unit,
    onToggleDoneView: () -> Unit,
    onOpen: (VaultItem) -> Unit,
    onAdd: () -> Unit,
    onToggleDone: (VaultItem) -> Unit,
    onSnooze: (VaultItem, Long) -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    val now = System.currentTimeMillis()
    val searched = remember(tasks, query, selectedTag) {
        val base = searchNotesTasks(emptyList(), tasks, query)
        if (selectedTag == null) base else base.filter { TagHelper.matchesTag(it.payload, selectedTag) }
    }
    val views = listOf(str.viewToday, str.viewUpcoming, "${str.viewOverdue} (${overdue.fa()})")
    AnimatedFilterRow(views, view) { onView(it) }
    val shown: List<VaultItem> = remember(searched, view, now) {
        when (view) {
            1 -> tasksUpcoming(searched, now).flatMap { it.second }
            2 -> tasksOverdue(searched, now)
            else -> tasksToday(searched, now)
        }
    }
    val upcomingGroups = remember(searched, view, now) {
        if (view == 1) tasksUpcoming(searched, now) else emptyList()
    }
    val doneTasks = remember(searched) { searched.filter { it.payload.done && !it.payload.archived }.sortedByDescending { it.payload.editedAt }.take(10) }
    if (shown.isEmpty() && view != 1) {
        Column(
            Modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                SIcon(SolinaIcons.Checklist, c.onPrimaryContainer, 34.dp)
            }
            Text(
                if (view == 2) "کاری عقب‌افتاده نداری" else str.tasksEmpty,
                color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
            Text(str.tasksEmptyBody, color = c.muted, fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.primary)
                    .clickable(role = Role.Button, onClick = onAdd)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(str.newTask, color = c.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (view == 1) {
                upcomingGroups.forEach { (day, dayTasks) ->
                    item(key = "h-$day") {
                        Text(day, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(dayTasks, key = { it.id }) { item ->
                        TaskRow(item, now, onOpen, onToggleDone, onSnooze)
                    }
                }
            } else {
                items(shown, key = { it.id }) { item ->
                    TaskRow(item, now, onOpen, onToggleDone, onSnooze)
                }
            }
            if (view == 0 && doneTasks.isNotEmpty()) {
                item(key = "done-header") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("انجام‌شده (${doneTasks.size.fa()})", color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            if (showDone) "پنهان" else "نمایش",
                            color = c.primary, fontSize = 13.sp,
                            modifier = Modifier.clickable { onToggleDoneView() }.padding(6.dp),
                        )
                    }
                }
                if (showDone) {
                    items(doneTasks, key = { "done-${it.id}" }) { item ->
                        TaskRow(item, now, onOpen, onToggleDone, onSnooze)
                    }
                }
            }
            item { Spacer(Modifier.height(84.dp)) }
        }
    }
}

@Composable
private fun TaskRow(
    item: VaultItem,
    now: Long,
    onOpen: (VaultItem) -> Unit,
    onToggleDone: (VaultItem) -> Unit,
    onSnooze: (VaultItem, Long) -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    val p = item.payload
    val overdue = isOverdue(p, now)
    Card(background = if (overdue) c.secondaryContainer else c.surface, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Checkbox(
                checked = p.done,
                onCheckedChange = { onToggleDone(item) },
                colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.muted),
            )
            Column(Modifier.weight(1f).clickable(role = Role.Button) { onOpen(item) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    p.title.ifBlank { "کار بدون عنوان" },
                    color = if (p.done) c.muted else c.text,
                    fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (p.dueAt > 0) {
                        Text(
                            formatJalali(p.dueAt),
                            color = if (overdue) c.secondary else c.muted,
                            fontSize = 12.sp, fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                        )
                    } else {
                        Text(str.noDueDate, color = c.muted, fontSize = 12.sp)
                    }
                    val prio = when (p.priority) {
                        TaskPriority.High -> str.priorityHigh
                        TaskPriority.Medium -> str.priorityMedium
                        TaskPriority.Low -> str.priorityLow
                    }
                    Pill(prio, if (p.priority == TaskPriority.High) c.secondaryContainer else c.container, if (p.priority == TaskPriority.High) c.onSecondaryContainer else c.muted)
                    if (p.repeat != TaskRepeat.None) {
                        Pill(
                            when (p.repeat) {
                                TaskRepeat.Daily -> str.repeatDaily
                                TaskRepeat.Weekly -> str.repeatWeekly
                                TaskRepeat.Monthly -> str.repeatMonthly
                                TaskRepeat.None -> ""
                            },
                            c.container, c.muted,
                        )
                    }
                    if (p.pinned) Text("★", color = c.primary, fontSize = 13.sp)
                }
                if (overdue) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            str.snoozeTomorrow, color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onSnooze(item, snoozeTomorrow(now)) }.padding(vertical = 4.dp),
                        )
                        Text(
                            str.snoozeNextWeek, color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onSnooze(item, snoozeNextWeek(now)) }.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = Solina.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = c.muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(c.container)
                    .clickable(role = Role.Button, onClick = onMinus),
                contentAlignment = Alignment.Center,
            ) { Text("−", color = c.text, fontSize = 18.sp) }
            Text(value, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(c.container)
                    .clickable(role = Role.Button, onClick = onPlus),
                contentAlignment = Alignment.Center,
            ) { Text("+", color = c.text, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun ChipButton(text: String, active: Boolean, onClick: () -> Unit) {
    val c = Solina.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) c.primaryContainer else c.container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, color = if (active) c.onPrimaryContainer else c.muted, fontSize = 13.sp) }
}

@Composable
private fun TagEditor(tags: List<String>, onTags: (List<String>) -> Unit) {
    val c = Solina.colors
    val str = Solina.strings
    var input by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(str.tagsLabel, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.primaryContainer)
                        .clickable(role = Role.Button) { onTags(TagHelper.removeTag(tags, tag)) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("#$tag ×", color = c.onPrimaryContainer, fontSize = 12.sp) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.container)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) Text(str.addTagHint, color = c.muted, fontSize = 14.sp)
                BasicTextField(
                    value = input,
                    onValueChange = { v ->
                        if (v.endsWith(",") || v.endsWith("،")) {
                            onTags(TagHelper.parseTags(tags.joinToString(",") + "," + v.dropLast(1)))
                            input = ""
                        } else input = v
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "افزودن", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    val parsed = TagHelper.parseTags(input)
                    if (parsed.isNotEmpty()) {
                        var next = tags
                        parsed.forEach { next = TagHelper.addTag(next, it) }
                        onTags(next)
                        input = ""
                    }
                }.padding(6.dp),
            )
        }
    }
}

@Composable
private fun LinkPicker(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    Box(
        Modifier.fillMaxSize().background(c.bg.copy(alpha = 0.92f)).padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.container)
                    .clickable(role = Role.Button) { onSelect(""); onClose() }.padding(14.dp),
            ) { Text(str.noLink, color = c.muted, fontSize = 15.sp) }
            options.forEach { (id, label) ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (id == selectedId) c.primaryContainer else c.container)
                        .clickable(role = Role.Button) { onSelect(id); onClose() }.padding(14.dp),
                ) {
                    Text(label, color = if (id == selectedId) c.onPrimaryContainer else c.text, fontSize = 15.sp, maxLines = 1)
                }
            }
            Text(str.cancel, color = c.primary, fontSize = 15.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
        }
    }
}

@Composable
private fun NoteEditor(
    item: VaultItem?,
    linkables: List<VaultItem>,
    history: List<HistoryEntry>,
    onRequestHistory: (String) -> Unit,
    onRestoreHistory: (HistoryEntry) -> Unit,
    onSave: (String?, ItemPayload, (String) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    val key = item?.id ?: "new"
    var body by remember(key) { mutableStateOf(item?.payload?.body.orEmpty()) }
    var checklist by remember(key) { mutableStateOf(item?.payload?.checklist.orEmpty()) }
    var tags by remember(key) { mutableStateOf(item?.payload?.tags.orEmpty()) }
    var pinned by remember(key) { mutableStateOf(item?.payload?.pinned ?: false) }
    var archived by remember(key) { mutableStateOf(item?.payload?.archived ?: false) }
    var linkedItemId by remember(key) { mutableStateOf(item?.payload?.linkedItemId.orEmpty()) }
    var currentId by remember(key) { mutableStateOf<String?>(item?.id) }
    var lastSaved by remember(key) { mutableStateOf<ItemPayload?>(item?.payload?.copy(editedAt = 0L)) }
    var saveState by remember(key) { mutableStateOf("") }
    var newCheckText by remember { mutableStateOf("") }
    var showLinkPicker by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(item?.id) {
        if (item != null) onRequestHistory(item.id)
    }

    val draft = ItemPayload(
        kind = ItemKind.QuickNote,
        title = deriveTitle(body).ifBlank { "یادداشت" },
        body = body,
        checklist = checklist.filter { it.text.isNotBlank() },
        tags = tags,
        pinned = pinned,
        archived = archived,
        linkedItemId = linkedItemId,
        createdAt = item?.payload?.createdAt ?: 0L,
        editedAt = 0L,
    )
    val isBlank = body.isBlank() && draft.checklist.isEmpty() && tags.isEmpty()
    // ذخیرهٔ خودکار با تأخیر (بدون دکمهٔ ذخیره): هر تغییر ۶۰۰ میلی‌ثانیه بعد ذخیره می‌شود.
    LaunchedEffect(draft) {
        if (isBlank || draft == lastSaved) return@LaunchedEffect
        delay(600)
        saveState = str.savingAuto
        onSave(currentId, draft) { id -> currentId = id }
        lastSaved = draft
        saveState = str.savedAuto
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(SolinaIcons.ChevronRight, "بازگشت", tint = c.text, onClick = onClose)
                Text(
                    deriveTitle(body).ifBlank { str.newNote },
                    color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f),
                )
                if (saveState.isNotEmpty()) Text(saveState, color = c.muted, fontSize = 12.sp)
            }
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                if (body.isEmpty()) Text(str.noteBodyHint, color = c.muted, fontSize = 16.sp, lineHeight = 26.sp)
                BasicTextField(
                    value = body,
                    onValueChange = { body = it },
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp, lineHeight = 26.sp),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // چک‌لیست داخل یادداشت
            Text("چک‌لیست", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            checklist.forEachIndexed { idx, check ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(
                        checked = check.done,
                        onCheckedChange = { checked ->
                            checklist = checklist.toMutableList().also { it[idx] = check.copy(done = checked) }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.muted),
                    )
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = check.text,
                            onValueChange = { v ->
                                checklist = checklist.toMutableList().also { it[idx] = check.copy(text = v) }
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = if (check.done) c.muted else c.text, fontSize = 15.sp),
                            cursorBrush = SolidColor(c.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        "×", color = c.muted, fontSize = 18.sp,
                        modifier = Modifier.clickable { checklist = checklist.filterIndexed { i, _ -> i != idx } }.padding(6.dp),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.container)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (newCheckText.isEmpty()) Text(str.checklistAdd, color = c.muted, fontSize = 14.sp)
                    BasicTextField(
                        value = newCheckText,
                        onValueChange = { newCheckText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "افزودن", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        val t = newCheckText.trim()
                        if (t.isNotEmpty()) {
                            checklist = checklist + CheckItem(t, false)
                            newCheckText = ""
                        }
                    }.padding(6.dp),
                )
            }
            TagEditor(tags = tags, onTags = { tags = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(if (pinned) "★ سنجاق‌شده" else str.pinNote, pinned) { pinned = !pinned }
                ChipButton(if (archived) str.unarchive else str.archiveNote, archived) { archived = !archived }
            }
            // پیوند به ورودی خزانه
            val linkedTitle = linkables.firstOrNull { it.id == linkedItemId }?.payload?.title
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.container)
                    .clickable(role = Role.Button) { showLinkPicker = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(str.linkVaultItem, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(linkedTitle ?: str.noLink, color = c.muted, fontSize = 13.sp, maxLines = 1)
            }
            if (item != null) {
                Text(
                    "${str.createdLabel}: ${formatJalali(item.payload.createdAt)} · ${str.editedLabel}: ${formatJalali(item.payload.editedAt)}",
                    color = c.muted, fontSize = 12.sp,
                )
                if (history.isNotEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.container)
                            .clickable(role = Role.Button) { showHistory = !showHistory }
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${str.historyTitle} (${history.size.fa()})", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(if (showHistory) "بستن" else "نمایش", color = c.primary, fontSize = 14.sp)
                        }
                    }
                    if (showHistory) {
                        history.forEach { h ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(c.container)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(h.payload.title.ifBlank { "یادداشت" }, color = c.text, fontSize = 14.sp, maxLines = 1)
                                    Text(formatJalali(h.savedAt), color = c.muted, fontSize = 12.sp)
                                }
                                Text(
                                    str.restoreVersion, color = c.primary, fontSize = 13.sp,
                                    modifier = Modifier.clickable { onRestoreHistory(h); onToast("نسخه بازگردانی شد") }.padding(6.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    if (confirmDelete) "برای حذف قطعی، دوباره بزن" else str.deleteToTrash,
                    color = c.secondary, fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        if (confirmDelete) onDelete(item.id) else confirmDelete = true
                    }.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        if (showLinkPicker) {
            LinkPicker(
                title = str.linkVaultItem,
                options = linkables.map { it.id to it.payload.title },
                selectedId = linkedItemId,
                onSelect = { linkedItemId = it },
                onClose = { showLinkPicker = false },
            )
        }
    }
}

@Composable
private fun TaskEditor(
    item: VaultItem?,
    linkables: List<VaultItem>,
    notes: List<VaultItem>,
    onSave: (String?, ItemPayload, (String) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onToggleDone: (VaultItem) -> Unit,
    onSnooze: (VaultItem, Long) -> Unit,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    val key = item?.id ?: "new"
    val now = System.currentTimeMillis()
    val todayParts = millisToJalaliParts(now)
    var title by remember(key) { mutableStateOf(item?.payload?.title.orEmpty()) }
    var desc by remember(key) { mutableStateOf(item?.payload?.body.orEmpty()) }
    var priority by remember(key) { mutableStateOf(item?.payload?.priority ?: TaskPriority.Medium) }
    var repeat by remember(key) { mutableStateOf(item?.payload?.repeat ?: TaskRepeat.None) }
    var hasDue by remember(key) { mutableStateOf((item?.payload?.dueAt ?: 0L) > 0) }
    var jy by remember(key) { mutableStateOf(item?.payload?.dueAt?.takeIf { it > 0 }?.let { millisToJalaliParts(it).jy } ?: todayParts.jy) }
    var jm by remember(key) { mutableStateOf(item?.payload?.dueAt?.takeIf { it > 0 }?.let { millisToJalaliParts(it).jm } ?: todayParts.jm) }
    var jd by remember(key) { mutableStateOf(item?.payload?.dueAt?.takeIf { it > 0 }?.let { millisToJalaliParts(it).jd } ?: todayParts.jd) }
    var hour by remember(key) { mutableStateOf(item?.payload?.dueAt?.takeIf { it > 0 }?.let { millisToJalaliParts(it).hour } ?: 9) }
    var minute by remember(key) { mutableStateOf(item?.payload?.dueAt?.takeIf { it > 0 }?.let { millisToJalaliParts(it).minute } ?: 0) }
    var linkedItemId by remember(key) { mutableStateOf(item?.payload?.linkedItemId.orEmpty()) }
    var linkedNoteId by remember(key) { mutableStateOf(item?.payload?.linkedNoteId.orEmpty()) }
    var currentId by remember(key) { mutableStateOf<String?>(item?.id) }
    var lastSaved by remember(key) { mutableStateOf<ItemPayload?>(item?.payload?.copy(editedAt = 0L)) }
    var saveState by remember(key) { mutableStateOf("") }
    var linkTarget by remember { mutableStateOf(0) } // ۰ هیچی، ۱ خزانه، ۲ یادداشت
    var confirmDelete by remember { mutableStateOf(false) }

    val dayLen = jalaliMonthLength(jy, jm)
    val safeJd = jd.coerceAtMost(dayLen)
    val dueAt = if (hasDue) jalaliToMillis(jy, jm, safeJd, hour, minute) else 0L
    val draft = ItemPayload(
        kind = ItemKind.Task,
        title = title.trim(),
        body = desc,
        priority = priority,
        dueAt = dueAt,
        repeat = repeat,
        done = item?.payload?.done ?: false,
        pinned = item?.payload?.pinned ?: false,
        linkedItemId = linkedItemId,
        linkedNoteId = linkedNoteId,
        createdAt = item?.payload?.createdAt ?: 0L,
        editedAt = 0L,
    )
    val isBlank = title.isBlank() && desc.isBlank()
    LaunchedEffect(draft) {
        if (isBlank || draft == lastSaved) return@LaunchedEffect
        delay(600)
        saveState = str.savingAuto
        onSave(currentId, draft) { id -> currentId = id }
        lastSaved = draft
        saveState = str.savedAuto
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(SolinaIcons.ChevronRight, "بازگشت", tint = c.text, onClick = onClose)
                Text(
                    title.ifBlank { str.newTask },
                    color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f),
                )
                if (saveState.isNotEmpty()) Text(saveState, color = c.muted, fontSize = 12.sp)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(c.container)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (title.isEmpty()) Text(str.taskTitleHint, color = c.muted, fontSize = 16.sp)
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)).background(c.container).padding(14.dp)) {
                if (desc.isEmpty()) Text(str.taskDescHint, color = c.muted, fontSize = 15.sp)
                BasicTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp, lineHeight = 24.sp),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // اولویت
            Text("اولویت", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(str.priorityHigh, priority == TaskPriority.High) { priority = TaskPriority.High }
                ChipButton(str.priorityMedium, priority == TaskPriority.Medium) { priority = TaskPriority.Medium }
                ChipButton(str.priorityLow, priority == TaskPriority.Low) { priority = TaskPriority.Low }
            }
            // تکرار
            Text("تکرار", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(str.repeatNone, repeat == TaskRepeat.None) { repeat = TaskRepeat.None }
                ChipButton(str.repeatDaily, repeat == TaskRepeat.Daily) { repeat = TaskRepeat.Daily }
                ChipButton(str.repeatWeekly, repeat == TaskRepeat.Weekly) { repeat = TaskRepeat.Weekly }
                ChipButton(str.repeatMonthly, repeat == TaskRepeat.Monthly) { repeat = TaskRepeat.Monthly }
            }
            // موعد شمسی + ساعت (اختیاری)
            Text(str.dueDate, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(str.noDueDate, !hasDue) { hasDue = false }
                ChipButton("تعیین موعد", hasDue) { hasDue = true }
            }
            if (hasDue) {
                Card(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stepper("سال", jy.fa(), onMinus = { jy-- }, onPlus = { jy++ })
                            Stepper("ماه", JALALI_MONTHS[jm - 1], onMinus = { jm = if (jm == 1) 12 else jm - 1; if (jm == 12) jy-- }, onPlus = { jm = if (jm == 12) 1 else jm + 1; if (jm == 1) jy++ })
                            Stepper("روز", safeJd.fa(), onMinus = { jd = if (safeJd == 1) dayLen else safeJd - 1 }, onPlus = { jd = if (safeJd == dayLen) 1 else safeJd + 1 })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stepper(str.dueTime + " (ساعت)", hour.fa(), onMinus = { hour = if (hour == 0) 23 else hour - 1 }, onPlus = { hour = if (hour == 23) 0 else hour + 1 })
                            Stepper("دقیقه", minute.fa(), onMinus = { minute = if (minute == 0) 55 else minute - 5 }, onPlus = { minute = if (minute >= 55) 0 else minute + 5 })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipButton("امروز", false) {
                                val t = millisToJalaliParts(now)
                                jy = t.jy; jm = t.jm; jd = t.jd
                            }
                            ChipButton("فردا", false) {
                                val t = millisToJalaliParts(now + 86_400_000L)
                                jy = t.jy; jm = t.jm; jd = t.jd
                            }
                        }
                        Text("موعد: ${formatJalali(dueAt)}، ساعت ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}", color = c.muted, fontSize = 13.sp)
                    }
                }
            }
            // پیوندها
            val linkedVaultTitle = linkables.firstOrNull { it.id == linkedItemId }?.payload?.title
            val linkedNoteTitle = notes.firstOrNull { it.id == linkedNoteId }?.payload?.title
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.container)
                    .clickable(role = Role.Button) { linkTarget = 1 }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(str.linkVaultItem, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(linkedVaultTitle ?: str.noLink, color = c.muted, fontSize = 13.sp, maxLines = 1)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.container)
                    .clickable(role = Role.Button) { linkTarget = 2 }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(str.linkNote, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(linkedNoteTitle ?: str.noLink, color = c.muted, fontSize = 13.sp, maxLines = 1)
            }
            if (item != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!item.payload.done && item.payload.dueAt > 0) {
                        ChipButton(str.snoozeTomorrow, false) { onSnooze(item, snoozeTomorrow(now)) }
                        ChipButton(str.snoozeNextWeek, false) { onSnooze(item, snoozeNextWeek(now)) }
                    }
                    ChipButton(if (item.payload.done) str.reopenTask else str.markDone, false) {
                        onToggleDone(item)
                    }
                }
                Text(
                    if (confirmDelete) "برای حذف قطعی، دوباره بزن" else str.deleteToTrash,
                    color = c.secondary, fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        if (confirmDelete) onDelete(item.id) else confirmDelete = true
                    }.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        if (linkTarget == 1) {
            LinkPicker(
                title = str.linkVaultItem,
                options = linkables.map { it.id to it.payload.title },
                selectedId = linkedItemId,
                onSelect = { linkedItemId = it },
                onClose = { linkTarget = 0 },
            )
        } else if (linkTarget == 2) {
            LinkPicker(
                title = str.linkNote,
                options = notes.map { it.id to (it.payload.title.ifBlank { deriveTitle(it.payload.body).ifBlank { "یادداشت" } }) },
                selectedId = linkedNoteId,
                onSelect = { linkedNoteId = it },
                onClose = { linkTarget = 0 },
            )
        }
    }
}
