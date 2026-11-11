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
import app.solina.passwordmanager.data.DemoData
import app.solina.passwordmanager.data.ItemFlag
import app.solina.passwordmanager.data.ItemKind
import app.solina.passwordmanager.data.TotpEntry
import app.solina.passwordmanager.data.VaultItem
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

private fun codeFor(e: TotpEntry, now: Long) = Totp.generate(e.secret, now)

private fun faSecondsLeft(now: Long): String {
    val s = ((Totp.remainingMillis(now) + 999) / 1000).toInt()
    return s.fa()
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

// ───────────────────────── خانه ─────────────────────────

@Composable
fun HomeScreen(onLock: () -> Unit, onOpenItem: (Int) -> Unit, onGoVault: () -> Unit, onGoCodes: () -> Unit, onGoGenerator: () -> Unit) {
    val c = Solina.colors
    val now = rememberNow()
    val items = DemoData.items
    val weak = items.count { it.flag == ItemFlag.Weak }
    val reused = items.count { it.flag == ItemFlag.Reused }
    val score = (100 - weak * 9 - reused * 5).coerceIn(0, 100)

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

        Card(background = c.primaryContainer, padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TotpRing(
                    remainingMillis = (score * 300L).coerceAtLeast(1L),
                    periodSeconds = 30,
                    size = 88.dp,
                    faSeconds = "${score.fa()}٪",
                    color = c.primary,
                    track = c.primary.copy(alpha = 0.2f),
                    textColor = c.onPrimaryContainer,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("سلامت خزانه", color = c.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 13.sp)
                    Text(
                        "${reused.fa()} رمز تکراری و ${weak.fa()} رمز ضعیف دارید.",
                        color = c.onPrimaryContainer,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 24.sp,
                    )
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

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("کدهای من", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("همه", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onGoCodes).padding(8.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DemoData.totp.take(3).forEach { e ->
                    Column(
                        Modifier
                            .width(156.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.surface)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.issuer, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TotpRing(
                                Totp.remainingMillis(now), 30, 34.dp, faSecondsLeft(now),
                                if (Totp.remainingMillis(now) <= 5000) c.secondary else c.primary,
                                c.primary.copy(alpha = 0.2f), c.text,
                            )
                        }
                        Text(
                            Totp.group(codeFor(e, now)),
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("ساخت رمز", SolinaIcons.Key, c.secondaryContainer, c.onSecondaryContainer, Modifier.weight(1f), onGoGenerator)
            QuickAction("افزودن", SolinaIcons.Plus, c.container, c.text, Modifier.weight(1f), onGoVault)
            QuickAction("اسکن QR", SolinaIcons.Qr, c.container, c.text, Modifier.weight(1f), onGoCodes)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("اخیر", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            items.take(3).forEach { item -> ItemRow(item, onClick = { onOpenItem(item.id) }) }
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
private fun ItemRow(item: VaultItem, onClick: () -> Unit) {
    val c = Solina.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (item.kind) {
            ItemKind.Card -> Box(Modifier.size(44.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Card, c.text) }
            ItemKind.Note -> Box(Modifier.size(44.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Note, c.text) }
            ItemKind.Login -> ItemAvatar(item.title)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(item.username, color = c.muted, fontSize = 13.sp, fontFamily = Mono, textAlign = TextAlign.Left)
        }
        when (item.flag) {
            ItemFlag.TwoFa -> Pill("۲FA", c.primaryContainer, c.onPrimaryContainer)
            ItemFlag.Weak -> Pill("ضعیف", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Warning)
            ItemFlag.Reused -> Pill("تکراری", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Copy)
            null -> Unit
        }
    }
}

@Composable
fun VaultScreen(onOpenItem: (Int) -> Unit) {
    val c = Solina.colors
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    val filters = listOf("همه", "ورود", "کارت", "یادداشت", "ضعیف")

    val shown = DemoData.items.filter { item ->
        val byFilter = when (filter) {
            1 -> item.kind == ItemKind.Login
            2 -> item.kind == ItemKind.Card
            3 -> item.kind == ItemKind.Note
            4 -> item.flag == ItemFlag.Weak || item.flag == ItemFlag.Reused
            else -> true
        }
        val q = query.trim()
        byFilter && (q.isEmpty() || item.title.contains(q, ignoreCase = true) || item.username.contains(q, ignoreCase = true) || item.website.contains(q, ignoreCase = true))
    }

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
                if (query.isEmpty()) Text("جست‌وجو در ${DemoData.items.size.fa()} ورودی", color = c.muted, fontSize = 15.sp)
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
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Text("موردی پیدا نشد", color = c.muted, fontSize = 15.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.id }) { item -> ItemRow(item) { onOpenItem(item.id) } }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

// ───────────────────────── جزئیات ورودی ─────────────────────────

@Composable
fun DetailScreen(itemId: Int, onBack: () -> Unit, onToast: (String) -> Unit) {
    val c = Solina.colors
    val ctx = LocalContext.current
    val item = DemoData.item(itemId)
    val now = rememberNow()
    var shown by remember(itemId) { mutableStateOf(false) }
    val totp = DemoData.totp.firstOrNull { it.issuer.equals(item.title, ignoreCase = true) }
    val level = when {
        PasswordGenerator.estimateBits(item.password) >= 90 -> 4
        PasswordGenerator.estimateBits(item.password) >= 60 -> 3
        PasswordGenerator.estimateBits(item.password) >= 40 -> 2
        else -> 1
    }

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
            IconAction(SolinaIcons.Edit, "ویرایش (در نسخهٔ پیش‌نمایش غیرفعال)", tint = c.text, background = c.container) { onToast("ویرایش در نسخهٔ پیش‌نمایش غیرفعال است") }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemAvatar(item.title, 72.dp)
            Text(item.title, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            if (item.website.isNotEmpty()) Text(item.website, color = c.primary, fontSize = 14.sp, fontFamily = Mono)
        }

        Card(padding = 6.dp) {
            Column {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("نام کاربری", color = c.muted, fontSize = 12.sp)
                        Text(item.username, color = c.text, fontSize = 16.sp, fontFamily = Mono, textAlign = TextAlign.Left)
                    }
                    IconAction(SolinaIcons.Copy, "کپی نام کاربری") {
                        copySensitive(ctx, "username", item.username)
                        onToast("نام کاربری کپی شد")
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("رمز عبور", color = c.muted, fontSize = 12.sp)
                        Text(
                            if (shown) item.password else "•".repeat(16),
                            color = c.text,
                            fontSize = 17.sp,
                            fontFamily = Mono,
                            letterSpacing = if (shown) 0.sp else 2.sp,
                            textAlign = TextAlign.Left,
                        )
                        StrengthBar(level, when (level) { 4 -> "خیلی قوی"; 3 -> "قوی"; 2 -> "متوسط"; else -> "ضعیف" })
                    }
                    IconAction(if (shown) SolinaIcons.EyeOff else SolinaIcons.Eye, if (shown) "پنهان کردن رمز" else "نمایش رمز") { shown = !shown }
                    IconAction(SolinaIcons.Copy, "کپی رمز عبور") {
                        copySensitive(ctx, "password", item.password)
                        onToast("رمز کپی شد")
                    }
                }
            }
        }

        if (totp != null) {
            val left = Totp.remainingMillis(now)
            Card(background = c.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("کد یک‌بارمصرف", color = c.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text(Totp.group(codeFor(totp, now)), color = c.onPrimaryContainer, fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Left)
                    }
                    TotpRing(left, 30, 44.dp, faSecondsLeft(now), if (left <= 5000) c.secondary else c.primary, c.primary.copy(alpha = 0.2f), c.onPrimaryContainer)
                    IconAction(SolinaIcons.Copy, "کپی کد", tint = c.onPrimaryContainer) {
                        copySensitive(ctx, "totp", codeFor(totp, now))
                        onToast("کد کپی شد")
                    }
                }
            }
        }

        if (item.note.isNotEmpty()) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("یادداشت", color = c.muted, fontSize = 12.sp)
                    Text(item.note, color = c.text, fontSize = 15.sp, lineHeight = 26.sp)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("ایجاد: ${item.created}", color = c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("آخرین ویرایش: ${item.edited}", color = c.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ───────────────────────── کدها ─────────────────────────

@Composable
fun CodesScreen(onToast: (String) -> Unit) {
    val c = Solina.colors
    val ctx = LocalContext.current
    val now = rememberNow()
    val left = Totp.remainingMillis(now)
    val urgent = left <= 5000

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("کدها")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(DemoData.totp, key = { it.id }) { e ->
                val code = codeFor(e, now)
                val accent = if (urgent) c.secondary else c.primary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (urgent) c.secondaryContainer else c.surface)
                        .clickable(role = Role.Button, onClickLabel = "کپی کد ${e.issuer}") {
                            copySensitive(ctx, "totp", code)
                            onToast("کد ${e.issuer} کپی شد")
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(e.issuer, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(e.account, color = c.muted, fontSize = 13.sp, fontFamily = Mono, textAlign = TextAlign.Left)
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
                                "بعدی: " + Totp.group(Totp.generate(e.secret, now + 30_000L)),
                                color = c.onSecondaryContainer,
                                fontFamily = Mono,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Left,
                            )
                        }
                    }
                    TotpRing(left, 30, 52.dp, faSecondsLeft(now), accent, accent.copy(alpha = 0.2f), c.text)
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
                        onToast("رمز کپی شد. به‌زودی پاک می‌شود.")
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
                    onToast("رمز کپی شد")
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
                    "رمز اصلی، رمزنگاری خزانه، عبارت بازیابی و قفل واقعی‌اند، ولی فهرست رمزها و کدهای داخل برنامه هنوز نمایشی است و ذخیره نمی‌شود. ذخیره‌سازی رمزشدهٔ ورودی‌ها، Autofill و بکاپ هنوز پیاده‌سازی نشده‌اند. رمز واقعی خود را در برنامه وارد نکنید.",
                    color = c.onSecondaryContainer,
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                )
            }
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("آنچه واقعی است", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("• کدهای TOTP طبق RFC 6238 محاسبه می‌شوند (با بردارهای رسمی تست شده‌اند).", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• مولد رمز با SecureRandom کار می‌کند.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• کپی در کلیپ‌بورد با علامت «حساس» انجام می‌شود.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• اسکرین‌شات و پیش‌نمایش اپ‌های اخیر مسدود است.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• اپ هیچ مجوز شبکه‌ای ندارد.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• خزانه با Argon2id و AES-256-GCM ساخته می‌شود و عبارت بازیابی ۲۴ کلمه‌ای (BIP-39) دارد.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Text("• باز کردن با اثر انگشت از Android Keystore استفاده می‌کند و با افزودن اثر انگشت جدید باطل می‌شود.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
            }
        }
        Text("Solina 0.0.2-preview", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}
