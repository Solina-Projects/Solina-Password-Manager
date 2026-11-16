package app.solina.passwordmanager.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.core.Totp
import app.solina.passwordmanager.ui.theme.Solina

/**
 * حرکت‌های رابط کاربری، با الهام از الگوهای D3 Motion Resources
 * (d3motion.pro/resources — کامپوننت‌های HTML رایگان).
 *
 * نکتهٔ مهم: آن فایل‌ها HTML/CSS/SVG برای وب و ویدیو هستند و در اپ اندرویدی
 * (Jetpack Compose) اجرا نمی‌شوند؛ آوردنشان با WebView هم حس بومی اپ را خراب
 * می‌کند و با قانون «بدون INTERNET» این برنامه نمی‌سازد. پس به‌جای کپی فایل،
 * «حرکت» آن‌ها — زمان‌بندی، فنر و ترتیب — با کد کاملاً بومی Compose بازنویسی
 * شده است. هیچ فایل HTML، هیچ WebView و هیچ وابستگی جدیدی اضافه نشده و هیچ
 * لوگو یا برندی از آن کارت‌ها استفاده نشده است.
 */

/**
 * کد TOTP با رقم‌های چرخان (با الهام از کارت «Two-factor verification» در D3):
 * وقتی کد عوض می‌شود، کد قبلی به بالا سر می‌خورد و محو می‌شود و کد تازه از
 * پایین می‌آید؛ مثل غلتک عدد.
 */
@Composable
fun RollingTotpCode(
    code: String,
    color: Color,
    fontSize: TextUnit,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = code,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn(tween(200))).togetherWith(
                slideOutVertically { -it / 2 } + fadeOut(tween(200)),
            )
        },
        label = "totp-code-roll",
        modifier = modifier,
    ) { target ->
        Text(
            Totp.group(target),
            color = color,
            fontFamily = Mono,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = letterSpacing,
            textAlign = TextAlign.Left,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * ردیف فیلتر با قرص لغزنده (با الهام از «Segmented control» در D3):
 * به‌جای اینکه چیپ انتخاب‌شده ناگهان رنگ عوض کند، یک قرص با فنر از چیپ
 * قبلی سر می‌خورد روی چیپ تازه و رنگ متن هم نرم عوض می‌شود.
 * جای قرص با اندازه‌گیری واقعی چیپ‌ها به دست می‌آید، پس در چیدمان
 * راست‌به‌چپ و در حالت اسکرول هم درست کار می‌کند.
 */
@Composable
fun AnimatedFilterRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Solina.colors
    // مرز هر چیپ در مختصات ردیف (پیکسل)؛ با هر چیدمان تازه به‌روز می‌شود.
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    var targetLeft by remember { mutableFloatStateOf(0f) }
    var targetWidth by remember { mutableFloatStateOf(0f) }
    val pillLeft by animateFloatAsState(
        targetValue = targetLeft,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "filter-pill-left",
    )
    val pillWidth by animateFloatAsState(
        targetValue = targetWidth,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "filter-pill-width",
    )
    LaunchedEffect(selected) {
        bounds[selected]?.let { targetLeft = it.left; targetWidth = it.width }
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .drawBehind {
                if (pillWidth > 0f) {
                    drawRoundRect(
                        color = c.primaryContainer,
                        topLeft = Offset(pillLeft, 0f),
                        size = Size(pillWidth, size.height),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val isSelected = i == selected
            val textColor by animateColorAsState(
                targetValue = if (isSelected) c.onPrimaryContainer else c.text,
                animationSpec = tween(durationMillis = 200),
                label = "filter-text",
            )
            Box(
                Modifier
                    .height(40.dp)
                    .clip(shape)
                    .then(if (isSelected) Modifier else Modifier.border(BorderStroke(1.dp, c.outline), shape))
                    .clickable(role = Role.Tab, onClick = { onSelect(i) })
                    .onGloballyPositioned { coords ->
                        val r = coords.boundsInParent()
                        bounds[i] = r
                        if (isSelected) {
                            targetLeft = r.left
                            targetWidth = r.width
                        }
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text(
                    label,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
