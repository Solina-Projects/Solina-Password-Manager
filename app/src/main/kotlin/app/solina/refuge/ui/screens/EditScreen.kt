package app.solina.refuge.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import app.solina.refuge.core.Base32
import app.solina.refuge.core.OtpType
import app.solina.refuge.core.PasswordGenerator
import app.solina.refuge.core.storage.ItemKind
import app.solina.refuge.core.storage.ItemPayload
import app.solina.refuge.core.storage.TagHelper
import app.solina.refuge.ui.components.AnimatedFilterRow
import app.solina.refuge.ui.components.IconAction
import app.solina.refuge.ui.components.Mono
import app.solina.refuge.ui.components.StrengthBar
import app.solina.refuge.ui.theme.Refuge
import app.solina.refuge.ui.theme.RefugeIcons

private val ICON_CHOICES = listOf("", "🔑", "🏦", "📧", "💼", "🛒", "🌐", "🪪", "🛡️", "💳", "📝")

@Composable
internal fun Field(
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
    val c = Refuge.colors
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
 * ایجاد/ویرایش ورودی (Login، Card، Identity، Note) با پشتیبانی از دسته‌بندی، آیکون اختصاصی و TOTP/HOTP.
 */
@Composable
fun EditScreen(initial: ItemPayload?, isNew: Boolean, busy: Boolean, onSave: (ItemPayload) -> Unit, onCancel: () -> Unit) {
    val c = Refuge.colors
    val str = Refuge.strings
    val start = initial ?: ItemPayload()
    var kind by remember { mutableStateOf(start.kind) }
    var title by remember { mutableStateOf(start.title) }
    var category by remember { mutableStateOf(start.category) }
    var tags by remember { mutableStateOf(start.tags) }
    var tagInput by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(start.icon) }
    var favorite by remember { mutableStateOf(start.favorite) }

    // Login & OTP
    var username by remember { mutableStateOf(start.username) }
    var password by remember { mutableStateOf(start.password) }
    var showPw by remember { mutableStateOf(isNew) }
    var website by remember { mutableStateOf(start.website) }
    var totp by remember { mutableStateOf(start.totpSecret) }
    var otpType by remember { mutableStateOf(start.otpType) }
    var otpDigits by remember { mutableStateOf(start.totpDigits) }
    var otpAlgo by remember { mutableStateOf(start.totpAlgorithm) }
    var otpCounter by remember { mutableStateOf(start.totpCounter.toString()) }

    // Card
    var bankName by remember { mutableStateOf(start.bankName) }
    var holder by remember { mutableStateOf(start.cardHolder) }
    var number by remember { mutableStateOf(start.cardNumber) }
    var expiry by remember { mutableStateOf(start.cardExpiry) }
    var cvv by remember { mutableStateOf(start.cardCvv) }
    var cardPin by remember { mutableStateOf(start.cardPin) }

    // Identity
    var fullName by remember { mutableStateOf(start.fullName) }
    var email by remember { mutableStateOf(start.email) }
    var phone by remember { mutableStateOf(start.phone) }
    var nationalId by remember { mutableStateOf(start.nationalId) }
    var company by remember { mutableStateOf(start.company) }
    var address by remember { mutableStateOf(start.address) }
    var city by remember { mutableStateOf(start.city) }
    var postalCode by remember { mutableStateOf(start.postalCode) }
    var country by remember { mutableStateOf(start.country) }

    var note by remember { mutableStateOf(start.note) }
    var customFields by remember { mutableStateOf(start.customFields) }
    var tried by remember { mutableStateOf(false) }

    val titleError = if (tried && title.isBlank()) (if (str.isFa) "عنوان لازم است" else "Title is required") else null
    val totpError = if (totp.isNotBlank() && Base32.decode(totp) == null) {
        if (str.isFa) "راز OTP باید Base32 معتبر باشد (A–Z و ۲–۷)" else "OTP secret must be valid Base32"
    } else null

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(RefugeIcons.ChevronRight, str.cancel, tint = c.text, onClick = onCancel)
            Text(if (isNew) str.newItem else str.editItem, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        if (isNew) {
            AnimatedFilterRow(
                labels = listOf(str.kindLogin, str.kindCard, str.kindIdentity, str.kindNote),
                selected = when (kind) {
                    ItemKind.Login -> 0
                    ItemKind.Card -> 1
                    ItemKind.Identity -> 2
                    ItemKind.Note -> 3
                    // یادداشت/کار بخش جدا دارند و هرگز به این ویرایشگر نمی‌رسند.
                    ItemKind.QuickNote -> 3
                    ItemKind.Task -> 3
                },
                onSelect = {
                    kind = when (it) {
                        0 -> ItemKind.Login
                        1 -> ItemKind.Card
                        2 -> ItemKind.Identity
                        else -> ItemKind.Note
                    }
                },
            )
        }

        Field(if (str.isFa) "عنوان" else "Title", title, { title = it }, error = titleError)
        Field(str.categoryLabel, category, { category = it })

        // برچسب‌های چندگانه (مکمل دسته‌بندی)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (str.isFa) "برچسب‌ها (اختیاری، با ویرگول جدا کنید)" else "Tags (optional, comma-separated)", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            if (tags.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.primaryContainer)
                                .clickable(role = Role.Button) { tags = TagHelper.removeTag(tags, tag) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(tag, color = c.onPrimaryContainer, fontSize = 13.sp)
                            Text("✕", color = c.onPrimaryContainer, fontSize = 12.sp)
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.container)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.weight(1f).height(54.dp).padding(vertical = 15.dp),
                )
                Text(
                    if (str.isFa) "＋ افزودن" else "+ Add",
                    color = c.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(role = Role.Button) {
                        val added = TagHelper.parseTags(tagInput)
                        if (added.isNotEmpty()) {
                            tags = (tags + added.filter { t -> tags.none { it.equals(t, ignoreCase = true) } }).take(TagHelper.MAX_TAGS)
                            tagInput = ""
                        }
                    }.padding(8.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(str.customIconLabel, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ICON_CHOICES.forEach { choice ->
                    val selected = icon == choice
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (selected) c.primaryContainer else c.container)
                            .clickable(role = Role.RadioButton) { icon = choice },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (choice.isEmpty()) "A" else choice, color = if (selected) c.onPrimaryContainer else c.muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(str.favoriteLabel, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = favorite,
                onCheckedChange = { favorite = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                    uncheckedThumbColor = c.outline, uncheckedTrackColor = c.container, uncheckedBorderColor = c.outline,
                ),
            )
        }

        when (kind) {
            ItemKind.Login -> {
                Field(if (str.isFa) "نام کاربری یا ایمیل" else "Username or Email", username, { username = it }, mono = true, keyboard = KeyboardType.Email)
                Field(
                    if (str.isFa) "رمز عبور" else "Password", password, { password = it }, mono = true, hidden = !showPw,
                    trailing = {
                        IconAction(if (showPw) RefugeIcons.EyeOff else RefugeIcons.Eye, "toggle") { showPw = !showPw }
                        IconAction(RefugeIcons.Refresh, "generate") {
                            password = PasswordGenerator.generate(20); showPw = true
                        }
                    },
                )
                if (password.isNotEmpty()) {
                    val level = strengthLevel(password)
                    StrengthBar(level, strengthLabel(level, str.isFa))
                }
                Field(if (str.isFa) "وب‌سایت" else "Website", website, { website = it }, mono = true, keyboard = KeyboardType.Uri)
                Field(if (str.isFa) "راز احراز هویت OTP (Base32، اختیاری)" else "OTP Secret (Base32, optional)", totp, { totp = it.uppercase() }, mono = true, error = totpError)
                if (totp.isNotBlank()) {
                    AnimatedFilterRow(
                        labels = listOf("TOTP (زمانی)", "HOTP (شمارنده‌ای)", "Steam (۵حرفی)"),
                        selected = when (otpType) {
                            OtpType.TOTP -> 0
                            OtpType.HOTP -> 1
                            OtpType.STEAM -> 2
                        },
                        onSelect = {
                            otpType = when (it) {
                                1 -> OtpType.HOTP
                                2 -> OtpType.STEAM
                                else -> OtpType.TOTP
                            }
                            // استیم همیشه ۵ حرف و SHA1 است؛ انتخاب قبلی کاربر حفظ نمی‌شود.
                            if (otpType == OtpType.STEAM) {
                                otpDigits = 5
                                otpAlgo = "SHA1"
                            }
                        },
                    )
                    if (otpType != OtpType.STEAM) {
                        AnimatedFilterRow(
                            labels = listOf("6 digits", "8 digits"),
                            selected = if (otpDigits == 8) 1 else 0,
                            onSelect = { otpDigits = if (it == 1) 8 else 6 },
                        )
                        AnimatedFilterRow(
                            labels = listOf("SHA1", "SHA256", "SHA512"),
                            selected = when (otpAlgo) { "SHA256" -> 1; "SHA512" -> 2; else -> 0 },
                            onSelect = { otpAlgo = when (it) { 1 -> "SHA256"; 2 -> "SHA512"; else -> "SHA1" } },
                        )
                    }
                    if (otpType == OtpType.HOTP) {
                        Field(str.counterLabel, otpCounter, { otpCounter = it.filter { ch -> ch.isDigit() } }, mono = true, keyboard = KeyboardType.Number)
                    }
                }
            }
            ItemKind.Card -> {
                Field(if (str.isFa) "نام بانک" else "Bank Name", bankName, { bankName = it })
                Field(if (str.isFa) "نام دارندهٔ کارت" else "Cardholder Name", holder, { holder = it })
                Field(if (str.isFa) "شمارهٔ کارت" else "Card Number", number, { number = it.filter { ch -> ch.isDigit() }.take(19) }, mono = true, keyboard = KeyboardType.Number)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { Field(if (str.isFa) "انقضا (MM/YY)" else "Expiry (MM/YY)", expiry, { expiry = it.take(5) }, mono = true) }
                    Box(Modifier.weight(1f)) { Field("CVV2", cvv, { cvv = it.filter { ch -> ch.isDigit() }.take(4) }, mono = true, keyboard = KeyboardType.NumberPassword) }
                }
                Field(if (str.isFa) "رمز کارت / PIN (اختیاری)" else "Card PIN (optional)", cardPin, { cardPin = it.filter { ch -> ch.isDigit() }.take(8) }, mono = true, hidden = true, keyboard = KeyboardType.NumberPassword)
            }
            ItemKind.Identity -> {
                Field(if (str.isFa) "نام و نام خانوادگی" else "Full Name", fullName, { fullName = it })
                Field(if (str.isFa) "ایمیل" else "Email", email, { email = it }, mono = true, keyboard = KeyboardType.Email)
                Field(if (str.isFa) "تلفن" else "Phone", phone, { phone = it }, mono = true, keyboard = KeyboardType.Phone)
                Field(if (str.isFa) "کد ملی / شماره شناسایی" else "National ID / SSN", nationalId, { nationalId = it }, mono = true)
                Field(if (str.isFa) "شرکت / سازمان" else "Company", company, { company = it })
                Field(if (str.isFa) "نشانی" else "Address", address, { address = it }, multiline = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { Field(if (str.isFa) "شهر" else "City", city, { city = it }) }
                    Box(Modifier.weight(1f)) { Field(if (str.isFa) "کد پستی" else "Postal Code", postalCode, { postalCode = it }, mono = true) }
                }
                Field(if (str.isFa) "کشور" else "Country", country, { country = it })
            }
            ItemKind.Note -> Unit
            // یادداشت/کار بخش جدا دارند و هرگز به این ویرایشگر نمی‌رسند.
            ItemKind.QuickNote -> Unit
            ItemKind.Task -> Unit
        }

        Field(
            if (kind == ItemKind.Note) (if (str.isFa) "متن یادداشت" else "Secure Note")
            else (if (str.isFa) "یادداشت (اختیاری)" else "Notes (optional)"),
            note,
            { note = it },
            multiline = true,
        )

        // فیلدهای سفارشی (فاز ۴) — برای همهٔ نوع‌های ورودی؛ مقدار پنهان مثل رمز پوشیده می‌ماند.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (str.isFa) "فیلدهای سفارشی (اختیاری)" else "Custom fields (optional)", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            customFields.forEachIndexed { idx, field ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.container)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Field(if (str.isFa) "برچسب" else "Label", field.label, { v ->
                                customFields = customFields.toMutableList().also { it[idx] = field.copy(label = v) }
                            })
                        }
                        Text(
                            if (str.isFa) "حذف" else "Remove",
                            color = c.secondary,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(role = Role.Button) {
                                customFields = customFields.filterIndexed { i, _ -> i != idx }
                            }.padding(8.dp),
                        )
                    }
                    Field(if (str.isFa) "مقدار" else "Value", field.value, { v ->
                        customFields = customFields.toMutableList().also { it[idx] = field.copy(value = v) }
                    }, hidden = field.hidden)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (str.isFa) "مقدار پنهان باشد" else "Hide value", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = field.hidden,
                            onCheckedChange = { h ->
                                customFields = customFields.toMutableList().also { it[idx] = field.copy(hidden = h) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                                uncheckedThumbColor = c.outline, uncheckedTrackColor = c.container, uncheckedBorderColor = c.outline,
                            ),
                        )
                    }
                }
            }
            if (customFields.size < app.solina.refuge.core.storage.CustomField.MAX_FIELDS) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(c.container)
                        .clickable(role = Role.Button) { customFields = customFields + app.solina.refuge.core.storage.CustomField() },
                    contentAlignment = Alignment.Center,
                ) { Text(if (str.isFa) "＋ افزودن فیلد سفارشی" else "+ Add custom field", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
        }

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
                                kind = kind,
                                title = title.trim(),
                                category = category.trim(),
                                tags = tags,
                                icon = icon,
                                favorite = favorite,
                                username = username.trim(),
                                password = password,
                                website = website.trim(),
                                totpSecret = totp.trim(),
                                otpType = otpType,
                                totpDigits = otpDigits,
                                totpAlgorithm = otpAlgo,
                                totpCounter = if (otpType == OtpType.HOTP) (otpCounter.toLongOrNull() ?: 0L).coerceAtLeast(0L) else 0L,
                                note = note,
                                customFields = customFields.mapNotNull {
                                    app.solina.refuge.core.storage.CustomField.sanitize(it.label, it.value, it.hidden)
                                }.take(app.solina.refuge.core.storage.CustomField.MAX_FIELDS),
                                bankName = bankName.trim(),
                                cardHolder = holder.trim(),
                                cardNumber = number,
                                cardExpiry = expiry.trim(),
                                cardCvv = cvv,
                                cardPin = cardPin,
                                fullName = fullName.trim(),
                                email = email.trim(),
                                phone = phone.trim(),
                                nationalId = nationalId.trim(),
                                company = company.trim(),
                                address = address.trim(),
                                city = city.trim(),
                                postalCode = postalCode.trim(),
                                country = country.trim(),
                            ),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text(str.save, color = c.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(32.dp))
    }
}
