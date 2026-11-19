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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.ui.components.Card
import app.solina.passwordmanager.ui.components.IconAction
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons

@Composable
fun WebDavSetupScreen(
    isJoin: Boolean,
    busy: Boolean,
    message: String?,
    onSubmit: (url: String, username: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    var url by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    val valid = url.trim().startsWith("https://", ignoreCase = true) && url.trim().length > 10

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(SolinaIcons.ChevronRight, str.cancel, tint = c.text, onClick = onBack)
            Text(
                if (isJoin) {
                    if (str.isFa) "بازیابی از سرور WebDAV" else "Restore from WebDAV Server"
                } else {
                    str.webdavSyncTitle
                },
                color = c.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Card(background = c.primaryContainer) {
            Text(
                if (str.isFa) {
                    "تمام ورودی‌ها پیش از ارسال در همین گوشی با AES-256-GCM و HMAC-SHA256 رمزگذاری می‌شوند. " +
                        "سرور WebDAV فقط فایل‌های رمزشده را نگه می‌دارد و تنها اتصال HTTPS مجاز است."
                } else {
                    "All records are client-side encrypted (AES-256-GCM + HMAC-SHA256) before upload. " +
                        "Only HTTPS endpoints are accepted; the server never sees plaintext secrets."
                },
                color = c.onPrimaryContainer,
                fontSize = 13.sp,
                lineHeight = 22.sp,
            )
        }

        Field(
            if (str.isFa) "نشانی HTTPS پوشهٔ WebDAV" else "HTTPS WebDAV Collection URL",
            url,
            { url = it },
            mono = true,
            keyboard = KeyboardType.Uri,
        )
        Field(if (str.isFa) "نام کاربری (اختیاری)" else "Username (optional)", username, { username = it }, mono = true)
        Field(
            if (str.isFa) "رمز عبور یا App Password" else "Password or App Password",
            password,
            { password = it },
            mono = true,
            hidden = !showPw,
            trailing = {
                IconAction(if (showPw) SolinaIcons.EyeOff else SolinaIcons.Eye, "toggle") { showPw = !showPw }
            },
        )

        if (message != null) {
            Card(background = c.secondaryContainer) {
                Text(message, color = c.onSecondaryContainer, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(if (!busy && valid) c.primary else c.container)
                .clickable(enabled = !busy && valid, role = Role.Button) {
                    onSubmit(url.trim(), username.trim(), password)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (busy) (if (str.isFa) "در حال اتصال…" else "Connecting…")
                else if (isJoin) (if (str.isFa) "بررسی و بازیابی خزانه" else "Verify & Join Vault")
                else (if (str.isFa) "اتصال و همگام‌سازی" else "Connect & Sync"),
                color = if (!busy && valid) c.onPrimary else c.muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
