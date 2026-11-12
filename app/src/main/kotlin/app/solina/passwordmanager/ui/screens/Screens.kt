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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.core.PasswordGenerator
import app.solina.passwordmanager.core.Totp
import app.solina.passwordmanager.core.fa
import app.solina.passwordmanager.core.Base32
import app.solina.passwordmanager.core.formatJalali
import app.solina.passwordmanager.core.storage.Flag
import app.solina.passwordmanager.core.storage.HistoryEntry
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import app.solina.passwordmanager.core.storage.SecurityReport
import app.solina.passwordmanager.core.storage.VaultItem
import app.solina.passwordmanager.ui.components.Card
import app.solina.passwordmanager.ui.components.FilterChip
import app.solina.passwordmanager.ui.components.IconAction
import app.solina.passwordmanager.ui.components.ItemAvatar
import app.solina.passwordmanager.ui.components.Mono
import app.solina.passwordmanager.ui.components.Pill
import app.solina.passwordmanager.ui.components.SIcon
import app.solina.passwordmanager.ui.components.StrengthBar
import app.solina.passwordmanager.ui.components.TotpRing
import app.solina.passwordmanager.ui.components.copySensitive
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons
import kotlinx.coroutines.delay

/** زمان زنده برای کدهای TOTP؛ هر ۲۵۰ میلی‌ثانیه به‌روز می‌شود. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    return now
}


private fun secretBytes(p: ItemPayload): ByteArray? = if (p.totpSecret.isBlank()) null else Base32.decode(p.totpSecret)

private fun codeFor(p: ItemPayload, now: Long, offsetMillis: Long = 0L): String? = secretBytes(p)?.let {
    runCatching { Totp.generate(it, now + offsetMillis, p.totpPeriod, p.totpDigits, "Hmac" + p.totpAlgorithm) }.getOrNull()
}

private fun faSecondsLeft(now: Long, period: Int = 30): String {
    val s = ((Totp.remainingMillis(now, period) + 999) / 1000).toInt()
    return s.fa()
}

private fun strengthLevel(pw: String): Int {
    val bits = PasswordGenerator.estimateBits(pw)
    return when {
        bits >= 90 -> 4
        bits >= 60 -> 3
        bits >= 40 -> 2
        else -> 1
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text,
        color = Solina.colors.text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 12.dp)
            .height(44.dp),
    )
}

@Composable
private fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    val c = Solina.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
            SIcon(SolinaIcons.Shield, c.onPrimaryContainer, 34.dp)
        }
        Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(body, color = c.muted, fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        if (action != null) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.primary)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, color = c.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

// ───────────────────────── خانه ─────────────────────────

@Composable
fun HomeScreen(
    items: List<VaultItem>,
    report: SecurityReport,
    onLock: () -> Unit,
    onOpenItem: (String) -> Unit,
    onGoVault: () -> Unit,
    onGoCodes: () -> Unit,
    onGoGenerator: () -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit,
) {
    val c = Solina.colors
    val now = rememberNow()
    val withTotp = items.filter { secretBytes(it.payload) != null }
    val recent = items.sortedByDescending { it.payload.editedAt }.take(3)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle("خانه") }
            Box(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                IconAction(SolinaIcons.Lock, "قفل کردن خزانه", tint = c.text, background = c.container, onClick = onLock)
            }
        }

        if (items.isEmpty()) {
            EmptyState(
                "خزانهٔ شما خالی است",
                "اولین ورودی را اضافه کنید. همه‌چیز با کلید خزانه رمز می‌شود و فقط روی همین دستگاه می‌ماند.",
                "افزودن اولین ورودی",
                onAdd,
            )
        } else {
            Card(background = c.primaryContainer, padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TotpRing(
                        remainingMillis = (report.score * 300L).coerceAtLeast(1L),
                        periodSeconds = 30,
                        size = 88.dp,
                        faSeconds = "${report.score.fa()}٪",
                        color = c.primary,
                        track = c.primary.copy(alpha = 0.2f),
                        textColor = c.onPrimaryContainer,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("سلامت خزانه", color = c.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 13.sp)
                        Text(
                            if (report.weak == 0 && report.reused == 0) "رمز ضعیف یا تکراری ندارید."
                            else "${report.reused.fa()} رمز تکراری و ${report.weak.fa()} رمز ضعیف دارید.",
                            color = c.onPrimaryContainer,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp,
                        )
                        if (report.weak > 0 || report.reused > 0) {
                            Box(
                                Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(c.primary)
                                    .clickable(role = Role.Button, onClick = onGoVault)
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("رفع مشکلات", color = c.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }

            if (withTotp.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("کدهای من", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("همه", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onGoCodes).padding(8.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        withTotp.take(3).forEach { item ->
                            val code = codeFor(item.payload, now) ?: return@forEach
                            Column(
                                Modifier
                                    .width(156.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(c.surface)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.payload.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                                    TotpRing(
                                        Totp.remainingMillis(now, item.payload.totpPeriod), item.payload.totpPeriod, 34.dp, faSecondsLeft(now, item.payload.totpPeriod),
                                        if (Totp.remainingMillis(now, item.payload.totpPeriod) <= 5000) c.secondary else c.primary,
                                        c.primary.copy(alpha = 0.2f), c.text,
                                    )
                                }
                                Text(
                                    Totp.group(code),
                                    color = c.primary,
                                    fontFamily = Mono,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Left,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("ساخت رمز", SolinaIcons.Key, c.secondaryContainer, c.onSecondaryContainer, Modifier.weight(1f), onGoGenerator)
                QuickAction("افزودن", SolinaIcons.Plus, c.container, c.text, Modifier.weight(1f), onAdd)
                QuickAction("اسکن QR", SolinaIcons.Qr, c.container, c.text, Modifier.weight(1f), onScan)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("اخیر", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                recent.forEach { item -> ItemRow(item, report.flags[item.id].orEmpty(), onClick = { onOpenItem(item.id) }) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun QuickAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(icon, fg, 18.dp)
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ───────────────────────── خزانه ─────────────────────────

@Composable
private fun ItemRow(item: VaultItem, flags: Set<Flag>, onClick: () -> Unit) {
    val c = Solina.colors
    val p = item.payload
    val subtitle = when (p.kind) {
        ItemKind.Login -> p.username
        ItemKind.Card -> if (p.cardNumber.length >= 4) "•••• " + p.cardNumber.takeLast(4) else "کارت"
        ItemKind.Note -> "یادداشت امن"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (p.kind) {
            ItemKind.Card -> Box(Modifier.size(44.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Card, c.text) }
            ItemKind.Note -> Box(Modifier.size(44.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Note, c.text) }
            ItemKind.Login -> ItemAvatar(p.title.ifBlank { "?" })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, color = c.muted, fontSize = 13.sp, fontFamily = Mono, textAlign = TextAlign.Left, maxLines = 1)
        }
        when {
            Flag.Weak in flags -> Pill("ضعیف", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Warning)
            Flag.Reused in flags -> Pill("تکراری", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Copy)
            p.totpSecret.isNotBlank() -> Pill("۲FA", c.primaryContainer, c.onPrimaryContainer)
        }
    }
}

@Composable
fun VaultScreen(
    items: List<VaultItem>,
    report: SecurityReport,
    unreadable: Int,
    onOpenItem: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val c = Solina.colors
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    val filters = listOf("همه", "ورود", "کارت", "یادداشت", "ضعیف")

    val shown = items.filter { item ->
        val p = item.payload
        val flags = report.flags[item.id].orEmpty()
        val byFilter = when (filter) {
            1 -> p.kind == ItemKind.Login
            2 -> p.kind == ItemKind.Card
            3 -> p.kind == ItemKind.Note
            4 -> Flag.Weak in flags || Flag.Reused in flags
            else -> true
        }
        val q = query.trim()
        byFilter && (q.isEmpty() || p.title.contains(q, ignoreCase = true) || p.username.contains(q, ignoreCase = true) || p.website.contains(q, ignoreCase = true))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle("خزانه")
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
                    if (query.isEmpty()) Text("جست‌وجو در ${items.size.fa()} ورودی", color = c.muted, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEachIndexed { i, label -> FilterChip(label, filter == i) { filter = i } }
            }
            if (unreadable > 0) {
                Card(background = c.secondaryContainer, padding = 12.dp) {
                    Text(
                        "${unreadable.fa()} ورودی رمزگشایی نشد (خراب یا دست‌کاری‌شده). این موارد نمایش داده نمی‌شوند و حذف هم نشده‌اند.",
                        color = c.onSecondaryContainer, fontSize = 13.sp, lineHeight = 22.sp,
                    )
                }
            }
            if (items.isEmpty()) {
                EmptyState("هنوز ورودی‌ای ندارید", "با دکمهٔ + یک رمز، کارت یا یادداشت امن اضافه کنید.")
            } else if (shown.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text("موردی پیدا نشد", color = c.muted, fontSize = 15.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown, key = { it.id }) { item -> ItemRow(item, report.flags[item.id].orEmpty()) { onOpenItem(item.id) } }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
                .size(60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(c.primary)
                .clickable(role = Role.Button, onClickLabel = "افزودن ورودی", onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { SIcon(SolinaIcons.Plus, c.onPrimary, 28.dp, "افزودن ورودی") }
    }
}

// ───────────────────────── جزئیات ورودی ─────────────────────────

@Composable
private fun CopyRow(label: String, value: String, mono: Boolean = true, copyLabel: String, onCopy: () -> Unit) {
    val c = Solina.colors
    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = c.muted, fontSize = 12.sp)
            Text(value, color = c.text, fontSize = 16.sp, fontFamily = if (mono) Mono else androidx.compose.ui.text.font.FontFamily.Default, textAlign = if (mono) TextAlign.Left else null)
        }
        IconAction(SolinaIcons.Copy, copyLabel, onClick = onCopy)
    }
}

@Composable
fun DetailScreen(
    item: VaultItem?,
    history: List<HistoryEntry>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: (HistoryEntry) -> Unit,
    onToast: (String) -> Unit,
) {
    val c = Solina.colors
    val ctx = LocalContext.current
    val now = rememberNow()
    var shown by remember(item?.id) { mutableStateOf(false) }
    var confirmDelete by remember(item?.id) { mutableStateOf(false) }
    var showHistory by remember(item?.id) { mutableStateOf(false) }
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val p = item.payload
    val code = codeFor(p, now)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(SolinaIcons.ChevronRight, "بازگشت", tint = c.text, onClick = onBack)
            Box(Modifier.weight(1f))
            IconAction(SolinaIcons.Edit, "ویرایش", tint = c.text, background = c.container, onClick = onEdit)
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemAvatar(p.title.ifBlank { "?" }, 72.dp)
            Text(p.title, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            if (p.website.isNotEmpty()) Text(p.website, color = c.primary, fontSize = 14.sp, fontFamily = Mono)
        }

        when (p.kind) {
            ItemKind.Login -> {
                Card(padding = 6.dp) {
                    Column {
                        if (p.username.isNotEmpty()) {
                            CopyRow("نام کاربری", p.username, copyLabel = "کپی نام کاربری") {
                                copySensitive(ctx, "username", p.username)
                                onToast("نام کاربری کپی شد")
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        }
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("رمز عبور", color = c.muted, fontSize = 12.sp)
                                Text(
                                    if (shown) p.password else "•".repeat(minOf(16, maxOf(p.password.length, 8))),
                                    color = c.text,
                                    fontSize = 17.sp,
                                    fontFamily = Mono,
                                    letterSpacing = if (shown) 0.sp else 2.sp,
                                    textAlign = TextAlign.Left,
                                )
                                if (p.password.isNotEmpty()) {
                                    val level = strengthLevel(p.password)
                                    StrengthBar(level, when (level) { 4 -> "خیلی قوی"; 3 -> "قوی"; 2 -> "متوسط"; else -> "ضعیف" })
                                }
                            }
                            IconAction(if (shown) SolinaIcons.EyeOff else SolinaIcons.Eye, if (shown) "پنهان کردن رمز" else "نمایش رمز") { shown = !shown }
                            IconAction(SolinaIcons.Copy, "کپی رمز عبور") {
                                copySensitive(ctx, "password", p.password)
                                onToast("رمز کپی شد. ۳۰ ثانیهٔ دیگر پاک می‌شود.")
                            }
                        }
                    }
                }
            }
            ItemKind.Card -> {
                Card(padding = 6.dp) {
                    Column {
                        if (p.cardHolder.isNotEmpty()) {
                            CopyRow("نام دارندهٔ کارت", p.cardHolder, mono = false, copyLabel = "کپی نام دارنده") { copySensitive(ctx, "holder", p.cardHolder); onToast("کپی شد") }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        }
                        CopyRow("شمارهٔ کارت", if (shown) p.cardNumber else "•••• " + p.cardNumber.takeLast(4), copyLabel = "کپی شمارهٔ کارت") {
                            copySensitive(ctx, "card", p.cardNumber); onToast("شمارهٔ کارت کپی شد. ۳۰ ثانیهٔ دیگر پاک می‌شود.")
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        CopyRow("تاریخ انقضا", p.cardExpiry, copyLabel = "کپی تاریخ انقضا") { copySensitive(ctx, "expiry", p.cardExpiry); onToast("کپی شد") }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        CopyRow("CVV2", if (shown) p.cardCvv else "•••", copyLabel = "کپی CVV2") { copySensitive(ctx, "cvv", p.cardCvv); onToast("CVV2 کپی شد. ۳۰ ثانیهٔ دیگر پاک می‌شود.") }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).clickable(role = Role.Button) { shown = !shown }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (shown) "پنهان کردن اطلاعات" else "نمایش اطلاعات", color = c.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            ItemKind.Note -> Unit
        }

        if (code != null) {
            val left = Totp.remainingMillis(now, p.totpPeriod)
            Card(background = c.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("کد یک‌بارمصرف", color = c.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text(Totp.group(code), color = c.onPrimaryContainer, fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Left)
                    }
                    TotpRing(left, p.totpPeriod, 44.dp, faSecondsLeft(now, p.totpPeriod), if (left <= 5000) c.secondary else c.primary, c.primary.copy(alpha = 0.2f), c.onPrimaryContainer)
                    IconAction(SolinaIcons.Copy, "کپی کد", tint = c.onPrimaryContainer) {
                        copySensitive(ctx, "totp", code)
                        onToast("کد کپی شد")
                    }
                }
            }
        } else if (p.totpSecret.isNotBlank()) {
            Card(background = c.secondaryContainer, padding = 12.dp) {
                Text("راز TOTP معتبر نیست (Base32 نیست). آن را از «ویرایش» اصلاح کنید.", color = c.onSecondaryContainer, fontSize = 13.sp, lineHeight = 22.sp)
            }
        }

        if (p.note.isNotEmpty()) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("یادداشت", color = c.muted, fontSize = 12.sp)
                    Text(p.note, color = c.text, fontSize = 15.sp, lineHeight = 26.sp)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("ایجاد: ${formatJalali(p.createdAt)}", color = c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("آخرین ویرایش: ${formatJalali(p.editedAt)}", color = c.muted, fontSize = 12.sp)
        }

        if (history.isNotEmpty()) {
            Card(padding = 6.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clickable(role = Role.Button) { showHistory = !showHistory }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("نسخه‌های قبلی (${history.size.fa()})", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(if (showHistory) "بستن" else "نمایش", color = c.primary, fontSize = 14.sp)
                    }
                    if (showHistory) history.forEach { h ->
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("ویرایش ${h.rev.toInt().fa()} · ${formatJalali(h.payload.editedAt)}", color = c.muted, fontSize = 12.sp)
                                Text(h.payload.title, color = c.text, fontSize = 14.sp)
                            }
                            Text(
                                "بازگردانی",
                                color = c.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable(role = Role.Button) { onRestore(h) }.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }

        if (confirmDelete) {
            Card(background = c.secondaryContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("این ورودی و تاریخچه‌اش برای همیشه حذف شود؟", color = c.onSecondaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton("انصراف", SolinaIcons.Check, Modifier.weight(1f)) { confirmDelete = false }
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.secondary).clickable(role = Role.Button, onClick = onDelete),
                            contentAlignment = Alignment.Center,
                        ) { Text("حذف", color = c.bg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        } else {
            Text(
                "حذف ورودی",
                color = c.secondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { confirmDelete = true }.padding(vertical = 14.dp),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ───────────────────────── کدها ─────────────────────────

@Composable
fun CodesScreen(items: List<VaultItem>, onToast: (String) -> Unit, onOpenItem: (String) -> Unit, onScan: () -> Unit) {
    val c = Solina.colors
    val ctx = LocalContext.current
    val now = rememberNow()
    val withTotp = items.filter { codeFor(it.payload, now) != null }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle("کدها") }
            Box(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                IconAction(SolinaIcons.Qr, "اسکن QR", tint = c.onPrimary, background = c.primary, onClick = onScan)
            }
        }
        if (withTotp.isEmpty()) {
            EmptyState(
                "هنوز کدی ندارید",
                "هنگام افزودن یا ویرایش یک ورودی، «راز TOTP» (کلید Base32 که سرویس نشان می‌دهد) را وارد کنید تا کد زنده اینجا بیاید. یا با دکمهٔ اسکن QR بالای صفحه، QR سرویس (یا خروجی Google Authenticator) را بخوانید.",
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(withTotp, key = { it.id }) { item ->
                val p = item.payload
                val code = codeFor(p, now) ?: return@items
                val left = Totp.remainingMillis(now, p.totpPeriod)
                val urgent = left <= 5000
                val accent = if (urgent) c.secondary else c.primary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (urgent) c.secondaryContainer else c.surface)
                        .clickable(role = Role.Button, onClickLabel = "کپی کد ${p.title}") {
                            copySensitive(ctx, "totp", code)
                            onToast("کد ${p.title} کپی شد")
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (p.username.isNotBlank()) Text(p.username, color = c.muted, fontSize = 13.sp, fontFamily = Mono, textAlign = TextAlign.Left)
                        Text(
                            Totp.group(code),
                            color = accent,
                            fontFamily = Mono,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (urgent) {
                            Text(
                                "بعدی: " + Totp.group(codeFor(p, now, p.totpPeriod * 1000L).orEmpty()),
                                color = c.onSecondaryContainer,
                                fontFamily = Mono,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Left,
                            )
                        }
                    }
                    TotpRing(left, p.totpPeriod, 52.dp, faSecondsLeft(now, p.totpPeriod), accent, accent.copy(alpha = 0.2f), c.text)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ───────────────────────── مولد رمز ─────────────────────────

@Composable
fun GeneratorScreen(onBack: () -> Unit, onToast: (String) -> Unit) {
    val c = Solina.colors
    val ctx = LocalContext.current
    var length by remember { mutableStateOf(16f) }
    var upper by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var avoid by remember { mutableStateOf(false) }
    var seed by remember { mutableStateOf(0) }

    val password = remember(length.toInt(), upper, lower, digits, symbols, avoid, seed) {
        PasswordGenerator.generate(length.toInt(), upper, lower, digits, symbols, avoid)
    }
    val bits = PasswordGenerator.estimateBits(password)
    val level = when {
        bits >= 90 -> 4
        bits >= 60 -> 3
        bits >= 40 -> 2
        else -> 1
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(SolinaIcons.ChevronRight, "بازگشت", tint = c.text, onClick = onBack)
            Text("مولد رمز", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Card(background = c.container, padding = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    colored(password, c),
                    fontFamily = Mono,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 38.sp,
                    textAlign = TextAlign.Left,
                    modifier = Modifier.fillMaxWidth(),
                )
                StrengthBar(level, when (level) { 4 -> "خیلی قوی"; 3 -> "قوی"; 2 -> "متوسط"; else -> "ضعیف" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("رمز تازه", SolinaIcons.Refresh, Modifier.weight(1f)) { seed++ }
                    OutlineButton("کپی", SolinaIcons.Copy, Modifier.weight(1f)) {
                        copySensitive(ctx, "generated", password)
                        onToast("رمز کپی شد. ۳۰ ثانیهٔ دیگر پاک می‌شود.")
                    }
                }
            }
        }

        Card(background = c.surface, padding = 18.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("طول رمز", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(length.toInt().toString(), color = c.primary, fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = length,
                    onValueChange = { length = it },
                    valueRange = 8f..64f,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.divider),
                )
                ToggleRow("حروف بزرگ (A-Z)", upper) { upper = it }
                ToggleRow("حروف کوچک (a-z)", lower) { lower = it }
                ToggleRow("اعداد (0-9)", digits) { digits = it }
                ToggleRow("نمادها (!@#)", symbols) { symbols = it }
                ToggleRow("حذف کاراکترهای مبهم (O, 0, l, 1)", avoid) { avoid = it }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.primary)
                .clickable(role = Role.Button) {
                    copySensitive(ctx, "generated", password)
                    onToast("رمز کپی شد. ۳۰ ثانیهٔ دیگر پاک می‌شود.")
                    onBack()
                },
            contentAlignment = Alignment.Center,
        ) { Text("کپی و استفاده", color = c.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))
    }
}

private fun colored(password: String, c: app.solina.passwordmanager.ui.theme.SolinaColors): AnnotatedString =
    buildAnnotatedString {
        for (ch in password) {
            val color = when {
                ch.isDigit() -> c.primary
                ch.isLetter() -> c.text
                else -> c.secondary
            }
            withStyle(SpanStyle(color = color)) { append(ch) }
        }
    }

@Composable
private fun OutlineButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = Solina.colors
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(icon, c.text, 18.dp)
        Text(text, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Solina.colors
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onPrimary,
                checkedTrackColor = c.primary,
                uncheckedThumbColor = c.outline,
                uncheckedTrackColor = c.container,
                uncheckedBorderColor = c.outline,
            ),
        )
    }
}

// ───────────────────────── تنظیمات ─────────────────────────

@Composable
fun SettingsScreen(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    autofillEnabled: Boolean,
    onEnableAutofill: () -> Unit,
    onOpenData: () -> Unit,
    syncStatus: app.solina.passwordmanager.sync.SyncStatus,
    onSyncConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onSyncDisconnect: () -> Unit,
    onLock: () -> Unit,
) {
    val c = Solina.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("تنظیمات")
        Card {
            Column {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("باز کردن با اثر انگشت", color = c.text, fontSize = 15.sp)
                        if (!biometricAvailable) Text("در این دستگاه اثر انگشت فعال یا ثبت نشده است", color = c.muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        enabled = biometricAvailable || biometricEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                            uncheckedThumbColor = c.outline, uncheckedTrackColor = c.container, uncheckedBorderColor = c.outline,
                        ),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clickable(role = Role.Button, onClick = onEnableAutofill),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("پر کردن خودکار (Autofill)", color = c.text, fontSize = 15.sp)
                        Text(
                            if (autofillEnabled) "فعال است. در Chrome گزینهٔ «Autofill using another service» را هم روشن کنید."
                            else "غیرفعال؛ برای انتخاب Solina به‌عنوان سرویس بزنید",
                            color = c.muted, fontSize = 12.sp, lineHeight = 18.sp,
                        )
                    }
                    Pill(if (autofillEnabled) "فعال" else "فعال‌سازی", if (autofillEnabled) c.primaryContainer else c.secondaryContainer, if (autofillEnabled) c.onPrimaryContainer else c.onSecondaryContainer)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button, onClick = onOpenData),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("بکاپ و ایمپورت", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SIcon(SolinaIcons.ChevronRight, c.muted, 20.dp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = if (syncStatus.configured) onSyncNow else onSyncConnect).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (syncStatus.configured) "همگام‌سازی اکنون" else "همگام‌سازی (انتخاب پوشه)", color = c.text, fontSize = 15.sp)
                        Text(
                            if (!syncStatus.configured) "پوشه‌ای را انتخاب کنید که با Syncthing، Drive، Nextcloud یا ... بین دستگاه‌هایتان همگام می‌شود. فقط داده‌های رمزشده در آن نوشته می‌شود."
                            else buildString {
                                append("پوشه: ${syncStatus.folderName}")
                                if (syncStatus.lastSyncAt > 0) append(" · آخرین: ${app.solina.passwordmanager.core.formatJalali(syncStatus.lastSyncAt)}")
                                if (syncStatus.syncing) append(" · در حال همگام‌سازی…")
                                syncStatus.message?.let { append("\n$it") }
                            },
                            color = c.muted, fontSize = 12.sp, lineHeight = 18.sp,
                        )
                    }
                }
                if (syncStatus.configured) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clickable(role = Role.Button, onClick = onSyncDisconnect),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text("قطع اتصال همگام‌سازی (فایل‌های پوشه پاک نمی‌شوند)", color = c.secondary, fontSize = 14.sp) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button, onClick = onLock),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("قفل کردن خزانه", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SIcon(SolinaIcons.Lock, c.muted, 20.dp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Text("قفل خودکار: یک دقیقه پس از رفتن اپ به پس‌زمینه", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
        }
        Card(background = c.secondaryContainer) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("نسخهٔ پیش‌نمایش", color = c.onSecondaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "ورودی‌ها اکنون واقعی و رمزشده روی همین دستگاه ذخیره می‌شوند، ولی این نسخه هنوز بازبینی امنیتی مستقل نشده است. Autofill فقط پر کردن را پشتیبانی می‌کند (ذخیرهٔ رمز جدید از Autofill هنوز نیست). همگام‌سازی فقط پوشه‌ای است (WebDAV و Drive هنوز نیستند). فعلاً رمزهای حیاتی خود را در آن نگه ندارید و حتماً کلمات بازیابی را جدا یادداشت کنید.",
                    color = c.onSecondaryContainer,
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                )
            }
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("آنچه واقعی است", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("• هر ورودی با کلید تصادفی خودش و AES-256-GCM رمز می‌شود؛ فقط شناسه و زمان‌ها آشکارند.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• هر ویرایش نسخهٔ قبلی را رمزشده در تاریخچه (تا ۱۰ نسخه) نگه می‌دارد.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• کدهای TOTP طبق RFC 6238 محاسبه می‌شوند (با بردارهای رسمی تست شده‌اند).", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• مولد رمز با SecureRandom کار می‌کند.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• کپی در کلیپ‌بورد با علامت «حساس» انجام می‌شود و بعد از ۳۰ ثانیه پاک می‌شود.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• اسکرین‌شات و پیش‌نمایش اپ‌های اخیر مسدود است.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• Autofill در صفحات وب فقط ورودی‌هایی را پیشنهاد می‌دهد که دامنه‌شان دقیقاً مطابقت دارد (یا زیردامنه است).", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• دوربین فقط هنگام باز بودن صفحهٔ اسکن روشن است؛ تصویری ذخیره یا ارسال نمی‌شود. اسکن از عکس گالری هم ممکن است.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• ورود از Google Authenticator (QR خروجی) و لینک‌های otpauth پشتیبانی می‌شود.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• بکاپ فقط با رمز جداگانهٔ خودتان رمز می‌شود (Argon2id + AES-256-GCM) و بدون مجوز ذخیره‌سازی، از طریق انتخاب‌گر فایل سیستم نوشته می‌شود.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• همگام‌سازی: هر نسخه از هر ورودی یک فایل تغییرناپذیر و احراز‌شده (HMAC) است. در ویرایش هم‌زمان هیچ نسخه‌ای گم نمی‌شود (بازنده در تاریخچه می‌ماند). فایل vault.hdr هم در پوشه است؛ پس رمز اصلی قوی لازم است.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• اپ هیچ مجوز شبکه‌ای ندارد.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• خزانه با Argon2id ساخته می‌شود و عبارت بازیابی ۲۴ کلمه‌ای (BIP-39) دارد.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• باز کردن با اثر انگشت از Android Keystore استفاده می‌کند و با افزودن اثر انگشت جدید باطل می‌شود.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
            }
        }
        Text("Solina 0.0.8-alpha2", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}
