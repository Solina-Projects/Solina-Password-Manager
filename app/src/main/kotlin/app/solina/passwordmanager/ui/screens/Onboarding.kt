package app.solina.passwordmanager.ui.screens

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
import app.solina.passwordmanager.core.fa
import app.solina.passwordmanager.ui.components.IconAction
import app.solina.passwordmanager.ui.components.Mono
import app.solina.passwordmanager.ui.components.SIcon
import app.solina.passwordmanager.ui.components.copySensitive
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons

// ───────────────────────── اجزای مشترک ─────────────────────────

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Solina.colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun Heading(title: String, body: String) {
    val c = Solina.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
        Text(body, color = c.muted, fontSize = 15.sp, lineHeight = 26.sp)
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Solina.colors
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
    val c = Solina.colors
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
        Text(text, color = Solina.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Solina.colors
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
        IconAction(
            if (visible) SolinaIcons.EyeOff else SolinaIcons.Eye,
            if (visible) "پنهان کردن رمز" else "نمایش رمز",
            tint = c.muted,
        ) { visible = !visible }
    }
}

@Composable
private fun Notice(text: String, error: Boolean = false) {
    val c = Solina.colors
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
fun WelcomeScreen(message: String? = null, onCreate: () -> Unit, onJoinCloud: () -> Unit, onJoinStorage: () -> Unit, onDiagnostics: () -> Unit = {}) {
    val c = Solina.colors
    Page {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { SIcon(SolinaIcons.Shield, c.primary, 48.dp) }
        Heading("به Solina خوش آمدید", "گاوصندوق دیجیتال شما. همه‌چیز فقط روی همین دستگاه رمزگذاری و نگه‌داری می‌شود.")
        Notice("فقط روی دستگاه شما، بدون نیاز به حساب، و شما تنها کسی هستید که کلید را دارید.")
        Notice("نسخهٔ آزمایشی: رمزنگاری و ذخیره‌سازی واقعی‌اند ولی هنوز بازبینی امنیتی مستقل نشده‌اند. فعلاً رمزهای حیاتی خود را در آن نگه ندارید.", error = true)
        if (message != null) Notice(message, error = true)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("ساخت خزانهٔ جدید", onClick = onCreate)
        SecondaryButton("بازیابی از فضای ابری", onClick = onJoinCloud)
        SecondaryButton("بازیابی از فضای ذخیره‌سازی", onClick = onJoinStorage)
        TextLink("گزارش عیب‌یابی", onDiagnostics)
    }
}

// ───────────────────────── ساخت رمز اصلی ─────────────────────────

private const val MIN_MASTER_LENGTH = 10
private const val RECOVERY_CLIPBOARD_MILLIS = 60_000L

@Composable
fun CreatePasswordScreen(busy: Boolean, onSubmit: (CharArray) -> Unit) {
    val c = Solina.colors
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
        PrimaryButton(if (busy) "لطفاً صبر کنید…" else "ادامه", enabled = ok && !busy) {
            val secret = pass.toCharArray()
            pass = ""
            confirm = ""
            onSubmit(secret)
        }
    }
}

// ───────────────────────── عبارت بازیابی ─────────────────────────

@Composable
fun RecoveryWordsScreen(words: List<String>, onNext: () -> Unit) {
    val c = Solina.colors
    Page {
        Heading(
            "عبارت بازیابی شما",
            "این ۲۴ کلمه را به ترتیب نگه دارید. بهترین کار نوشتن روی کاغذ و نگه‌داری در جای امن است؛ اگر کاغذ در دسترس نیست، می‌توانید کلمات را کپی کنید. اسکرین‌شات در این صفحه ممکن نیست.",
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
        val ctx = LocalContext.current
        var copied by remember { mutableStateOf(false) }
        SecondaryButton(if (copied) "کپی شد ✓" else "کپی کلمات") {
            copySensitive(ctx, "recovery", words.joinToString(" "), clearAfterMillis = RECOVERY_CLIPBOARD_MILLIS)
            copied = true
        }
        if (copied) {
            Notice("کلمات کپی شدند و ۶۰ ثانیهٔ دیگر از کلیپ‌بورد پاک می‌شوند. همین حالا آن‌ها را جایی امن بچسبانید.")
        }
        Notice(
            "هر کس این کلمات را داشته باشد می‌تواند خزانهٔ شما را باز کند. اگر کاغذ ندارید، کلمات را فقط در یادداشتی قفل‌شده یا رمزگذاری‌شده بچسبانید، نه در پیام‌رسان، ایمیل یا یادداشت معمولی. بعضی کیبوردها تاریخچهٔ کلیپ‌بورد را نگه می‌دارند؛ بعد از ذخیره، آن تاریخچه را هم پاک کنید.",
            error = true,
        )
        PrimaryButton("ذخیره کردم، ادامه", onClick = onNext)
    }
}

@Composable
fun ConfirmWordsScreen(words: List<String>, onConfirmed: () -> Unit, onBack: () -> Unit) {
    val c = Solina.colors
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
    val c = Solina.colors
    Page {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { SIcon(SolinaIcons.Fingerprint, c.primary, 48.dp) }
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
) {
    val c = Solina.colors
    var pass by remember { mutableStateOf("") }
    Page {
        Spacer(Modifier.height(32.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                SIcon(SolinaIcons.Shield, c.primary, 48.dp)
            }
            Text("Solina", color = c.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("خزانهٔ شما قفل است", color = c.muted, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (biometricReady) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(80.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(c.primary).clickable(role = Role.Button, onClickLabel = "باز کردن با اثر انگشت", onClick = onBiometric),
                    contentAlignment = Alignment.Center,
                ) { SIcon(SolinaIcons.Fingerprint, c.onPrimary, 40.dp, "باز کردن با اثر انگشت") }
            }
        }
        SecretField("رمز اصلی", pass, { pass = it })
        if (message != null) Notice(message, error = true)
        PrimaryButton(if (busy) "در حال بررسی…" else "باز کردن", enabled = pass.isNotEmpty() && !busy) {
            onPassword(pass.toCharArray())
            pass = ""
        }
        TextLink("رمز را فراموش کرده‌ام (بازیابی با ۲۴ کلمه)", onForgot)
    }
}

@Composable
fun RecoveryEntryScreen(message: String?, busy: Boolean, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    val c = Solina.colors
    var phrase by remember { mutableStateOf("") }
    Page {
        Heading("بازیابی با عبارت ۲۴ کلمه‌ای", "کلمات را به ترتیب و با فاصله وارد کنید. بعد از بازیابی باید رمز اصلی جدید تعیین کنید.")
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.container).padding(16.dp)) {
            BasicTextField(
                value = phrase,
                onValueChange = { phrase = it },
                textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontFamily = Mono, textAlign = TextAlign.Left, lineHeight = 26.sp),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
        }
        if (message != null) Notice(message, error = true)
        PrimaryButton(if (busy) "در حال بررسی…" else "بازیابی", enabled = phrase.isNotBlank() && !busy) {
            val submitted = phrase
            phrase = ""
            onSubmit(submitted)
        }
        TextLink("بازگشت", onBack)
    }
}

@Composable
fun ResetPasswordScreen(busy: Boolean, onSubmit: (CharArray) -> Unit) {
    val c = Solina.colors
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
        PrimaryButton(if (busy) "لطفاً صبر کنید…" else "ذخیره", enabled = ok && !busy) {
            val secret = pass.toCharArray()
            pass = ""
            confirm = ""
            onSubmit(secret)
        }
    }
}
