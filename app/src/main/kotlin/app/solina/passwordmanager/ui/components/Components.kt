package app.solina.passwordmanager.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.ui.theme.Solina

val Mono = FontFamily.Monospace

/** کپی در کلیپ‌بورد با علامت «حساس» تا در پیش‌نمایش کلیپ‌بورد اندروید نمایش داده نشود. */
fun copySensitive(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= 33) {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        } else {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    cm.setPrimaryClip(clip)
}

@Composable
fun SIcon(icon: ImageVector, tint: Color, size: Dp = 22.dp, description: String? = null) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    tint: Color = Solina.colors.primary,
    background: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { SIcon(icon, tint, 22.dp, description) }
}

@Composable
fun ItemAvatar(title: String, size: Dp = 44.dp) {
    val c = Solina.colors
    val palette = if (c.isDark) {
        listOf(
            Color(0xFF0B4F49) to Color(0xFFCCFBF1),
            Color(0xFF4A2F05) to Color(0xFFFDE68A),
            Color(0xFF3B1A2B) to Color(0xFFFBCFE8),
            Color(0xFF1E2A5A) to Color(0xFFC7D2FE),
        )
    } else {
        listOf(
            Color(0xFFCCFBF1) to Color(0xFF0B4F49),
            Color(0xFFFEF3C7) to Color(0xFF5A3A06),
            Color(0xFFFFE4E6) to Color(0xFF881337),
            Color(0xFFE0E7FF) to Color(0xFF312E81),
        )
    }
    val (bg, fg) = palette[(title.hashCode() and 0x7fffffff) % palette.size]
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .semantics { contentDescription = "" },
        contentAlignment = Alignment.Center,
    ) {
        Text(title.take(1).uppercase(), color = fg, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) SIcon(icon, fg, 13.dp)
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Solina.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) c.primaryContainer else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(1.dp, c.outline), shape))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) c.onPrimaryContainer else c.text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** حلقهٔ شمارش معکوس TOTP؛ در ۵ ثانیهٔ آخر رنگ هشدار می‌گیرد و عدد باقی‌مانده هم نمایش داده می‌شود. */
@Composable
fun TotpRing(remainingMillis: Long, periodSeconds: Int, size: Dp, faSeconds: String, color: Color, track: Color, textColor: Color) {
    val fraction = remainingMillis.toFloat() / (periodSeconds * 1000f)
    Box(
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = "$faSeconds ثانیه تا کد بعدی" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = this.size.minDimension * 0.1f, cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = stroke)
        }
        Text(faSeconds, color = textColor, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StrengthBar(level: Int, label: String) {
    val c = Solina.colors
    val color = when {
        level >= 4 -> c.success
        level >= 3 -> c.primary
        else -> c.secondary
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f, fill = false)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(width = 28.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i < level) color else c.divider),
                )
            }
        }
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    background: Color = Solina.colors.surface,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .padding(padding),
    ) { content() }
}

@Composable
fun ColumnLabel(label: String, value: String, mono: Boolean = false, modifier: Modifier = Modifier) {
    val c = Solina.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = c.muted, fontSize = 12.sp)
        Text(
            value,
            color = c.text,
            fontSize = if (mono) 17.sp else 16.sp,
            fontFamily = if (mono) Mono else FontFamily.Default,
            textAlign = if (mono) TextAlign.Left else null,
        )
    }
}

@Composable
fun RowScope.Spacer1() = Box(Modifier.weight(1f))
