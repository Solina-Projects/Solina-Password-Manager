package app.solina.passwordmanager.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntOffset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.ui.components.Card
import app.solina.passwordmanager.ui.components.IconAction
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons
import kotlin.math.roundToInt

enum class DataStep { Menu, ExportPassword, RestorePassword, ImportPassword }

private const val MIN_BACKUP_PASSWORD = 10

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    val c = Solina.colors
    Row(Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(SolinaIcons.ChevronRight, "بازگشت", tint = c.text, onClick = onBack)
        Text(title, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BigButton(text: String, enabled: Boolean = true, filled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    val c = Solina.colors
    var widthPx by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onSizeChanged { widthPx = it.width }
            .clip(RoundedCornerShape(28.dp))
            .background(if (!enabled) c.divider else if (filled) c.primary else c.container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled && enabled) c.onPrimary else c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        // حرکت (با الهام از «Action button» و «File transfer» در D3 Motion): وقتی
        // دکمه مشغول است، یک نوار لغزنده پایین دکمه می‌دود. درصد واقعی نداریم
        // (رمزگذاری یک مرحله است) پس درصد جعلی نشان نمی‌دهیم.
        if (busy) {
            Box(Modifier.align(Alignment.BottomStart)) {
                BusySlideBar(widthPx, if (filled) c.onPrimary else c.primary)
            }
        }
    }
}

/** نوار لغزندهٔ «در حال انجام» داخل دکمهٔ مشغول. */
@Composable
private fun BusySlideBar(buttonWidthPx: Int, color: Color) {
    val density = LocalDensity.current
    val slide by rememberInfiniteTransition(label = "busy-slide").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "busy-slide-value",
    )
    if (buttonWidthPx <= 0) return
    val barWidthPx = (buttonWidthPx * 0.45f).roundToInt()
    val xPx = (slide * (buttonWidthPx - barWidthPx)).roundToInt()
    Box(
        Modifier
            .offset { IntOffset(xPx, 0) }
            .size(width = with(density) { barWidthPx.toDp() }, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.85f)),
    )
}

/** بکاپ رمزشده، بازیابی و ایمپورت. انتخاب فایل را فراخواننده با Storage Access Framework انجام می‌دهد (بدون مجوز). */
@Composable
fun DataScreen(
    step: DataStep,
    busy: Boolean,
    message: String?,
    itemCount: Int,
    onBack: () -> Unit,
    onStartExport: () -> Unit,
    onSubmitExport: (CharArray) -> Unit,
    onPickRestore: () -> Unit,
    onSubmitRestore: (CharArray) -> Unit,
    onPickImport: () -> Unit,
    onSubmitImport: (CharArray) -> Unit,
) {
    val c = Solina.colors
    var pw by remember(step) { mutableStateOf("") }
    var pw2 by remember(step) { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (step) {
            DataStep.Menu -> {
                Header("بکاپ و ایمپورت", onBack)
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("بکاپ رمزشده", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "همهٔ ورودی‌ها (${itemCount.toString()} مورد) در یک فایل با رمز جداگانه‌ای که خودتان انتخاب می‌کنید رمز می‌شود. " +
                                "این رمز را فراموش کنید، بکاپ قابل‌بازیابی نیست. فایل را جایی امن (فضای ابری یا حافظهٔ جدا) نگه دارید.",
                            color = c.muted, fontSize = 14.sp, lineHeight = 24.sp,
                        )
                        BigButton("ساخت بکاپ", enabled = itemCount > 0, onClick = onStartExport)
                        BigButton("بازیابی از بکاپ Solina", filled = false, onClick = onPickRestore)
                    }
                }
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ورود از برنامهٔ دیگر", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "بدون رمز: Bitwarden (JSON/CSV)، Aegis، 2FAS، andOTP، Raivo، Proton Pass (ZIP/JSON/CSV)، Proton Authenticator، Ente Auth (متن ساده)، Google Authenticator (کد QR انتقال)، CSV مرورگرها و گوگل، LastPass، KeePass، Dashlane، NordPass، 1Password و لینک‌های otpauth.\n" +
                                "رمزشده (با رمز خود فایل): Aegis، 2FAS و Bitwarden. خروجی رمزشدهٔ Ente و PGP پروتون هنوز پشتیبانی نمی‌شود.\n" +
                                "ورودی‌های عیناً تکراری اضافه نمی‌شوند.",
                            color = c.muted, fontSize = 14.sp, lineHeight = 24.sp,
                        )
                        BigButton("انتخاب فایل برای ورود", filled = false, onClick = onPickImport)
                    }
                }
                Card(background = c.secondaryContainer) {
                    Text(
                        "فایل‌های خروجی سایر برنامه‌ها معمولاً بدون رمز هستند. بعد از ورود، فایل اصلی را از حافظه و سطل زباله و فضای ابری حذف کنید.",
                        color = c.onSecondaryContainer, fontSize = 14.sp, lineHeight = 24.sp,
                    )
                }
                if (message != null) Text(message, color = c.secondary, fontSize = 14.sp, lineHeight = 24.sp)
            }

            DataStep.ExportPassword -> {
                Header("رمز بکاپ", onBack)
                Text("یک رمز قوی برای این بکاپ انتخاب کنید. می‌تواند با رمز اصلی فرق داشته باشد. حداقل ${MIN_BACKUP_PASSWORD} نویسه.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Field("رمز بکاپ", pw, { pw = it }, hidden = true)
                Field("تکرار رمز بکاپ", pw2, { pw2 = it }, hidden = true)
                val tooShort = pw.length in 1 until MIN_BACKUP_PASSWORD
                val mismatch = pw2.isNotEmpty() && pw != pw2
                if (tooShort) Text("رمز کوتاه است.", color = c.secondary, fontSize = 13.sp)
                if (mismatch) Text("دو رمز یکسان نیستند.", color = c.secondary, fontSize = 13.sp)
                if (message != null) Text(message, color = c.secondary, fontSize = 14.sp)
                BigButton(if (busy) "در حال رمزگذاری…" else "ساخت و ذخیرهٔ فایل", enabled = !busy && pw.length >= MIN_BACKUP_PASSWORD && pw == pw2, busy = busy) {
                    onSubmitExport(pw.toCharArray())
                }
            }

            DataStep.ImportPassword -> {
                Header("ورود از فایل رمزشده", onBack)
                Text("این فایل رمزشده است. رمزی را که هنگام خروجی گرفتن از آن برنامه انتخاب کرده بودید وارد کنید. رمز جایی ذخیره نمی‌شود.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Field("رمز فایل", pw, { pw = it }, hidden = true)
                if (message != null) Text(message, color = c.secondary, fontSize = 14.sp, lineHeight = 24.sp)
                BigButton(if (busy) "در حال بازگشایی…" else "وارد کردن", enabled = !busy && pw.isNotEmpty(), busy = busy) { onSubmitImport(pw.toCharArray()) }
            }

            DataStep.RestorePassword -> {
                Header("بازیابی بکاپ", onBack)
                Text("رمزی را که هنگام ساخت این بکاپ انتخاب کرده بودید وارد کنید.", color = c.muted, fontSize = 14.sp, lineHeight = 24.sp)
                Field("رمز بکاپ", pw, { pw = it }, hidden = true)
                if (message != null) Text(message, color = c.secondary, fontSize = 14.sp, lineHeight = 24.sp)
                BigButton(if (busy) "در حال بازگشایی…" else "بازیابی", enabled = !busy && pw.isNotEmpty(), busy = busy) { onSubmitRestore(pw.toCharArray()) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
