package app.solina.refuge.ui.theme

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
import app.solina.refuge.core.security.AppLanguage
import app.solina.refuge.core.security.ThemeMode
import app.solina.refuge.ui.i18n.AppStrings
import app.solina.refuge.ui.i18n.LocalAppStrings
import app.solina.refuge.ui.i18n.stringsFor

/** توکن‌های رنگ Refuge (روشن، تاریک و AMOLED). */
@Immutable
class RefugeColors(
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

val LightColors = RefugeColors(
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

val DarkColors = RefugeColors(
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

val AmoledColors = RefugeColors(
    isDark = true,
    bg = Color(0xFF000000),
    surface = Color(0xFF0A0D12),
    container = Color(0xFF121822),
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF083F3A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFFBBF24),
    secondaryContainer = Color(0xFF241B0B),
    onSecondaryContainer = Color(0xFFFDE68A),
    text = Color(0xFFF1F5F9),
    muted = Color(0xFF94A3B8),
    outline = Color(0xFF64748B),
    divider = Color(0xFF1E293B),
    success = Color(0xFF4ADE80),
)

val LocalRefugeColors = staticCompositionLocalOf { LightColors }

object Refuge {
    val colors: RefugeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRefugeColors.current

    val strings: AppStrings
        @Composable
        @ReadOnlyComposable
        get() = LocalAppStrings.current
}

@Composable
fun RefugeTheme(
    themeMode: ThemeMode = ThemeMode.System,
    language: AppLanguage = AppLanguage.FA,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val c = when (themeMode) {
        ThemeMode.Light -> LightColors
        ThemeMode.Dark -> DarkColors
        ThemeMode.Amoled -> AmoledColors
        ThemeMode.System -> if (systemDark) DarkColors else LightColors
    }
    val scheme = if (c.isDark) {
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
    val direction = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalRefugeColors provides c,
        LocalAppStrings provides stringsFor(language),
        LocalLayoutDirection provides direction,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
