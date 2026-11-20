package app.solina.passwordmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.core.Base32
import app.solina.passwordmanager.core.OtpType
import app.solina.passwordmanager.core.PasswordGenerator
import app.solina.passwordmanager.core.Totp
import app.solina.passwordmanager.core.fa
import app.solina.passwordmanager.core.formatJalali
import app.solina.passwordmanager.core.security.AppLanguage
import app.solina.passwordmanager.core.security.AutoLockOption
import app.solina.passwordmanager.core.security.ClipboardClearOption
import app.solina.passwordmanager.core.security.ListDensity
import app.solina.passwordmanager.core.security.ThemeMode
import app.solina.passwordmanager.core.storage.Flag
import app.solina.passwordmanager.core.storage.ChangedField
import app.solina.passwordmanager.core.storage.HistoryDiff
import app.solina.passwordmanager.core.storage.HistoryEntry
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import app.solina.passwordmanager.core.storage.SecurityReport
import app.solina.passwordmanager.core.storage.TagHelper
import app.solina.passwordmanager.core.storage.VaultItem
import app.solina.passwordmanager.core.storage.VaultRepository
import app.solina.passwordmanager.ui.components.AnimatedFilterRow
import app.solina.passwordmanager.ui.components.CLIPBOARD_CLEAR_MILLIS
import app.solina.passwordmanager.ui.components.Card
import app.solina.passwordmanager.ui.components.distinctById
import app.solina.passwordmanager.ui.components.IconAction
import app.solina.passwordmanager.ui.components.ItemAvatar
import app.solina.passwordmanager.ui.components.Mono
import app.solina.passwordmanager.ui.components.Pill
import app.solina.passwordmanager.ui.components.RollingTotpCode
import app.solina.passwordmanager.ui.components.SIcon
import app.solina.passwordmanager.ui.components.StrengthBar
import app.solina.passwordmanager.ui.components.TotpRing
import app.solina.passwordmanager.ui.components.copySensitive
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons
import app.solina.passwordmanager.ui.viewmodel.BreachScanState
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
    runCatching {
        when (p.otpType) {
            OtpType.HOTP -> Totp.generateHotp(it, p.totpCounter, p.totpDigits, "Hmac" + p.totpAlgorithm)
            // استیم (فاز ۴): کد ۵حرفی با الفبای مخصوص، همیشه SHA1.
            OtpType.STEAM -> Totp.generateSteam(it, now + offsetMillis, p.totpPeriod)
            else -> Totp.generate(it, now + offsetMillis, p.totpPeriod, p.totpDigits, "Hmac" + p.totpAlgorithm)
        }
    }.getOrNull()
}

private fun faSecondsLeft(now: Long, period: Int = 30): String {
    val s = ((Totp.remainingMillis(now, period) + 999) / 1000).toInt()
    return s.fa()
}

internal fun strengthLevel(pw: String): Int {
    val bits = PasswordGenerator.effectiveBits(pw)
    return when {
        bits >= 90 -> 4
        bits >= 60 -> 3
        bits >= 40 -> 2
        else -> 1
    }
}

internal fun strengthLabel(level: Int, isFa: Boolean): String = when (level) {
    4 -> if (isFa) "خیلی قوی" else "Very strong"
    3 -> if (isFa) "قوی" else "Strong"
    2 -> if (isFa) "متوسط" else "Medium"
    else -> if (isFa) "ضعیف" else "Weak"
}

/** نام نمایشی فیلد تغییرکرده در تفاوت تاریخچه (فقط نام فیلد، هرگز مقدار). */
private fun changedFieldLabel(field: ChangedField, isFa: Boolean): String = when (field) {
    ChangedField.Title -> if (isFa) "عنوان" else "Title"
    ChangedField.Username -> if (isFa) "نام کاربری" else "Username"
    ChangedField.Password -> if (isFa) "رمز عبور" else "Password"
    ChangedField.Website -> if (isFa) "وب‌سایت" else "Website"
    ChangedField.Note -> if (isFa) "یادداشت" else "Note"
    ChangedField.Category -> if (isFa) "دسته‌بندی" else "Category"
    ChangedField.Tags -> if (isFa) "برچسب‌ها" else "Tags"
    ChangedField.Favorite -> if (isFa) "علاقه‌مندی" else "Favorite"
    ChangedField.Totp -> if (isFa) "کد دوعاملی" else "Two-factor code"
    ChangedField.Card -> if (isFa) "اطلاعات کارت" else "Card details"
    ChangedField.Identity -> if (isFa) "اطلاعات هویت" else "Identity details"
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
    breachState: BreachScanState = BreachScanState(),
    onRunBreachCheck: () -> Unit = {},
    onLock: () -> Unit,
    onOpenItem: (String) -> Unit,
    onGoVault: () -> Unit,
    onGoCodes: () -> Unit,
    onGoGenerator: () -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    showSecrets: Boolean = false,
    onToggleShowSecrets: () -> Unit = {},
    clipboardClearMillis: Long = CLIPBOARD_CLEAR_MILLIS,
    onToast: (String) -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    onOpenTask: (String) -> Unit = {},
    onToggleDone: (VaultItem) -> Unit = {},
) {
    val c = Solina.colors
    val str = Solina.strings
    val now = rememberNow()
    val withTotp = items.filter { secretBytes(it.payload) != null }
    var revealedRecentIds by remember { mutableStateOf(setOf<String>()) }
    // «اخیر»: تازه‌ترین ورودی‌های هر چهار بخش، هر کدام با کارت اختصاصی خودش.
    val recent = items.sortedByDescending { it.payload.editedAt }.take(6)
    val breachedCount = breachState.breachedIds.size
    val hasProblems = report.weak > 0 || report.reused > 0 || report.insecureHttp > 0 || report.oldPasswords > 0 || report.commonPasswords > 0 || breachedCount > 0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle(str.tabHome) }
            Box(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconAction(if (showSecrets) SolinaIcons.EyeOff else SolinaIcons.Eye, if (showSecrets) "پنهان کردن رمزها" else "نمایش رمزها", tint = c.text, background = c.container, onClick = onToggleShowSecrets)
                    IconAction(SolinaIcons.Lock, str.lockVault, tint = c.text, background = c.container, onClick = onLock)
                }
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
            // کارت سلامت، فشرده (حدود نصف ارتفاع قبل): حلقهٔ کوچک + متن وضعیت + دکمه‌ها در ستون کناری.
            Card(background = c.primaryContainer, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TotpRing(
                        remainingMillis = (report.score * 300L).coerceAtLeast(1L),
                        periodSeconds = 30,
                        size = 56.dp,
                        faSeconds = "${report.score.fa()}٪",
                        color = c.primary,
                        track = c.primary.copy(alpha = 0.2f),
                        textColor = c.onPrimaryContainer,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(str.vaultHealth, color = c.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text(
                            if (hasProblems) {
                                buildString {
                                    append("${report.reused.fa()} رمز تکراری و ${report.weak.fa()} رمز ضعیف")
                                    if (report.commonPasswords > 0) append(" و ${report.commonPasswords.fa()} رمز خیلی رایج")
                                    if (report.oldPasswords > 0) append(" و ${report.oldPasswords.fa()} رمز قدیمی")
                                    if (report.insecureHttp > 0) append(" و ${report.insecureHttp.fa()} نشانی HTTP ناامن")
                                    if (breachedCount > 0) append(" و ${breachedCount.fa()} رمز افشاشده")
                                    append(" دارید.")
                                }
                            } else "مشکلی نیست.",
                            color = c.onPrimaryContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp,
                            maxLines = 3,
                        )
                        Text(
                            if (breachState.scanning) str.checkingBreaches
                            else if (breachState.error != null) breachState.error
                            else if (breachState.checkedCount > 0) {
                                if (breachedCount == 0) "${breachState.checkedCount.fa()} رمز بررسی شد — افشایی یافت نشد."
                                else "${breachedCount.fa()} رمز در نشت‌های عمومی یافت شد!"
                            } else "فقط ۵ کاراکتر اول SHA-1 ارسال می‌شود",
                            color = if (breachedCount > 0) c.secondary else c.onPrimaryContainer.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 2,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                        if (hasProblems) {
                            Box(
                                Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(c.primary)
                                    .clickable(role = Role.Button, onClick = onGoVault)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("رفع مشکلات", color = c.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                        }
                        Box(
                            Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.surface)
                                .clickable(enabled = !breachState.scanning, role = Role.Button, onClick = onRunBreachCheck)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (breachState.scanning) "…" else str.checkBreaches,
                                color = c.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            if (withTotp.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("کدهای من", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(str.all, color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onGoCodes).padding(8.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        withTotp.take(3).forEach { item ->
                            val code = codeFor(item.payload, now) ?: return@forEach
                            Column(
                                Modifier
                                    .width(156.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(c.surface)
                                    .clickable(role = Role.Button) { onOpenItem(item.id) }
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.payload.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                                    if (item.payload.otpType == OtpType.HOTP) {
                                        Pill("#${item.payload.totpCounter.toInt().fa()}", c.primaryContainer, c.onPrimaryContainer)
                                    } else {
                                        TotpRing(
                                            Totp.remainingMillis(now, item.payload.totpPeriod), item.payload.totpPeriod, 34.dp, faSecondsLeft(now, item.payload.totpPeriod),
                                            if (Totp.remainingMillis(now, item.payload.totpPeriod) <= 5000) c.secondary else c.primary,
                                            c.primary.copy(alpha = 0.2f), c.text,
                                        )
                                    }
                                }
                                if (showSecrets) {
                                    RollingTotpCode(code, c.primary, 22.sp, modifier = Modifier.fillMaxWidth())
                                } else {
                                    Text(Totp.group("•".repeat(item.payload.totpDigits)), color = c.primary, fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
                                }
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

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("اخیر", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                recent.forEach { item ->
                    val p = item.payload
                    when {
                        p.totpSecret.isNotBlank() -> RecentCodeCard(
                            item = item,
                            now = now,
                            revealed = showSecrets || item.id in revealedRecentIds,
                            onReveal = { revealedRecentIds = revealedRecentIds + item.id },
                            clipboardClearMillis = clipboardClearMillis,
                            onToast = onToast,
                        )
                        p.kind == ItemKind.QuickNote -> RecentNoteCard(item = item, onClick = { onOpenNote(item.id) })
                        p.kind == ItemKind.Task -> RecentTaskCard(item = item, now = now, onToggleDone = { onToggleDone(item) }, onClick = { onOpenTask(item.id) })
                        else -> RecentVaultCard(
                            item = item,
                            flags = report.flags[item.id].orEmpty(),
                            breached = item.id in breachState.breachedIds,
                            onClick = { onOpenItem(item.id) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ───────────────────────── کارت‌های «اخیر» خانه ─────────────────────────
// هر دسته کارت اختصاصی خودش را دارد: خزانه (نام سرویس + نام کاربری، بدون هیچ
// اطلاعات کدی)، کدها (نام + کد فعلی + نوار شمارش معکوس)، یادداشت‌ها (عنوان +
// خلاصه + تاریخ) و کارها (وضعیت انجام + عنوان + موعد).

/** نوار شمارش معکوس اعتبار کد؛ کسر باقی‌مانده از ساعت دیواری حساب می‌شود، پس با بازسازی UI از اول شروع نمی‌شود. */
@Composable
private fun CountdownBar(modifier: Modifier, remainingMillis: Long, periodSeconds: Int, accent: androidx.compose.ui.graphics.Color, track: androidx.compose.ui.graphics.Color) {
    val fraction = (remainingMillis.toFloat() / (periodSeconds * 1000f)).coerceIn(0f, 1f)
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(accent))
    }
}

/** کارت خزانه در «اخیر»: نام سرویس + نام کاربری؛ هیچ اطلاعات کدی نشان نمی‌دهد. */
@Composable
private fun RecentVaultCard(item: VaultItem, flags: Set<Flag>, breached: Boolean, onClick: () -> Unit) {
    val c = Solina.colors
    val str = Solina.strings
    val p = item.payload
    val subtitle = when (p.kind) {
        ItemKind.Login -> p.username.ifBlank { p.website }
        ItemKind.Card -> if (p.cardNumber.length >= 4) "•••• " + p.cardNumber.takeLast(4) else p.bankName.ifBlank { str.kindCard }
        ItemKind.Identity -> p.fullName.ifBlank { p.email.ifBlank { p.phone.ifBlank { str.kindIdentity } } }
        ItemKind.Note -> p.category.ifBlank { "یادداشت امن" }
        ItemKind.QuickNote -> p.category.ifBlank { "یادداشت سریع" }
        ItemKind.Task -> if (p.dueAt > 0) formatJalali(p.dueAt) else str.noDueDate
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.icon.isNotBlank()) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) {
                    Text(p.icon, fontSize = 18.sp)
                }
            } else {
                when (p.kind) {
                    ItemKind.Card -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Card, c.text) }
                    ItemKind.Identity -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Shield, c.onPrimaryContainer) }
                    ItemKind.Note -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Note, c.text) }
                    ItemKind.QuickNote -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Note, c.text) }
                    ItemKind.Task -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Checklist, c.text) }
                    ItemKind.Login -> ItemAvatar(p.title.ifBlank { "?" }, 40.dp, website = p.website)
                }
            }
            if (p.favorite) Text("★", color = c.primary, fontSize = 13.sp)
            if (breached) Pill(str.breachedBadge, c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Warning)
            else if (Flag.Weak in flags || Flag.Common in flags) Pill("!", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Warning)
            Spacer(Modifier.weight(1f))
            Pill("خزانه", c.primaryContainer, c.onPrimaryContainer)
        }
        Text(p.title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (subtitle.isNotBlank()) Text(subtitle, color = c.muted, fontSize = 12.sp, fontFamily = Mono, textAlign = TextAlign.Left, maxLines = 1)
    }
}

/** کارت کد در «اخیر»: نام سرویس + کد فعلی + نوار شمارش معکوس. لمس: نمایش/کپی کد. */
@Composable
private fun RecentCodeCard(
    item: VaultItem,
    now: Long,
    revealed: Boolean,
    onReveal: () -> Unit,
    clipboardClearMillis: Long,
    onToast: (String) -> Unit,
) {
    val c = Solina.colors
    val ctx = LocalContext.current
    val p = item.payload
    val code = codeFor(p, now) ?: return
    val isHotp = p.otpType == OtpType.HOTP
    val left = if (isHotp) 30_000L else Totp.remainingMillis(now, p.totpPeriod)
    val urgent = !isHotp && left <= 5000
    val accent = if (urgent) c.secondary else c.primary
    val masked = Totp.group("•".repeat(p.totpDigits))
    val copy = { copySensitive(ctx, "otp", code, clipboardClearMillis); onToast("کد ${p.title} کپی شد") }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (urgent) c.secondaryContainer else c.surface)
            .clickable(
                role = Role.Button,
                onClickLabel = if (revealed) "کپی کد ${p.title}" else "نمایش کد ${p.title}",
            ) { if (revealed) copy() else onReveal() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (p.icon.isNotBlank()) Text(p.icon, fontSize = 16.sp)
            Text(p.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            Pill("کد", c.secondaryContainer, c.onSecondaryContainer)
            IconAction(SolinaIcons.Copy, "کپی کد ${p.title}", tint = c.muted) { copy() }
        }
        if (p.username.isNotBlank()) Text(p.username, color = c.muted, fontSize = 12.sp, fontFamily = Mono, textAlign = TextAlign.Left, maxLines = 1)
        if (revealed) {
            RollingTotpCode(code, accent, 20.sp, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth())
        } else {
            Text(masked, color = accent, fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
        }
        if (isHotp) {
            Text("HOTP · شمارنده: ${p.totpCounter.toInt().fa()}", color = c.muted, fontSize = 11.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountdownBar(Modifier.weight(1f), left, p.totpPeriod, accent, accent.copy(alpha = 0.2f))
                Text("${faSecondsLeft(now, p.totpPeriod)} ثانیه", color = c.muted, fontSize = 11.sp)
            }
        }
    }
}

/** کارت یادداشت در «اخیر»: عنوان + خلاصهٔ متن + تاریخ ویرایش. */
@Composable
private fun RecentNoteCard(item: VaultItem, onClick: () -> Unit) {
    val c = Solina.colors
    val p = item.payload
    val title = p.title.ifBlank { p.body.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "یادداشت" } }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SIcon(SolinaIcons.Note, c.text, 18.dp)
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            Pill("یادداشت", c.container, c.muted)
        }
        if (p.body.isNotBlank()) Text(p.body, color = c.muted, fontSize = 13.sp, lineHeight = 20.sp, maxLines = 2)
        Text(formatJalali(p.editedAt), color = c.muted, fontSize = 12.sp)
    }
}

/** کارت کار در «اخیر»: وضعیت انجام + عنوان + موعد. */
@Composable
private fun RecentTaskCard(item: VaultItem, now: Long, onToggleDone: () -> Unit, onClick: () -> Unit) {
    val c = Solina.colors
    val p = item.payload
    val overdue = !p.done && p.dueAt > 0 && p.dueAt < now
    val dueText = when {
        p.dueAt <= 0 -> "بدون مهلت"
        overdue -> "سررسید گذشته · ${formatJalali(p.dueAt)}"
        else -> formatJalali(p.dueAt)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (p.done) c.primary else c.container)
                .border(1.5.dp, if (p.done) c.primary else c.divider, CircleShape)
                .clickable(role = Role.Checkbox) { onToggleDone() },
            contentAlignment = Alignment.Center,
        ) {
            if (p.done) SIcon(SolinaIcons.Check, c.onPrimary, 16.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                p.title.ifBlank { "کار بدون عنوان" },
                color = if (p.done) c.muted else c.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textDecoration = if (p.done) TextDecoration.LineThrough else null,
            )
            Text(dueText, color = if (overdue) c.secondary else c.muted, fontSize = 12.sp)
        }
        Pill("کار", c.container, c.muted)
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
private fun ItemRow(item: VaultItem, flags: Set<Flag>, breached: Boolean = false, compact: Boolean = false, onClick: () -> Unit) {
    val c = Solina.colors
    val str = Solina.strings
    val p = item.payload
    val avatarSize = if (compact) 36.dp else 44.dp
    val rowHeight = if (compact) 52.dp else 64.dp
    val subtitle = when (p.kind) {
        ItemKind.Login -> p.username.ifBlank { p.website }
        ItemKind.Card -> if (p.cardNumber.length >= 4) "•••• " + p.cardNumber.takeLast(4) else p.bankName.ifBlank { str.kindCard }
        ItemKind.Identity -> p.fullName.ifBlank { p.email.ifBlank { p.phone.ifBlank { str.kindIdentity } } }
        ItemKind.Note -> p.category.ifBlank { "یادداشت امن" }
        // یادداشت/کار بخش «یادداشت و کارها» در تب خزانه نشان داده نمی‌شوند، ولی اگر جایی
        // به ItemRow رسیدند، زیرنویس معنادار داشته باشند.
        ItemKind.QuickNote -> p.category.ifBlank { "یادداشت سریع" }
        ItemKind.Task -> if (p.dueAt > 0) formatJalali(p.dueAt) else str.noDueDate
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (p.icon.isNotBlank()) {
            Box(Modifier.size(avatarSize).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) {
                Text(p.icon, fontSize = if (compact) 17.sp else 20.sp)
            }
        } else {
            when (p.kind) {
                ItemKind.Card -> Box(Modifier.size(avatarSize).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Card, c.text) }
                ItemKind.Identity -> Box(Modifier.size(avatarSize).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Shield, c.onPrimaryContainer) }
                ItemKind.Note -> Box(Modifier.size(avatarSize).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Note, c.text) }
                ItemKind.QuickNote -> Box(Modifier.size(avatarSize).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Note, c.text) }
                ItemKind.Task -> Box(Modifier.size(avatarSize).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Checklist, c.text) }
                ItemKind.Login -> ItemAvatar(p.title.ifBlank { "?" }, website = p.website)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.title, color = c.text, fontSize = if (compact) 15.sp else 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                if (p.favorite) Text("★", color = c.primary, fontSize = 13.sp)
            }
            val line2 = buildString {
                if (subtitle.isNotBlank()) append(subtitle)
                if (p.category.isNotBlank() && p.category != subtitle) {
                    if (isNotEmpty()) append(" · ")
                    append(p.category)
                }
            }
            if (line2.isNotBlank()) Text(line2, color = c.muted, fontSize = 13.sp, fontFamily = Mono, textAlign = TextAlign.Left, maxLines = 1)
        }
        when {
            breached -> Pill(str.breachedBadge, c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Warning)
            Flag.Common in flags -> Pill("رایج", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Warning)
            Flag.Weak in flags -> Pill("ضعیف", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Warning)
            Flag.Reused in flags -> Pill("تکراری", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Copy)
            Flag.InsecureHttp in flags -> Pill("HTTP", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Warning)
            Flag.Old in flags -> Pill("قدیمی", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Clock)
        }
    }
}

@Composable
private fun TileCard(item: VaultItem, flags: Set<Flag>, breached: Boolean = false, onClick: () -> Unit) {
    val c = Solina.colors
    val str = Solina.strings
    val p = item.payload
    val subtitle = when (p.kind) {
        ItemKind.Login -> p.username.ifBlank { p.website }
        ItemKind.Card -> if (p.cardNumber.length >= 4) "•••• " + p.cardNumber.takeLast(4) else p.bankName.ifBlank { str.kindCard }
        ItemKind.Identity -> p.fullName.ifBlank { p.email.ifBlank { p.phone.ifBlank { str.kindIdentity } } }
        ItemKind.Note -> p.category.ifBlank { "یادداشت امن" }
        ItemKind.QuickNote -> p.category.ifBlank { "یادداشت سریع" }
        ItemKind.Task -> if (p.dueAt > 0) formatJalali(p.dueAt) else str.noDueDate
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.icon.isNotBlank()) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) {
                    Text(p.icon, fontSize = 18.sp)
                }
            } else {
                when (p.kind) {
                    ItemKind.Card -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Card, c.text) }
                    ItemKind.Identity -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Shield, c.onPrimaryContainer) }
                    ItemKind.Note -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Note, c.text) }
                    ItemKind.QuickNote -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Note, c.text) }
                    ItemKind.Task -> Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) { SIcon(SolinaIcons.Checklist, c.text) }
                    ItemKind.Login -> ItemAvatar(p.title.ifBlank { "?" }, 40.dp, website = p.website)
                }
            }
            if (p.favorite) Text("★", color = c.primary, fontSize = 13.sp)
            if (breached) Pill(str.breachedBadge, c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Warning)
            else if (Flag.Weak in flags || Flag.Common in flags) Pill("!", c.secondaryContainer, c.onSecondaryContainer, SolinaIcons.Warning)
        }
        Text(p.title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (subtitle.isNotBlank()) Text(subtitle, color = c.muted, fontSize = 12.sp, fontFamily = Mono, textAlign = TextAlign.Left, maxLines = 1)
    }
}

@Composable
fun VaultScreen(
    items: List<VaultItem>,
    report: SecurityReport,
    unreadable: Int,
    breachState: BreachScanState = BreachScanState(),
    trashedCount: Int = 0,
    onOpenTrash: () -> Unit = {},
    onOpenItem: (String) -> Unit,
    onAdd: () -> Unit,
    density: ListDensity = ListDensity.Normal,
    showSecrets: Boolean = false,
    onToggleShowSecrets: () -> Unit = {},
) {
    val c = Solina.colors
    val str = Solina.strings
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val filters = listOf(str.all, str.kindLogin, str.kindCard, str.kindIdentity, str.kindNote, str.filterIssues)
    val categories = remember(items) { items.mapNotNull { it.payload.category.trim().ifBlank { null } }.distinct().sorted() }
    val tags = remember(items) { TagHelper.allTags(items) }

    val shown = items.filter { item ->
        val p = item.payload
        // یادداشت/کار بخش «یادداشت و کارها» تب جداگانه دارند و اینجا نشان داده نمی‌شوند.
        if (p.kind == ItemKind.QuickNote || p.kind == ItemKind.Task) return@filter false
        // ورودی «فقط‌کد» (مثل ایمپورت Aegis) متعلق به بخش «کدها» است، نه خزانه.
        if (p.isCodeOnly()) return@filter false
        val flags = report.flags[item.id].orEmpty()
        val breached = item.id in breachState.breachedIds
        val byFilter = when (filter) {
            1 -> p.kind == ItemKind.Login
            2 -> p.kind == ItemKind.Card
            3 -> p.kind == ItemKind.Identity
            4 -> p.kind == ItemKind.Note
            5 -> Flag.Weak in flags || Flag.Reused in flags || Flag.InsecureHttp in flags || Flag.Old in flags || Flag.Common in flags || breached
            else -> true
        }
        val byCat = selectedCategory == null || p.category.trim().equals(selectedCategory, ignoreCase = true)
        val byTag = selectedTag == null || TagHelper.matchesTag(p, selectedTag!!)
        val q = query.trim()
        byFilter && byCat && byTag && (
            q.isEmpty() ||
                p.title.contains(q, ignoreCase = true) ||
                p.username.contains(q, ignoreCase = true) ||
                p.website.contains(q, ignoreCase = true) ||
                p.category.contains(q, ignoreCase = true) ||
                p.tags.any { it.contains(q, ignoreCase = true) } ||
                p.fullName.contains(q, ignoreCase = true) ||
                p.email.contains(q, ignoreCase = true) ||
                p.bankName.contains(q, ignoreCase = true)
            )
    }.sortedByDescending { it.payload.favorite }.distinctById("vault") { it.id }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenTitle(str.tabVault) }
                IconAction(if (showSecrets) SolinaIcons.EyeOff else SolinaIcons.Eye, if (showSecrets) "پنهان کردن رمزها" else "نمایش رمزها", tint = c.text, background = c.container, onClick = onToggleShowSecrets)
            }
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
                    if (query.isEmpty()) Text("${str.searchVault} (${items.size.fa()})", color = c.muted, fontSize = 15.sp)
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
            AnimatedFilterRow(filters, filter) { filter = it }
            if (categories.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allCat = selectedCategory == null
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (allCat) c.primaryContainer else c.container)
                            .clickable(role = Role.RadioButton) { selectedCategory = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(str.all, color = if (allCat) c.onPrimaryContainer else c.muted, fontSize = 12.sp) }
                    categories.forEach { cat ->
                        val active = selectedCategory == cat
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) c.primaryContainer else c.container)
                                .clickable(role = Role.RadioButton) { selectedCategory = if (active) null else cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(cat, color = if (active) c.onPrimaryContainer else c.muted, fontSize = 12.sp) }
                    }
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
            if (unreadable > 0) {
                Card(background = c.secondaryContainer, padding = 12.dp) {
                    Text(
                        "${unreadable.fa()} ورودی رمزگشایی نشد (خراب یا دست‌کاری‌شده). این موارد نمایش داده نمی‌شوند و حذف هم نشده‌اند.",
                        color = c.onSecondaryContainer, fontSize = 13.sp, lineHeight = 22.sp,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.container)
                    .clickable(role = Role.Button, onClick = onOpenTrash)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(str.trashTitle, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (trashedCount > 0) Pill(trashedCount.fa(), c.primaryContainer, c.onPrimaryContainer)
                SIcon(SolinaIcons.ChevronRight, c.muted, 20.dp)
            }
            if (items.isEmpty()) {
                EmptyState("هنوز ورودی‌ای ندارید", "با دکمهٔ + یک رمز، کارت، هویت یا یادداشت امن اضافه کنید.")
            } else if (shown.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text("موردی پیدا نشد", color = c.muted, fontSize = 15.sp)
                }
            } else if (density == ListDensity.Tiles) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(shown, key = { it.id }) { item ->
                        TileCard(
                            item = item,
                            flags = report.flags[item.id].orEmpty(),
                            breached = item.id in breachState.breachedIds,
                            onClick = { onOpenItem(item.id) },
                        )
                    }
                    item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(96.dp)) }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown, key = { it.id }) { item ->
                        ItemRow(
                            item = item,
                            flags = report.flags[item.id].orEmpty(),
                            breached = item.id in breachState.breachedIds,
                            compact = density == ListDensity.Compact,
                            onClick = { onOpenItem(item.id) },
                        )
                    }
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

// ───────────────────────── سطل زباله ─────────────────────────

@Composable
fun TrashScreen(
    items: List<VaultRepository.TrashedItem>,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onPurge: (String) -> Unit,
    onEmptyAll: () -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    var confirmPurgeId by remember { mutableStateOf<String?>(null) }
    var confirmEmptyAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(SolinaIcons.ChevronRight, "بازگشت", tint = c.text, onClick = onBack)
            Text(str.trashTitle, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Text(str.trashEmptyBody, color = c.muted, fontSize = 13.sp, lineHeight = 20.sp)
        val distinct = items.distinctById("trash") { it.id }
        if (distinct.isEmpty()) {
            EmptyState(str.trashEmpty, str.trashEmptyBody)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(distinct, key = { it.id }) { trashed ->
                    val p = trashed.payload
                    val subtitle = when (p.kind) {
                        ItemKind.Login -> p.username.ifBlank { p.website }
                        ItemKind.Card -> if (p.cardNumber.length >= 4) "•••• " + p.cardNumber.takeLast(4) else p.bankName
                        ItemKind.Identity -> p.fullName.ifBlank { p.email.ifBlank { p.phone } }
                        ItemKind.Note -> p.category
                        ItemKind.QuickNote -> p.category.ifBlank { "یادداشت سریع" }
                        ItemKind.Task -> if (p.dueAt > 0) formatJalali(p.dueAt) else ""
                    }
                    Card(padding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ItemAvatar(p.title.ifBlank { "?" }, website = p.website)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(p.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    if (subtitle.isNotBlank()) Text(subtitle, color = c.muted, fontSize = 13.sp, fontFamily = Mono, maxLines = 1)
                                    Text("${str.trashDeletedOn}: ${formatJalali(trashed.deletedAt)}", color = c.muted, fontSize = 12.sp)
                                }
                            }
                            if (confirmPurgeId == trashed.id) {
                                Text(str.trashPurgeConfirm, color = c.onSecondaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlineButton(str.cancel, SolinaIcons.Check, Modifier.weight(1f)) { confirmPurgeId = null }
                                    Box(
                                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.secondary).clickable(role = Role.Button) {
                                            confirmPurgeId = null
                                            onPurge(trashed.id)
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(str.trashDeleteForever, color = c.bg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlineButton(str.trashRestore, SolinaIcons.Refresh, Modifier.weight(1f)) { onRestore(trashed.id) }
                                    OutlineButton(str.trashDeleteForever, SolinaIcons.Warning, Modifier.weight(1f)) { confirmPurgeId = trashed.id }
                                }
                            }
                        }
                    }
                }
                item {
                    if (confirmEmptyAll) {
                        Card(background = c.secondaryContainer) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${str.trashEmptyAll}؟ همهٔ موارد برای همیشه حذف می‌شوند.", color = c.onSecondaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlineButton(str.cancel, SolinaIcons.Check, Modifier.weight(1f)) { confirmEmptyAll = false }
                                    Box(
                                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.secondary).clickable(role = Role.Button) {
                                            confirmEmptyAll = false
                                            onEmptyAll()
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(str.trashEmptyAll, color = c.bg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    } else {
                        Text(
                            str.trashEmptyAll,
                            color = c.secondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { confirmEmptyAll = true }.padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
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
    breachCount: Int = 0,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit = {},
    onRestore: (HistoryEntry) -> Unit,
    onIncrementHotp: () -> Unit = {},
    onToast: (String) -> Unit,
    clipboardClearMillis: Long = CLIPBOARD_CLEAR_MILLIS,
    startShown: Boolean = false,
    onGoCodes: () -> Unit = {},
    passkeySupported: Boolean = false,
    onCreatePasskey: () -> Unit = {},
    onSignInWithPasskey: (app.solina.passwordmanager.core.passkey.PasskeyRecord) -> Unit = {},
) {
    val c = Solina.colors
    val str = Solina.strings
    val ctx = LocalContext.current
    val now = rememberNow()
    var shown by remember(item?.id) { mutableStateOf(startShown) }
    var revealedFields by remember(item?.id) { mutableStateOf(setOf<Int>()) }
    var confirmDelete by remember(item?.id) { mutableStateOf(false) }
    var showHistory by remember(item?.id) { mutableStateOf(false) }

    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val p = item.payload

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(SolinaIcons.ChevronRight, "بازگشت", tint = c.text, onClick = onBack)
            Spacer(Modifier.weight(1f))
            IconAction(SolinaIcons.Edit, "ویرایش", tint = c.text, background = c.container, onClick = onEdit)
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.icon.isNotBlank()) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) {
                    Text(p.icon, fontSize = 34.sp)
                }
            } else {
                ItemAvatar(p.title.ifBlank { "?" }, 72.dp, website = p.website)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.title, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                if (p.favorite) Text("★", color = c.primary, fontSize = 18.sp)
            }
            if (p.website.isNotEmpty()) Text(p.website, color = c.primary, fontSize = 14.sp, fontFamily = Mono)
            if (p.category.isNotBlank()) Pill(p.category, c.container, c.text)
            if (p.tags.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.tags.forEach { tag -> Pill("#$tag", c.primaryContainer, c.onPrimaryContainer) }
                }
            }
        }

        if (breachCount > 0) {
            Card(background = c.secondaryContainer, padding = 14.dp) {
                Text(
                    "هشدار افشا: این رمز عبور ${breachCount.fa()} بار در نشت‌های عمومی دیده شده است. بهتر است هرچه سریع‌تر آن را تغییر دهید.",
                    color = c.onSecondaryContainer,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                )
            }
        }

        when (p.kind) {
            ItemKind.Login -> {
                Card(padding = 6.dp) {
                    Column {
                        if (p.username.isNotEmpty()) {
                            CopyRow("نام کاربری", p.username, copyLabel = "کپی نام کاربری") {
                                copySensitive(ctx, "username", p.username, clipboardClearMillis)
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
                                copySensitive(ctx, "password", p.password, clipboardClearMillis)
                                onToast("رمز کپی شد. ${(clipboardClearMillis / 1000L).toInt().fa()} ثانیهٔ دیگر پاک می‌شود.")
                            }
                        }
                    }
                }
            }
            ItemKind.Card -> {
                Card(padding = 6.dp) {
                    Column {
                        if (p.bankName.isNotEmpty()) {
                            CopyRow("بانک", p.bankName, mono = false, copyLabel = "کپی نام بانک") { copySensitive(ctx, "bank", p.bankName, clipboardClearMillis); onToast("کپی شد") }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        }
                        if (p.cardHolder.isNotEmpty()) {
                            CopyRow("نام دارندهٔ کارت", p.cardHolder, mono = false, copyLabel = "کپی نام دارنده") { copySensitive(ctx, "holder", p.cardHolder, clipboardClearMillis); onToast("کپی شد") }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        }
                        CopyRow("شمارهٔ کارت", if (shown) p.cardNumber else "•••• " + p.cardNumber.takeLast(4), copyLabel = "کپی شمارهٔ کارت") {
                            copySensitive(ctx, "card", p.cardNumber, clipboardClearMillis); onToast("شمارهٔ کارت کپی شد. ${(clipboardClearMillis / 1000L).toInt().fa()} ثانیهٔ دیگر پاک می‌شود.")
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        CopyRow("تاریخ انقضا", p.cardExpiry, copyLabel = "کپی تاریخ انقضا") { copySensitive(ctx, "expiry", p.cardExpiry, clipboardClearMillis); onToast("کپی شد") }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        CopyRow("CVV2", if (shown) p.cardCvv else "•••", copyLabel = "کپی CVV2") { copySensitive(ctx, "cvv", p.cardCvv, clipboardClearMillis); onToast("CVV2 کپی شد. ${(clipboardClearMillis / 1000L).toInt().fa()} ثانیهٔ دیگر پاک می‌شود.") }
                        if (p.cardPin.isNotEmpty()) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                            CopyRow("رمز کارت (PIN)", if (shown) p.cardPin else "••••", copyLabel = "کپی PIN") { copySensitive(ctx, "pin", p.cardPin, clipboardClearMillis); onToast("PIN کپی شد") }
                        }
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
            ItemKind.Identity -> {
                val fields = listOf(
                    Triple("نام و نام خانوادگی", p.fullName, false),
                    Triple("ایمیل", p.email, true),
                    Triple("تلفن", p.phone, true),
                    Triple("کد ملی / شناسه", p.nationalId, true),
                    Triple("سازمان / شرکت", p.company, false),
                    Triple("نشانی", p.address, false),
                    Triple("شهر", p.city, false),
                    Triple("کد پستی", p.postalCode, true),
                    Triple("کشور", p.country, false),
                ).filter { it.second.isNotBlank() }
                if (fields.isNotEmpty()) {
                    Card(padding = 6.dp) {
                        Column {
                            fields.forEachIndexed { idx, (label, value, isMono) ->
                                if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                                CopyRow(label, value, mono = isMono, copyLabel = "کپی $label") {
                                    copySensitive(ctx, label, value, clipboardClearMillis)
                                    onToast("$label کپی شد")
                                }
                            }
                        }
                    }
                }
            }
            ItemKind.Note -> Unit
            // یادداشت/کار بخش جدا دارند و هرگز به صفحهٔ جزئیات خزانه نمی‌رسند.
            ItemKind.QuickNote -> Unit
            ItemKind.Task -> Unit
        }

        // فیلدهای سفارشی (فاز ۴) — مقدار پنهان تا لمس، پوشیده می‌ماند (مثل CVV کارت).
        if (p.customFields.isNotEmpty()) {
            Card(padding = 6.dp) {
                Column {
                    Text(
                        if (str.isFa) "فیلدهای سفارشی" else "Custom fields",
                        color = c.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                    )
                    p.customFields.forEachIndexed { idx, field ->
                        if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                        val revealed = idx in revealedFields || !field.hidden
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(field.label, color = c.muted, fontSize = 12.sp)
                                Text(
                                    if (revealed) field.value else "•".repeat(minOf(12, maxOf(field.value.length, 6))),
                                    color = c.text,
                                    fontSize = 16.sp,
                                    fontFamily = Mono,
                                    textAlign = TextAlign.Left,
                                )
                            }
                            if (field.hidden) {
                                IconAction(if (revealed) SolinaIcons.EyeOff else SolinaIcons.Eye, if (revealed) "پنهان کردن" else "نمایش") {
                                    revealedFields = if (revealed) revealedFields - idx else revealedFields + idx
                                }
                            }
                            IconAction(SolinaIcons.Copy, "کپی ${field.label}") {
                                copySensitive(ctx, field.label, field.value, clipboardClearMillis)
                                onToast("${field.label} کپی شد")
                            }
                        }
                    }
                }
            }
        }

        if (p.kind == ItemKind.Login) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(str.passkeyTitle, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (p.passkeys.isEmpty()) {
                        Text(str.passkeyEmpty, color = c.muted, fontSize = 13.sp)
                    } else {
                        p.passkeys.forEach { pk ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(pk.rpId, color = c.text, fontSize = 14.sp, fontFamily = Mono)
                                Text(
                                    listOfNotNull(
                                        pk.userName.takeIf { it.isNotEmpty() },
                                        "ساخته‌شده: ${formatJalali(pk.createdAt)}",
                                    ).joinToString(" · "),
                                    color = c.muted, fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    if (!passkeySupported) {
                        Text(str.passkeyUnsupported, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlineButton(str.passkeyCreate, SolinaIcons.Key, Modifier.weight(1f), onCreatePasskey)
                            if (p.passkeys.isNotEmpty()) {
                                OutlineButton(str.passkeySignIn, SolinaIcons.Fingerprint, Modifier.weight(1f)) { onSignInWithPasskey(p.passkeys.first()) }
                            }
                        }
                    }
                }
            }
        }

        if (p.totpSecret.isNotBlank()) {
            Card(background = c.primaryContainer, padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("کد دومرحله‌ای این ورودی در بخش «کدها» است.", color = c.onPrimaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (codeFor(p, now) == null) {
                        Text("راز OTP معتبر نیست (Base32 نیست). آن را از «ویرایش» اصلاح کنید.", color = c.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 22.sp)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(c.primary)
                            .clickable(role = Role.Button, onClick = onGoCodes),
                        contentAlignment = Alignment.Center,
                    ) { Text("باز کردن بخش کدها", color = c.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
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
            Text("${str.createdLabel}: ${formatJalali(p.createdAt)}", color = c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${str.editedLabel}: ${formatJalali(p.editedAt)}", color = c.muted, fontSize = 12.sp)
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
                                // تاریخچهٔ کد در خزانه نمایش داده نمی‌شود (تفکیک خزانه/کدها).
                                val changed = HistoryDiff.diffFields(h.payload, p).filter { it != ChangedField.Totp }
                                if (changed.isNotEmpty()) {
                                    Text(
                                        (if (str.isFa) "تغییرات: " else "Changed: ") + changed.joinToString(if (str.isFa) "، " else ", ") { changedFieldLabel(it, str.isFa) },
                                        color = c.muted, fontSize = 12.sp, lineHeight = 18.sp,
                                    )
                                }
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
                    Text("این ورودی به سطل زباله منتقل شود؟ تا ۹۰ روز قابل بازگردانی است.", color = c.onSecondaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton("انصراف", SolinaIcons.Check, Modifier.weight(1f)) { confirmDelete = false }
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.secondary).clickable(role = Role.Button, onClick = onDelete),
                            contentAlignment = Alignment.Center,
                        ) { Text("انتقال به سطل زباله", color = c.bg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        } else {
            Text(
                "تکثیر ورودی (کپی)",
                color = c.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onDuplicate).padding(vertical = 14.dp),
                textAlign = TextAlign.Center,
            )
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

// ───────────────────────── کدها (Authenticator داخلی) ─────────────────────────

@Composable
fun CodesScreen(
    items: List<VaultItem>,
    onToast: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onScan: () -> Unit,
    onAdd: () -> Unit,
    onIncrementHotp: (VaultItem) -> Unit = {},
    onBackupAuth: () -> Unit = {},
    clipboardClearMillis: Long = CLIPBOARD_CLEAR_MILLIS,
    density: ListDensity = ListDensity.Normal,
    showSecrets: Boolean = false,
    onToggleShowSecrets: () -> Unit = {},
) {
    val c = Solina.colors
    val str = Solina.strings
    val ctx = LocalContext.current
    val now = rememberNow()
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    // کدها تا زمان لمس پنهان‌اند (••• •••)؛ هر آیتم جداگانه آشکار می‌شود.
    var revealedIds by remember { mutableStateOf(setOf<String>()) }

    val allOtp = items.filter { codeFor(it.payload, now) != null }
    val categories = remember(allOtp) { allOtp.mapNotNull { it.payload.category.trim().ifBlank { null } }.distinct().sorted() }
    val tags = remember(allOtp) { TagHelper.allTags(allOtp) }
    val withOtp = allOtp.filter { item ->
        val p = item.payload
        val q = query.trim()
        val byCat = selectedCategory == null || p.category.trim().equals(selectedCategory, ignoreCase = true)
        val byTag = selectedTag == null || TagHelper.matchesTag(p, selectedTag!!)
        byCat && byTag && (q.isEmpty() || p.title.contains(q, ignoreCase = true) || p.username.contains(q, ignoreCase = true) || p.category.contains(q, ignoreCase = true) || p.tags.any { it.contains(q, ignoreCase = true) })
    }.sortedByDescending { it.payload.favorite }.distinctById("codes") { it.id }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle(str.tabCodes) }
            Row(Modifier.statusBarsPadding().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allOtp.isNotEmpty()) {
                    IconAction(SolinaIcons.Shield, str.authBackupButton, tint = c.text, background = c.container, onClick = onBackupAuth)
                }
                IconAction(if (showSecrets) SolinaIcons.EyeOff else SolinaIcons.Eye, if (showSecrets) "پنهان کردن کدها" else "نمایش کدها", tint = c.text, background = c.container, onClick = onToggleShowSecrets)
                IconAction(SolinaIcons.Plus, "افزودن دستی کد", tint = c.text, background = c.container, onClick = onAdd)
                IconAction(SolinaIcons.Qr, "اسکن QR", tint = c.onPrimary, background = c.primary, onClick = onScan)
            }
        }
        if (allOtp.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.container)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SIcon(SolinaIcons.Search, c.muted, 18.dp)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(str.searchCodes, color = c.muted, fontSize = 14.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (categories.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allCat = selectedCategory == null
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (allCat) c.primaryContainer else c.container)
                            .clickable(role = Role.RadioButton) { selectedCategory = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(str.all, color = if (allCat) c.onPrimaryContainer else c.muted, fontSize = 12.sp) }
                    categories.forEach { cat ->
                        val active = selectedCategory == cat
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) c.primaryContainer else c.container)
                                .clickable(role = Role.RadioButton) { selectedCategory = if (active) null else cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(cat, color = if (active) c.onPrimaryContainer else c.muted, fontSize = 12.sp) }
                    }
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
        }
        if (allOtp.isEmpty()) {
            EmptyState(
                "هنوز کدی ندارید",
                "هنگام افزودن یا ویرایش یک ورودی، کلید مخفی TOTP یا HOTP (Base32) را وارد کنید یا QR را اسکن کنید تا کدهای ۶ یا ۸ رقمی اینجا نمایش داده شوند.",
            )
        } else if (withOtp.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text("کدی با این فیلتر پیدا نشد", color = c.muted, fontSize = 14.sp)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(withOtp, key = { it.id }) { item ->
                val p = item.payload
                val code = codeFor(p, now) ?: return@items
                val isHotp = p.otpType == OtpType.HOTP
                val left = if (isHotp) 30_000L else Totp.remainingMillis(now, p.totpPeriod)
                val urgent = !isHotp && left <= 5000
                val accent = if (urgent) c.secondary else c.primary
                val revealed = showSecrets || item.id in revealedIds
                val masked = Totp.group("•".repeat(p.totpDigits))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (urgent) c.secondaryContainer else c.surface)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (revealed) "کپی کد ${p.title}" else "نمایش کد ${p.title}",
                        ) {
                            if (revealed) {
                                copySensitive(ctx, "otp", code, clipboardClearMillis)
                                onToast("کد ${p.title} کپی شد")
                            } else {
                                revealedIds = revealedIds + item.id
                            }
                        }
                        .padding(if (density == ListDensity.Compact) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (p.icon.isNotBlank()) Text(p.icon, fontSize = 16.sp)
                            Text(p.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (p.category.isNotBlank()) Pill(p.category, c.container, c.muted)
                        }
                        if (p.username.isNotBlank()) Text(p.username, color = c.muted, fontSize = 12.sp, fontFamily = Mono, textAlign = TextAlign.Left)
                        if (revealed) {
                            RollingTotpCode(code, accent, if (density == ListDensity.Compact) 22.sp else 26.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 4.dp))
                        } else {
                            Text(
                                masked,
                                color = accent,
                                fontFamily = Mono,
                                fontSize = if (density == ListDensity.Compact) 22.sp else 26.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Left,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                        if (urgent) {
                            Text(
                                "بعدی: " + if (revealed) Totp.group(codeFor(p, now, p.totpPeriod * 1000L).orEmpty()) else masked,
                                color = c.onSecondaryContainer,
                                fontFamily = Mono,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Left,
                            )
                        } else if (isHotp) {
                            Text("HOTP · ${str.counterLabel}: ${p.totpCounter.toInt().fa()}", color = c.muted, fontSize = 11.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isHotp) {
                            Box(
                                Modifier
                                    .size(if (density == ListDensity.Compact) 36.dp else 40.dp)
                                    .clip(CircleShape)
                                    .background(c.primaryContainer)
                                    .clickable(role = Role.Button, onClickLabel = str.nextHotpCode) { onIncrementHotp(item) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+1", color = c.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            TotpRing(left, p.totpPeriod, if (density == ListDensity.Compact) 36.dp else 44.dp, faSecondsLeft(now, p.totpPeriod), accent, accent.copy(alpha = 0.2f), c.text)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconAction(SolinaIcons.Copy, "کپی کد ${p.title}", tint = c.muted) {
                                copySensitive(ctx, "otp", code, clipboardClearMillis)
                                onToast("کد ${p.title} کپی شد")
                            }
                            IconAction(SolinaIcons.Edit, "جزئیات و ویرایش ${p.title}", tint = c.muted) { onOpenItem(item.id) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ───────────────────────── مولد رمز ─────────────────────────

@Composable
fun GeneratorScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    clipboardClearMillis: Long = CLIPBOARD_CLEAR_MILLIS,
) {
    val c = Solina.colors
    val ctx = LocalContext.current
    var passphraseMode by remember { mutableStateOf(false) }
    var wordCount by remember { mutableStateOf(5f) }
    var length by remember { mutableStateOf(16f) }
    var upper by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var avoid by remember { mutableStateOf(false) }
    var seed by remember { mutableStateOf(0) }

    val password = remember(passphraseMode, wordCount.toInt(), length.toInt(), upper, lower, digits, symbols, avoid, seed) {
        if (passphraseMode) PasswordGenerator.generatePassphrase(wordCount.toInt())
        else PasswordGenerator.generate(length.toInt(), upper, lower, digits, symbols, avoid)
    }
    val bits = if (passphraseMode) wordCount.toInt() * 11.0 else PasswordGenerator.effectiveBits(password)
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
                        copySensitive(ctx, "generated", password, clipboardClearMillis)
                        onToast("رمز کپی شد. ${(clipboardClearMillis / 1000L).toInt().fa()} ثانیهٔ دیگر پاک می‌شود.")
                    }
                }
            }
        }

        Card(background = c.surface, padding = 18.dp) {
            Column {
                ToggleRow("حالت عبارت عبور (کلمات BIP-39)", passphraseMode) { passphraseMode = it }
                if (passphraseMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تعداد کلمات", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(wordCount.toInt().fa(), color = c.primary, fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = wordCount,
                        onValueChange = { wordCount = it },
                        valueRange = 4f..8f,
                        steps = 3,
                        colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.divider),
                    )
                } else {
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
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.primary)
                .clickable(role = Role.Button) {
                    copySensitive(ctx, "generated", password, clipboardClearMillis)
                    onToast("رمز کپی شد. ${(clipboardClearMillis / 1000L).toInt().fa()} ثانیهٔ دیگر پاک می‌شود.")
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
    pinEnabled: Boolean = false,
    onSetupPin: () -> Unit = {},
    onDisablePin: () -> Unit = {},
    autofillEnabled: Boolean,
    onEnableAutofill: () -> Unit,
    autosaveEnabled: Boolean = true,
    onSetAutosaveEnabled: (Boolean) -> Unit = {},
    screenshotsAllowed: Boolean = false,
    onSetScreenshotsAllowed: (Boolean) -> Unit = {},
    notifSupported: Boolean,
    notifGranted: Boolean,
    onRequestNotifPermission: () -> Unit,
    onOpenData: () -> Unit,
    syncStatus: app.solina.passwordmanager.sync.SyncStatus,
    onSyncConnectCloud: () -> Unit,
    onSyncConnectStorage: () -> Unit,
    onSyncConnectWebDav: () -> Unit = {},
    onSyncNow: () -> Unit,
    onSyncDisconnect: () -> Unit,
    onSetSchedule: (app.solina.passwordmanager.core.sync.BackupSchedule) -> Unit,
    autoLock: AutoLockOption,
    onSetAutoLock: (AutoLockOption) -> Unit,
    clipboardClear: ClipboardClearOption,
    onSetClipboardClear: (ClipboardClearOption) -> Unit,
    themeMode: ThemeMode = ThemeMode.System,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    language: AppLanguage = AppLanguage.FA,
    onSetLanguage: (AppLanguage) -> Unit = {},
    density: ListDensity = ListDensity.Normal,
    onSetDensity: (ListDensity) -> Unit = {},
    externalAccountCount: Int = 0,
    onOpenExternalAccounts: () -> Unit = {},
    onChangeMasterPassword: () -> Unit = {},
    onWipeLocalVault: () -> Unit = {},
    onDiagnostics: () -> Unit,
    onLock: () -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    var confirmWipe by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(str.tabSettings)
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
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(str.pinTitle, color = c.text, fontSize = 15.sp)
                        Text(str.pinSub, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    Switch(
                        checked = pinEnabled,
                        onCheckedChange = { if (it) onSetupPin() else onDisablePin() },
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
                        Text("پر کردن خودکار (Autofill + Inline)", color = c.text, fontSize = 15.sp)
                        Text(
                            if (autofillEnabled) "فعال است (با پشتیبانی Inline روی اندروید ۱۱ به بالا). در Chrome گزینهٔ «Autofill using another service» را هم روشن کنید."
                            else "غیرفعال؛ برای انتخاب Solina Refuge به‌عنوان سرویس بزنید",
                            color = c.muted, fontSize = 12.sp, lineHeight = 18.sp,
                        )
                    }
                    Pill(if (autofillEnabled) "فعال" else "فعال‌سازی", if (autofillEnabled) c.primaryContainer else c.secondaryContainer, if (autofillEnabled) c.onPrimaryContainer else c.onSecondaryContainer)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(str.autosaveTitle, color = c.text, fontSize = 15.sp)
                        Text(str.autosaveSub, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    Switch(
                        checked = autosaveEnabled,
                        onCheckedChange = onSetAutosaveEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                            uncheckedThumbColor = c.outline, uncheckedTrackColor = c.container, uncheckedBorderColor = c.outline,
                        ),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("اسکرین‌شات", color = c.text, fontSize = 15.sp)
                        Text("وقتی خاموش است، از صفحه عکس و فیلم گرفته نمی‌شود و در پیش‌نمایش اپ‌های اخیر هم محتوا دیده نمی‌شود.", color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    Switch(
                        checked = screenshotsAllowed,
                        onCheckedChange = onSetScreenshotsAllowed,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                            uncheckedThumbColor = c.outline, uncheckedTrackColor = c.container, uncheckedBorderColor = c.outline,
                        ),
                    )
                }
                if (notifSupported) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).clickable(role = Role.Button, onClick = onRequestNotifPermission),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("اعلان خطا", color = c.text, fontSize = 15.sp)
                            Text(
                                if (notifGranted) "فعال است؛ وقتی خطایی ثبت شد نوتیف می‌آید تا گزارش را بخوانید."
                                else "غیرفعال؛ برای اینکه هنگام خطا نوتیف بیاید بزنید",
                                color = c.muted, fontSize = 12.sp, lineHeight = 18.sp,
                            )
                        }
                        Pill(if (notifGranted) "فعال" else "فعال‌سازی", if (notifGranted) c.primaryContainer else c.secondaryContainer, if (notifGranted) c.onPrimaryContainer else c.onSecondaryContainer)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button, onClick = onOpenData),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("بکاپ، بکاپ Authenticator و ایمپورت", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SIcon(SolinaIcons.ChevronRight, c.muted, 20.dp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenExternalAccounts).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(str.externalAccountsTitle, color = c.text, fontSize = 15.sp)
                        Text(str.externalAccountsSub, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    if (externalAccountCount > 0) {
                        Pill("${externalAccountCount.fa()} متصل", c.primaryContainer, c.onPrimaryContainer)
                    } else {
                        SIcon(SolinaIcons.ChevronRight, c.muted, 20.dp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = if (syncStatus.configured) onSyncNow else onSyncConnectCloud).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (syncStatus.configured) "پشتیبان‌گیری / همگام‌سازی اکنون" else "پشتیبان در فضای ابری (SAF)", color = c.text, fontSize = 15.sp)
                        Text(
                            if (!syncStatus.configured) "پوشه‌ای در فضای ابری خودتان (Google Drive، Nextcloud، Syncthing) انتخاب کنید. فقط داده‌های رمزشده نوشته می‌شود."
                            else buildString {
                                val prefix = when (syncStatus.kind) {
                                    "webdav" -> "سرور WebDAV: "
                                    "cloud" -> "فضای ابری: "
                                    else -> "فضای ذخیره‌سازی: "
                                }
                                append(prefix + syncStatus.folderName)
                                if (syncStatus.lastSyncAt > 0) append(" · آخرین: ${app.solina.passwordmanager.core.formatJalali(syncStatus.lastSyncAt)}")
                                if (syncStatus.syncing) append(" · در حال همگام‌سازی…")
                                syncStatus.message?.let { append("\n$it") }
                            },
                            color = c.muted, fontSize = 12.sp, lineHeight = 18.sp,
                        )
                    }
                    if (syncStatus.configured) Pill("همگام", c.primaryContainer, c.onPrimaryContainer)
                    else SIcon(SolinaIcons.ChevronRight, c.muted, 20.dp)
                }
                if (!syncStatus.configured) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSyncConnectWebDav).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(str.webdavSyncTitle, color = c.text, fontSize = 15.sp)
                            Text(str.webdavSyncSub, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        SIcon(SolinaIcons.ChevronRight, c.muted, 20.dp)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSyncConnectStorage).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("پشتیبان در پوشهٔ گوشی / کارت حافظه", color = c.text, fontSize = 15.sp)
                            Text("یک پوشه روی حافظهٔ داخلی یا SD انتخاب کنید؛ تمام داده‌ها قبل از ذخیره رمزگذاری می‌شوند.", color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        SIcon(SolinaIcons.ChevronRight, c.muted, 20.dp)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                    Text("زمان‌بندی پشتیبان خودکار", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    val schedules = listOf(
                        app.solina.passwordmanager.core.sync.BackupSchedule.OnChange to "هر زمان تغییری دادم (پیشنهادی)",
                        app.solina.passwordmanager.core.sync.BackupSchedule.Daily to "روزی یک‌بار",
                        app.solina.passwordmanager.core.sync.BackupSchedule.Weekly to "هفته‌ای یک‌بار",
                        app.solina.passwordmanager.core.sync.BackupSchedule.Manual to "فقط دستی (خودم دکمه بزنم)",
                    )
                    for ((option, label) in schedules) {
                        Row(
                            Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.RadioButton) { onSetSchedule(option) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (syncStatus.schedule == option) Text("●", color = c.primary, fontSize = 14.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clickable(role = Role.Button, onClick = onSyncDisconnect),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text("قطع اتصال همگام‌سازی (فایل‌های ریموت پاک نمی‌شوند)", color = c.secondary, fontSize = 14.sp) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button, onClick = onChangeMasterPassword),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(str.changeMasterTitle, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SIcon(SolinaIcons.Key, c.muted, 20.dp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Text(str.themeTitle, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                for (option in ThemeMode.entries) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.RadioButton) { onSetThemeMode(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.labelFa, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (themeMode == option) Text("●", color = c.primary, fontSize = 14.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Text(str.languageTitle, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                for (option in AppLanguage.entries) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.RadioButton) { onSetLanguage(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.label, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (language == option) Text("●", color = c.primary, fontSize = 14.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Text(str.densityTitle, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                for (option in ListDensity.entries) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.RadioButton) { onSetDensity(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.labelFa, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (density == option) Text("●", color = c.primary, fontSize = 14.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Text("زمان قفل خودکار پس از رفتن اپ به پس‌زمینه", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                for (option in AutoLockOption.entries) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.RadioButton) { onSetAutoLock(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.label, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (autoLock == option) Text("●", color = c.primary, fontSize = 14.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Text("پاک‌سازی خودکار کلیپ‌بورد", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                for (option in ClipboardClearOption.entries) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.RadioButton) { onSetClipboardClear(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.label, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (clipboardClear == option) Text("●", color = c.primary, fontSize = 14.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button, onClick = onDiagnostics),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("گزارش عیب‌یابی", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SIcon(SolinaIcons.ChevronRight, c.muted, 20.dp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button, onClick = onLock),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(str.lockVault, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SIcon(SolinaIcons.Lock, c.muted, 20.dp)
                }
            }
        }

        if (confirmWipe) {
            Card(background = c.secondaryContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(str.wipeVaultTitle, color = c.onSecondaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(str.wipeVaultConfirm, color = c.onSecondaryContainer, fontSize = 13.sp, lineHeight = 22.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton(str.cancel, SolinaIcons.Check, Modifier.weight(1f)) { confirmWipe = false }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(c.secondary)
                                .clickable(role = Role.Button) {
                                    confirmWipe = false
                                    onWipeLocalVault()
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text("پاک‌سازی کامل", color = c.bg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        } else {
            Card {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable(role = Role.Button) { confirmWipe = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(str.wipeVaultTitle, color = c.secondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    SIcon(SolinaIcons.Warning, c.secondary, 20.dp)
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("معماری امنیتی Solina Refuge", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("• رمزگذاری سرتاسری Zero-Knowledge: هر ورودی با کلید تصادفی اختصاصی (DEK) و AES-256-GCM رمز می‌شود؛ کلید اصلی با Argon2id و عبارت بازیابی ۲۴ کلمه‌ای BIP-39 محافظت می‌شود.", color = c.muted, fontSize = 13.sp, lineHeight = 22.sp)
                Text("• Authenticator داخلی: پشتیبانی کامل از TOTP (RFC 6238) و HOTP (RFC 4226) با ۶/۸ رقم، SHA1/256/512، کد ۵حرفی Steam Guard، اسکن QR، ورود از Aegis/2FAS/andOTP/Google Authenticator/Steam (maFile) و بکاپ جداگانهٔ رمزشده.", color = c.muted, fontSize = 13.sp, lineHeight = 22.sp)
                Text("• Autofill مدرن: پشتیبانی از Android Autofill Framework و Inline Suggestions روی اندروید ۱۱ به بالا با تطبیق دقیق دامنه و نام پکیج.", color = c.muted, fontSize = 13.sp, lineHeight = 22.sp)
                Text("• همگام‌سازی اختیاری HTTPS-only: پوشهٔ محلی/ابری (SAF) یا سرور شخصی WebDAV (Nextcloud / OwnCloud) با فایل‌های تغییرناپذیر احرازشده (HMAC-SHA256).", color = c.muted, fontSize = 13.sp, lineHeight = 22.sp)
                Text("• شبکه کاملاً اختیاری و TLS-only (Cleartext مسدود): اینترنت فقط هنگام اجرای دستی بررسی افشا (k-Anonymity)، اتصال WebDAV یا OAuth 2.0 + PKCE استفاده می‌شود.", color = c.muted, fontSize = 13.sp, lineHeight = 22.sp)
                Text("• امنیت محلی: FLAG_SECURE (ضد اسکرین‌شات)، پاک‌سازی خودکار کلیپ‌بورد، قفل خودکار پس‌زمینه، و ذخیرهٔ تنظیمات حساس با Android Keystore.", color = c.muted, fontSize = 13.sp, lineHeight = 22.sp)
            }
        }
        Text("Solina Refuge 2.0.0 · GPL-3.0", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(12.dp))
    }
}
