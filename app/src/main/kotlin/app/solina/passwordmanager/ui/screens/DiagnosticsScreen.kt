package app.solina.passwordmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.ui.components.IconAction
import app.solina.passwordmanager.ui.components.Mono
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons

/** گزارش عیب‌یابی: متن را می‌توان کپی یا از طریق هر برنامه (تلگرام، ایمیل ...) به‌اشتراک گذاشت. */
@Composable
fun DiagnosticsScreen(text: String, onBack: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit, onClear: () -> Unit) {
    val c = Solina.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(SolinaIcons.ChevronRight, "بازگشت", tint = c.text, onClick = onBack)
            Text("گزارش عیب‌یابی", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "این گزارش فقط روی همین دستگاه است و خودکار ارسال نمی‌شود. فقط مراحل برنامه و نام خطاها را دارد، نه رمز و نه محتوای ورودی‌ها. اگر مشکلی پیش آمد، آن را کپی یا اشتراک‌گذاری کنید و برای توسعه‌دهنده بفرستید.",
            color = c.muted, fontSize = 13.sp, lineHeight = 21.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, action, filled) in listOf(Triple("کپی", onCopy, true), Triple("اشتراک‌گذاری", onShare, true), Triple("پاک کردن", onClear, false))) {
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (filled) c.primary else c.container)
                        .clickable(role = Role.Button, onClick = action),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (filled) c.onPrimary else c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.container)
                .verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            Text(text.ifBlank { "هنوز چیزی ثبت نشده است." }, color = c.text, fontSize = 11.sp, fontFamily = Mono, lineHeight = 16.sp)
        }
    }
}
