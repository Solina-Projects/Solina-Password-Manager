package app.solina.passwordmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** توکن‌های رنگ Solina (طبق docs/DESIGN.md). */
@Immutable
class SolinaColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val container: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val text: Color,
    val muted: Color,
    val outline: Color,
    val divider: Color,
    val success: Color,
)

val LightColors = SolinaColors(
    isDark = false,
    bg = Color(0xFFF7FAFA),
    surface = Color(0xFFFFFFFF),
    container = Color(0xFFEEF4F4),
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF0B3B37),
    secondary = Color(0xFFB45309),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF5A3A06),
    text = Color(0xFF0F1F1E),
    muted = Color(0xFF4B5E5E),
    outline = Color(0xFF6B7C7C),
    divider = Color(0xFFE3ECEC),
    success = Color(0xFF15803D),
)

val DarkColors = SolinaColors(
    isDark = true,
    bg = Color(0xFF0B1220),
    surface = Color(0xFF111A2B),
    container = Color(0xFF17223A),
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0B4F49),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFFBBF24),
    secondaryContainer = Color(0xFF2A2110),
    onSecondaryContainer = Color(0xFFFDE68A),
    text = Color(0xFFE6EEF5),
    muted = Color(0xFF9FB0C6),
    outline = Color(0xFF7E8FA8),
    divider = Color(0xFF1F2C47),
    success = Color(0xFF4ADE80),
)

val LocalSolinaColors = staticCompositionLocalOf { LightColors }

object Solina {
    val colors: SolinaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSolinaColors.current
}

@Composable
fun SolinaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            background = c.bg,
            surface = c.surface,
            onSurface = c.text,
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            background = c.bg,
            surface = c.surface,
            onSurface = c.text,
        )
    }
    // رابط فارسی است: چیدمان همیشه راست‌به‌چپ.
    CompositionLocalProvider(
        LocalSolinaColors provides c,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
