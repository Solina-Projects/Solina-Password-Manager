package app.solina.refuge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.refuge.core.backup.Backup
import app.solina.refuge.core.backup.BackupHeaderInfo
import app.solina.refuge.core.security.AppLanguage
import app.solina.refuge.core.security.AutoLockOption
import app.solina.refuge.core.security.ListDensity
import app.solina.refuge.core.security.ThemeMode
import app.solina.refuge.core.fa
import app.solina.refuge.ui.components.IconAction
import app.solina.refuge.ui.components.Mono
import app.solina.refuge.ui.components.SIcon
import app.solina.refuge.ui.components.copySensitive
import app.solina.refuge.ui.theme.Refuge
import app.solina.refuge.ui.theme.RefugeIcons

// ───────────────────────── اجزای مشترک ─────────────────────────

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Refuge.colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun Heading(title: String, body: String) {
    val c = Refuge.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
        Text(body, color = c.muted, fontSize = 15.sp, lineHeight = 26.sp)
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Refuge.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (enabled) c.primary else c.container)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) c.onPrimary else c.muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    val c = Refuge.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(c.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.onPrimaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Refuge.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Password) {
    val c = Refuge.colors
    var visible by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.container)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.muted, fontSize = 12.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 18.sp, fontFamily = Mono, textAlign = TextAlign.Left),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
        IconAction(
            if (visible) RefugeIcons.EyeOff else RefugeIcons.Eye,
            if (visible) "پنهان کردن رمز" else "نمایش رمز",
            tint = c.muted,
        ) { visible = !visible }
    }
}

@Composable
private fun Notice(text: String, error: Boolean = false) {
    val c = Refuge.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (error) c.secondaryContainer else c.primaryContainer)
            .padding(14.dp),
    ) {
        Text(text, color = if (error) c.onSecondaryContainer else c.onPrimaryContainer, fontSize = 14.sp, lineHeight = 24.sp)
    }
}

// ───────────────────────── خوش‌آمدگویی ─────────────────────────

@Composable
private fun WelcomeOptionRow(
    title: String,
    subtitle: String,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Refuge.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = if (highlight) c.primary else c.text,
                fontSize = 15.sp,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            )
            Text(subtitle, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
        SIcon(RefugeIcons.ChevronRight, if (highlight) c.primary else c.muted, 20.dp)
    }
}

@Composable
fun WelcomeScreen(
    message: String? = null,
    busy: Boolean = false,
    onCreate: () -> Unit,
    onRestoreBackupFile: () -> Unit = {},
    onJoinCloud: () -> Unit,
    onJoinStorage: () -> Unit,
    onJoinWebDav: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
) {
    val c = Refuge.colors
    Page {
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(c.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { SIcon(RefugeIcons.Shield, c.primary, 38.dp) }
            Text("Refuge", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "مدیریت رمز عبور و Authenticator با رمزگذاری سرتاسری",
                color = c.muted,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "Argon2id · AES-256-GCM · Zero-Knowledge",
                color = c.muted.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontFamily = Mono,
            )
        }

        if (message != null) Notice(message, error = true)

        PrimaryButton(if (busy) "لطفاً صبر کنید…" else "ساخت خزانهٔ جدید", enabled = !busy, onClick = onCreate)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "بازیابی یا اتصال به خزانهٔ موجود",
                color = c.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.surface),
            ) {
                WelcomeOptionRow(
                    title = "بازیابی از فایل بکاپ (.rfgb)",
                    subtitle = "انتخاب مستقیم فایل بکاپ خزانه یا بکاپ Authenticator",
                    highlight = true,
                    onClick = onRestoreBackupFile,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                WelcomeOptionRow(
                    title = "اتصال به پوشهٔ همگام‌سازی گوشی",
                    subtitle = "انتخاب پوشهٔ Refuge-Sync روی حافظهٔ داخلی یا SD",
                    onClick = onJoinStorage,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                WelcomeOptionRow(
                    title = "اتصال به پوشهٔ فضای ابری",
                    subtitle = "Google Drive، Nextcloud، Syncthing و ارائه‌دهندگان فایل",
                    onClick = onJoinCloud,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                WelcomeOptionRow(
                    title = "اتصال به سرور اختصاصی WebDAV",
                    subtitle = "همگام‌سازی سرتاسری از طریق HTTPS",
                    onClick = onJoinWebDav,
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextLink("گزارش عیب‌یابی", onDiagnostics)
            Text("Refuge 2.0 · GPL-3.0", color = c.muted.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = Mono)
        }
    }
}

@Composable
fun WelcomeRestoreBackupScreen(
    fileName: String,
    headerInfo: BackupHeaderInfo,
    busy: Boolean,
    message: String?,
    onSubmit: (CharArray) -> Unit,
    onCancel: () -> Unit,
) {
    val c = Refuge.colors
    var pw by remember { mutableStateOf("") }
    Page {
        Heading(
            "بازیابی از فایل بکاپ",
            "فایل بکاپ Refuge شناسایی شد. رمزی را که هنگام ساخت این بکاپ انتخاب کرده بودید وارد کنید.",
        )
        Notice(
            buildString {
                if (fileName.isNotBlank()) append("فایل: $fileName\n")
                append("نسخهٔ ${headerInfo.version.fa()} · Argon2id (${(headerInfo.kdf.memoryKiB / 1024).fa()} MiB × ${headerInfo.kdf.iterations.fa()}) · ${Backup.formatByteSize(headerInfo.totalBytes)}")
            },
        )
        SecretField("رمز فایل بکاپ", pw, { pw = it })
        if (message != null) Notice(message, error = true)
        PrimaryButton(
            text = if (busy) "در حال رمزگشایی بکاپ…" else "بازگشایی و ادامه",
            enabled = pw.isNotEmpty() && !busy,
        ) {
            onSubmit(pw.toCharArray())
        }
        TextLink("انصراف و بازگشت", onCancel)
    }
}

// ───────────────────────── ساخت رمز اصلی ─────────────────────────

private const val MIN_MASTER_LENGTH = 10

@Composable
fun CreatePasswordScreen(busy: Boolean, restoreCount: Int = 0, onSubmit: (CharArray) -> Unit) {
    val c = Refuge.colors
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = pass.length < MIN_MASTER_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != pass
    val ok = !tooShort && pass == confirm

    Page {
        Heading(
            "رمز اصلی را بسازید",
            "این تنها رمزی است که باید به خاطر بسپارید. عبارتی طولانی و قابل‌به‌خاطرسپاری بهتر از رمز پیچیده و کوتاه است.",
        )
        if (restoreCount > 0) {
            Notice("${restoreCount.fa()} ورودی از فایل بکاپ رمزگشایی شد و بلافاصله پس از ساخت خزانهٔ جدید وارد می‌شود.")
        }
        SecretField("رمز اصلی", pass, { pass = it })
        SecretField("تکرار رمز اصلی", confirm, { confirm = it })
        Text(
            when {
                pass.isEmpty() -> "حداقل ${MIN_MASTER_LENGTH.fa()} نویسه. مثلاً چند کلمهٔ نامرتبط کنار هم."
                tooShort -> "هنوز کوتاه است (${pass.length.fa()} از ${MIN_MASTER_LENGTH.fa()})."
                pass.length < 14 -> "قابل قبول است؛ طولانی‌تر بهتر است."
                else -> "طول مناسب است."
            },
            color = if (tooShort && pass.isNotEmpty()) c.secondary else c.muted,
            fontSize = 13.sp,
        )
        if (mismatch) Notice("دو رمز یکسان نیستند.", error = true)
        Notice("اگر این رمز را فراموش کنید، فقط عبارت بازیابی ۲۴ کلمه‌ای شما را نجات می‌دهد. هیچ‌کس، حتی ما، نمی‌تواند آن را بازیابی کند.")
        if (busy) Notice("در حال ساخت کلیدها… ممکن است چند ثانیه طول بکشد.")
        PrimaryButton(if (busy) "لطفاً صبر کنید…" else "ادامه", enabled = ok && !busy) { onSubmit(pass.toCharArray()) }
    }
}

// ───────────────────────── عبارت بازیابی ─────────────────────────

@Composable
fun RecoveryWordsScreen(words: List<String>, onNext: () -> Unit) {
    val c = Refuge.colors
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Page {
        Heading(
            "عبارت بازیابی ۲۴ کلمه‌ای شما",
            "این ۲۴ کلمه را با دکمهٔ زیر کپی کنید و در جای امن ذخیره نمایید یا روی کاغذ بنویسید. این عبارت تنها راه بازیابی خزانه در صورت فراموشی رمز اصلی است.",
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.container).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            words.chunked(3).forEachIndexed { row, chunk ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEachIndexed { col, word ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text((row * 3 + col + 1).fa(), color = c.muted, fontSize = 12.sp, modifier = Modifier.width(20.dp))
                            Text(word, color = c.text, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Left)
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.primaryContainer)
                .clickable(role = Role.Button) {
                    copySensitive(ctx, "recovery-words", words.joinToString(" "), 120_000L)
                    copied = true
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SIcon(if (copied) RefugeIcons.Check else RefugeIcons.Copy, c.onPrimaryContainer, 20.dp)
            Text(
                if (copied) "۲۴ کلمه کپی شد (تا ۲ دقیقه در کلیپ‌بورد می‌ماند)" else "کپی ۲۴ کلمه",
                color = c.onPrimaryContainer,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Notice(
            "هر کس این ۲۴ کلمه را داشته باشد می‌تواند خزانهٔ شما را باز کند؛ پس از پیست کردن در محل امن (مثل یادداشت رمزشده یا حافظهٔ آفلاین)، مراقب امنیت آن باشید.",
            error = true,
        )
        PrimaryButton("ذخیره کردم، ادامه", onClick = onNext)
    }
}

@Composable
fun LegacyBackupRestoreScreen(
    itemCount: Int,
    busy: Boolean,
    message: String?,
    onRestoreWithExistingWords: (phrase: String, customPassword: CharArray?) -> Unit,
    onQuickRestoreWithBackupPassword: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = Refuge.colors
    var phrase by remember { mutableStateOf("") }
    var useSamePassword by remember { mutableStateOf(true) }
    var customPass by remember { mutableStateOf("") }
    val canUseWords = phrase.trim().isNotEmpty() && (useSamePassword || customPass.length >= MIN_MASTER_LENGTH)

    Page {
        Heading(
            "تکمیل بازیابی بکاپ (${itemCount.fa()} ورودی)",
            "این فایل بکاپ مربوط به نسخهٔ قبلی است و هدر ۲۴ کلمه را داخل خود ندارد. اگر ۲۴ کلمهٔ قبلی خود را دارید وارد کنید تا دقیقاً همان ۲۴ کلمه و رمز قبلی شما حفظ شود.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("عبارت بازیابی ۲۴ کلمه‌ای قبلی شما", color = c.muted, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.container).padding(16.dp)) {
                BasicTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = Mono, textAlign = TextAlign.Left, lineHeight = 24.sp),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface)
                .clickable(role = Role.Checkbox) { useSamePassword = !useSamePassword }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (useSamePassword) c.primary else c.container),
                contentAlignment = Alignment.Center,
            ) {
                if (useSamePassword) SIcon(RefugeIcons.Check, c.onPrimary, 14.dp)
            }
            Text("از همان رمز فایل بکاپ به‌عنوان رمز اصلی خزانه استفاده شود", color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        if (!useSamePassword) {
            SecretField("رمز اصلی قبلی شما (حداقل ۱۰ نویسه)", customPass, { customPass = it })
        }
        if (message != null) Notice(message, error = true)
        PrimaryButton(
            text = if (busy) "در حال بازیابی خزانه…" else "بازیابی با حفظ ۲۴ کلمهٔ قبلی",
            enabled = canUseWords && !busy,
        ) {
            val pw = if (useSamePassword) null else customPass.toCharArray()
            onRestoreWithExistingWords(phrase, pw)
        }
        SecondaryButton(
            text = if (busy) "لطفاً صبر کنید…" else "۲۴ کلمهٔ قبلی را ندارم (ورود سریع با همین رمز بکاپ)",
            onClick = { if (!busy) onQuickRestoreWithBackupPassword() },
        )
        TextLink("انصراف و بازگشت", onCancel)
    }
}

@Composable
fun ConfirmWordsScreen(words: List<String>, onConfirmed: () -> Unit, onBack: () -> Unit) {
    val c = Refuge.colors
    val indices = remember(words) { (0 until words.size).shuffled().take(3).sorted() }
    val answers = remember(words) { mutableStateOf(listOf("", "", "")) }
    val correct = indices.indices.all { answers.value[it].trim().lowercase() == words[indices[it]] }
    var tried by remember { mutableStateOf(false) }

    Page {
        Heading("تأیید عبارت بازیابی", "برای اطمینان از اینکه درست نوشته‌اید، این سه کلمه را وارد کنید.")
        indices.forEachIndexed { i, wordIndex ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("کلمهٔ شمارهٔ ${(wordIndex + 1).fa()}", color = c.muted, fontSize = 13.sp)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.container).padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    BasicTextField(
                        value = answers.value[i],
                        onValueChange = { v -> answers.value = answers.value.toMutableList().also { it[i] = v } },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 18.sp, fontFamily = Mono, textAlign = TextAlign.Left),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (tried && !correct) Notice("یک یا چند کلمه درست نیست. به عبارت بازیابی برگردید و دوباره بررسی کنید.", error = true)
        PrimaryButton("تأیید و ساخت خزانه") {
            if (correct) onConfirmed() else tried = true
        }
        TextLink("برگشت و دیدن دوبارهٔ کلمات", onBack)
    }
}

// ───────────────────────── پیشنهاد اثر انگشت ─────────────────────────

@Composable
fun BiometricOfferScreen(onEnable: () -> Unit, onSkip: () -> Unit) {
    val c = Refuge.colors
    Page {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { SIcon(RefugeIcons.Fingerprint, c.primary, 48.dp) }
        Heading("باز کردن سریع با اثر انگشت؟", "می‌توانید بعداً خزانه را فقط با اثر انگشت باز کنید. رمز اصلی همیشه معتبر می‌ماند. اگر اثر انگشت جدیدی به گوشی اضافه شود، این میان‌بر خودکار غیرفعال می‌شود.")
        PrimaryButton("فعال‌سازی", onClick = onEnable)
        TextLink("فعلاً نه", onSkip)
    }
}

// ───────────────────────── باز کردن خزانه ─────────────────────────

@Composable
fun VaultUnlockScreen(
    biometricReady: Boolean,
    message: String?,
    busy: Boolean,
    onPassword: (CharArray) -> Unit,
    onBiometric: () -> Unit,
    onForgot: () -> Unit,
    subtitle: String = "خزانهٔ شما قفل است",
    onCancel: (() -> Unit)? = null,
    pinReady: Boolean = false,
    onPin: ((CharArray) -> Unit)? = null,
) {
    val c = Refuge.colors
    val str = Refuge.strings
    var pass by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinMode by remember { mutableStateOf(false) }
    Page {
        Spacer(Modifier.height(32.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                SIcon(RefugeIcons.Shield, c.primary, 48.dp)
            }
            Text("Refuge", color = c.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = c.muted, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        if (biometricReady) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(80.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(c.primary).clickable(role = Role.Button, onClickLabel = "باز کردن با اثر انگشت", onClick = onBiometric),
                    contentAlignment = Alignment.Center,
                ) { SIcon(RefugeIcons.Fingerprint, c.onPrimary, 40.dp, "باز کردن با اثر انگشت") }
            }
        }
        if (pinReady && pinMode && onPin != null) {
            SecretField("پین", pin, { pin = it.filter { ch -> ch.isDigit() }.take(6) }, keyboardType = KeyboardType.NumberPassword)
            if (message != null) Notice(message, error = true)
            PrimaryButton(if (busy) "در حال بررسی…" else str.pinUnlockButton, enabled = pin.length in 4..6 && !busy) {
                onPin(pin.toCharArray())
                pin = ""
            }
            TextLink(str.pinUsePassword) { pinMode = false; pin = "" }
        } else {
            SecretField("رمز اصلی", pass, { pass = it })
            if (message != null) Notice(message, error = true)
            PrimaryButton(if (busy) "در حال بررسی…" else "باز کردن", enabled = pass.isNotEmpty() && !busy) {
                onPassword(pass.toCharArray())
                pass = ""
            }
            if (pinReady && onPin != null) {
                TextLink(str.pinUsePin) { pinMode = true; pass = "" }
            }
        }
        if (onCancel != null) {
            TextLink("انصراف و بازگشت", onCancel)
        } else if (!pinMode) {
            TextLink("رمز را فراموش کرده‌ام (بازیابی با ۲۴ کلمه)", onForgot)
        }
    }
}

/** تعیین یا تغییر پین؛ پین فعلی فقط وقتی پرسیده می‌شود که از قبل پین فعال باشد. */
@Composable
fun PinSetupScreen(
    hasCurrentPin: Boolean,
    busy: Boolean,
    message: String?,
    onSubmit: (current: CharArray?, next: CharArray) -> Unit,
    onBack: () -> Unit,
) {
    val c = Refuge.colors
    val str = Refuge.strings
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val digitsOk = next.length in 4..6 && next.all { it.isDigit() }
    val ok = digitsOk && next == confirm && (!hasCurrentPin || current.length in 4..6)
    Page {
        Heading(str.pinSetupTitle, str.pinSetupHint)
        if (hasCurrentPin) {
            SecretField(str.pinCurrent, current, { current = it.filter { ch -> ch.isDigit() }.take(6) }, keyboardType = KeyboardType.NumberPassword)
        }
        SecretField(str.pinNew, next, { next = it.filter { ch -> ch.isDigit() }.take(6) }, keyboardType = KeyboardType.NumberPassword)
        SecretField(str.pinConfirm, confirm, { confirm = it.filter { ch -> ch.isDigit() }.take(6) }, keyboardType = KeyboardType.NumberPassword)
        if (next.isNotEmpty() && !digitsOk) {
            Text(str.pinInvalid, color = c.secondary, fontSize = 13.sp)
        } else if (confirm.isNotEmpty() && next != confirm) {
            Text(str.pinMismatch, color = c.secondary, fontSize = 13.sp)
        }
        if (message != null) Notice(message, error = true)
        PrimaryButton(if (busy) "در حال بررسی…" else str.save, enabled = ok && !busy) {
            onSubmit(if (hasCurrentPin) current.toCharArray() else null, next.toCharArray())
            current = ""
            next = ""
            confirm = ""
        }
        TextLink("انصراف و بازگشت", onBack)
    }
}

@Composable
fun RecoveryEntryScreen(message: String?, busy: Boolean, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    val c = Refuge.colors
    var phrase by remember { mutableStateOf("") }
    Page {
        Heading("بازیابی با عبارت ۲۴ کلمه‌ای", "کلمات را به ترتیب و با فاصله وارد کنید. بعد از بازیابی باید رمز اصلی جدید تعیین کنید.")
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.container).padding(16.dp)) {
            BasicTextField(
                value = phrase,
                onValueChange = { phrase = it },
                textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontFamily = Mono, textAlign = TextAlign.Left, lineHeight = 26.sp),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
        }
        if (message != null) Notice(message, error = true)
        PrimaryButton(if (busy) "در حال بررسی…" else "بازیابی", enabled = phrase.isNotBlank() && !busy) { onSubmit(phrase) }
        TextLink("بازگشت", onBack)
    }
}

@Composable
fun ResetPasswordScreen(busy: Boolean, onSubmit: (CharArray) -> Unit) {
    val c = Refuge.colors
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val ok = pass.length >= MIN_MASTER_LENGTH && pass == confirm
    Page {
        Heading("رمز اصلی جدید", "بازیابی موفق بود. اکنون یک رمز اصلی جدید تعیین کنید. عبارت بازیابی قبلی همچنان معتبر است.")
        SecretField("رمز اصلی جدید", pass, { pass = it })
        SecretField("تکرار رمز", confirm, { confirm = it })
        Text(
            if (pass.length < MIN_MASTER_LENGTH) "حداقل ${MIN_MASTER_LENGTH.fa()} نویسه." else if (pass != confirm && confirm.isNotEmpty()) "دو رمز یکسان نیستند." else "",
            color = c.muted, fontSize = 13.sp,
        )
        PrimaryButton(if (busy) "لطفاً صبر کنید…" else "ذخیره", enabled = ok && !busy) { onSubmit(pass.toCharArray()) }
    }
}


@Composable
fun ChangeMasterPasswordScreen(
    busy: Boolean,
    message: String?,
    onSubmit: (current: CharArray, next: CharArray) -> Unit,
    onBack: () -> Unit,
) {
    val c = Refuge.colors
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val ok = current.isNotEmpty() && next.length >= MIN_MASTER_LENGTH && next == confirm
    Page {
        Heading("تغییر رمز اصلی", "ابتدا رمز فعلی را تأیید و سپس رمز اصلی جدید را وارد کنید. عبارت بازیابی ۲۴ کلمه‌ای همچنان معتبر می‌ماند.")
        SecretField("رمز اصلی فعلی", current, { current = it })
        SecretField("رمز اصلی جدید", next, { next = it })
        SecretField("تکرار رمز جدید", confirm, { confirm = it })
        if (next.isNotEmpty() && next.length < MIN_MASTER_LENGTH) {
            Text("حداقل ${MIN_MASTER_LENGTH.fa()} نویسه لازم است.", color = c.secondary, fontSize = 13.sp)
        } else if (confirm.isNotEmpty() && next != confirm) {
            Text("دو رمز جدید یکسان نیستند.", color = c.secondary, fontSize = 13.sp)
        }
        if (message != null) Notice(message, error = true)
        PrimaryButton(if (busy) "در حال تغییر رمز…" else "ذخیره رمز جدید", enabled = ok && !busy) {
            onSubmit(current.toCharArray(), next.toCharArray())
            current = ""
            next = ""
            confirm = ""
        }
        TextLink("انصراف و بازگشت", onBack)
    }
}

// ───────────────────────── آنبوردینگ اولین اجرا (۳ صفحه، فقط نصب تازه) ─────────────────────────

@Composable
private fun OnboardingOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Refuge.colors
    Row(
        Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (selected) Text("●", color = c.primary, fontSize = 14.sp)
    }
}

@Composable
private fun PermissionActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Refuge.colors
    Box(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (enabled) c.primary else c.container)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) c.onPrimary else c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, actionLabel: String, actionEnabled: Boolean, onAction: () -> Unit) {
    val c = Refuge.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
        PermissionActionButton(actionLabel, actionEnabled, onAction)
    }
}

@Composable
fun FirstRunOnboarding(
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    density: ListDensity,
    onSetDensity: (ListDensity) -> Unit,
    autoLock: AutoLockOption,
    onSetAutoLock: (AutoLockOption) -> Unit,
    language: AppLanguage,
    onSetLanguage: (AppLanguage) -> Unit,
    notifSupported: Boolean,
    notifGranted: Boolean,
    onRequestNotifPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onEnableAutofill: () -> Unit,
    onDone: () -> Unit,
) {
    val c = Refuge.colors
    var page by remember { mutableStateOf(0) }
    var cameraAsked by remember { mutableStateOf(false) }
    Page {
        when (page) {
            0 -> {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(c.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { SIcon(RefugeIcons.Shield, c.primary, 38.dp) }
                    Text("Refuge", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Heading(
                    "به سولینا خوش آمدید",
                    "مدیریت رمز عبور و کد دومرحله‌ای؛ همه‌چیز آفلاین و رمزگذاری‌شده روی همین دستگاه می‌ماند.",
                )
                Text(
                    "ساخته‌شده توسط Solina-Projects (ناصر سلیمانی) — متن‌باز، GPL-3.0.",
                    color = c.muted,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton("ادامه") { page = 1 }
            }
            1 -> {
                Heading(
                    "شخصی‌سازی",
                    "ظاهر برنامه را به سلیقهٔ خودت تنظیم کن؛ همه را بعداً هم می‌توانی از تنظیمات عوض کنی.",
                )
                Text("تم", color = c.muted, fontSize = 12.sp)
                for (option in ThemeMode.entries) {
                    OnboardingOptionRow(option.labelFa, themeMode == option) { onSetThemeMode(option) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Text("تراکم فهرست", color = c.muted, fontSize = 12.sp)
                for (option in ListDensity.entries) {
                    OnboardingOptionRow(option.labelFa, density == option) { onSetDensity(option) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Text("زمان قفل خودکار", color = c.muted, fontSize = 12.sp)
                for (option in AutoLockOption.entries) {
                    OnboardingOptionRow(option.label, autoLock == option) { onSetAutoLock(option) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Text("زبان", color = c.muted, fontSize = 12.sp)
                for (option in AppLanguage.entries) {
                    OnboardingOptionRow(option.label, language == option) { onSetLanguage(option) }
                }
                PrimaryButton("ادامه") { page = 2 }
                TextLink("بازگشت") { page = 0 }
            }
            else -> {
                Heading(
                    "مجوزها",
                    "فقط چیزهایی که برنامه واقعاً لازم دارد؛ هر کدام اختیاری است و رد کردنش مشکلی ایجاد نمی‌کند.",
                )
                if (notifSupported) {
                    PermissionRow(
                        "اعلان‌ها",
                        "برای اعلان خطا و یادآوری کارها",
                        if (notifGranted) "فعال است" else "فعال‌سازی",
                        !notifGranted,
                        onRequestNotifPermission,
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                }
                PermissionRow(
                    "دوربین",
                    "فقط برای اسکن کد QR — اختیاری",
                    if (cameraAsked) "درخواست شد" else "فعال‌سازی",
                    !cameraAsked,
                    { cameraAsked = true; onRequestCameraPermission() },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                PermissionRow(
                    "پر کردن خودکار",
                    "برای اینکه Refuge رمزها را در مرورگر و اپ‌ها پر کند",
                    "فعال‌سازی",
                    true,
                    onEnableAutofill,
                )
                Notice("به فضای ذخیره‌سازی دسترسی لازم نیست — انتخاب فایل و پوشه با انتخاب‌گر سیستم انجام می‌شود.")
                PrimaryButton("شروع") { onDone() }
                TextLink("بازگشت") { page = 1 }
            }
        }
    }
}
