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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.core.Base32
import app.solina.passwordmanager.core.PasswordGenerator
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import app.solina.passwordmanager.ui.components.IconAction
import app.solina.passwordmanager.ui.components.Mono
import app.solina.passwordmanager.ui.components.SIcon
import app.solina.passwordmanager.ui.components.FilterChip
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    mono: Boolean = false,
    multiline: Boolean = false,
    hidden: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Solina.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.container)
                .padding(horizontal = 16.dp, vertical = if (multiline) 14.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = !multiline,
                minLines = if (multiline) 4 else 1,
                visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (hidden) KeyboardType.Password else keyboard, autoCorrectEnabled = false),
                textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontFamily = if (mono) Mono else FontFamily.Default),
                cursorBrush = SolidColor(c.primary),
                modifier = if (multiline) Modifier.weight(1f) else Modifier.weight(1f).height(54.dp).padding(vertical = 15.dp),
            )
            trailing?.invoke()
        }
        if (error != null) Text(error, color = c.secondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/**
 * ایجاد/ویرایش ورودی. [initial] برابر null یعنی ورودی جدید.
 * یادداشت‌های کوتاه: عنوان اجباری است؛ راز TOTP اگر پر شود باید Base32 معتبر باشد.
 */
@Composable
fun EditScreen(initial: ItemPayload?, isNew: Boolean, busy: Boolean, onSave: (ItemPayload) -> Unit, onCancel: () -> Unit) {
    val c = Solina.colors
    val start = initial ?: ItemPayload()
    var kind by remember { mutableStateOf(start.kind) }
    var title by remember { mutableStateOf(start.title) }
    var username by remember { mutableStateOf(start.username) }
    var password by remember { mutableStateOf(start.password) }
    var showPw by remember { mutableStateOf(isNew) }
    var website by remember { mutableStateOf(start.website) }
    var totp by remember { mutableStateOf(start.totpSecret) }
    var note by remember { mutableStateOf(start.note) }
    var holder by remember { mutableStateOf(start.cardHolder) }
    var number by remember { mutableStateOf(start.cardNumber) }
    var expiry by remember { mutableStateOf(start.cardExpiry) }
    var cvv by remember { mutableStateOf(start.cardCvv) }
    var tried by remember { mutableStateOf(false) }

    val titleError = if (tried && title.isBlank()) "عنوان لازم است" else null
    val totpError = if (totp.isNotBlank() && Base32.decode(totp) == null) "راز TOTP باید Base32 معتبر باشد (حروف A–Z و ارقام ۲–۷)" else null

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(SolinaIcons.ChevronRight, "انصراف", tint = c.text, onClick = onCancel)
            Text(if (isNew) "ورودی جدید" else "ویرایش", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        if (isNew) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("ورود", kind == ItemKind.Login) { kind = ItemKind.Login }
                FilterChip("کارت", kind == ItemKind.Card) { kind = ItemKind.Card }
                FilterChip("یادداشت", kind == ItemKind.Note) { kind = ItemKind.Note }
            }
        }

        Field("عنوان", title, { title = it }, error = titleError)
        when (kind) {
            ItemKind.Login -> {
                Field("نام کاربری یا ایمیل", username, { username = it }, mono = true, keyboard = KeyboardType.Email)
                Field(
                    "رمز عبور", password, { password = it }, mono = true, hidden = !showPw,
                    trailing = {
                        IconAction(if (showPw) SolinaIcons.EyeOff else SolinaIcons.Eye, if (showPw) "پنهان کردن رمز" else "نمایش رمز") { showPw = !showPw }
                        IconAction(SolinaIcons.Refresh, "ساخت رمز تصادفی") {
                            password = PasswordGenerator.generate(20); showPw = true
                        }
                    },
                )
                Field("وب‌سایت", website, { website = it }, mono = true, keyboard = KeyboardType.Uri)
                Field("راز TOTP (Base32، اختیاری)", totp, { totp = it.uppercase() }, mono = true, error = totpError)
            }
            ItemKind.Card -> {
                Field("نام دارندهٔ کارت", holder, { holder = it })
                Field("شمارهٔ کارت", number, { number = it.filter { ch -> ch.isDigit() }.take(19) }, mono = true, keyboard = KeyboardType.Number)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { Field("انقضا (MM/YY)", expiry, { expiry = it.take(5) }, mono = true) }
                    Box(Modifier.weight(1f)) { Field("CVV2", cvv, { cvv = it.filter { ch -> ch.isDigit() }.take(4) }, mono = true, keyboard = KeyboardType.NumberPassword) }
                }
            }
            ItemKind.Note -> Unit
        }
        Field(if (kind == ItemKind.Note) "متن یادداشت" else "یادداشت (اختیاری)", note, { note = it }, multiline = true)

        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (busy) c.divider else c.primary)
                .clickable(enabled = !busy, role = Role.Button) {
                    tried = true
                    if (title.isNotBlank() && totpError == null) {
                        onSave(
                            start.copy(
                                kind = kind, title = title.trim(), username = username.trim(), password = password,
                                website = website.trim(), totpSecret = totp.trim(), note = note,
                                cardHolder = holder.trim(), cardNumber = number, cardExpiry = expiry.trim(), cardCvv = cvv,
                            ),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text("ذخیره", color = c.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(32.dp))
    }
}
