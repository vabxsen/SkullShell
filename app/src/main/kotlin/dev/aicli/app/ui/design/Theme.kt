package dev.aicli.app.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val BlueLightScheme = lightColorScheme(
    primary = Color(0xFF0B57D0), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF465D91), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E9FF), onSecondaryContainer = Color(0xFF172F61),
    tertiary = Color(0xFF146C52), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4EED0), onTertiaryContainer = Color(0xFF002114),
    background = Color(0xFFF8FAFD), onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFF8FAFD), onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE1E3E9), onSurfaceVariant = Color(0xFF444746),
    outline = Color(0xFF747775), outlineVariant = Color(0xFFC4C7C5),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4F9),
    surfaceContainer = Color(0xFFE9EEF6), surfaceContainerHigh = Color(0xFFE1E9F4),
    surfaceContainerHighest = Color(0xFFDCE4EF), surfaceTint = Color(0xFF0B57D0),
)
val BlueDarkScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFB2C6F7), onSecondary = Color(0xFF172F61),
    secondaryContainer = Color(0xFF304779), onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Color(0xFF6DD58C), onTertiary = Color(0xFF00391D),
    tertiaryContainer = Color(0xFF00522B), onTertiaryContainer = Color(0xFFA4F5B7),
    background = Color(0xFF111318), onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF111318), onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C7C5),
    outline = Color(0xFF8E918F), outlineVariant = Color(0xFF444746),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1E2125), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A), surfaceTint = Color(0xFFA8C7FA),
)

private fun ColorScheme.toSkullColors(dark: Boolean) = SkullColors(
    bg = surface, panel = surfaceContainerLow, panelHi = surfaceContainerHigh,
    line = outlineVariant, lineStrong = outline,
    ink = onSurface, inkMuted = onSurfaceVariant, inkFaint = onSurfaceVariant, onInk = surface,
    scrim = scrim.copy(alpha = .6f), isDark = dark,
    accent = primary, onAccent = onPrimary, accentSoft = primaryContainer,
    success = if (dark) Color(0xFF81C995) else Color(0xFF137333),
    warning = if (dark) Color(0xFFFDD663) else Color(0xFF8A5700), error = error, violet = tertiary,
)

val LocalSkullColors = staticCompositionLocalOf { DarkInk }
val LocalSkullType = staticCompositionLocalOf { SkullTypography }

@Composable
fun SkullTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> BlueDarkScheme
        else -> BlueLightScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = scheme, typography = MaterialTypography) {
        CompositionLocalProvider(LocalSkullColors provides scheme.toSkullColors(darkTheme),
            LocalSkullType provides SkullTypography, content = content)
    }
}

@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (lightStatus != null) controller.isAppearanceLightStatusBars = lightStatus
            if (lightNavigation != null) controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
    MaterialTheme(colorScheme = BlueDarkScheme, typography = MaterialTypography) {
        CompositionLocalProvider(LocalSkullColors provides DarkInk, LocalSkullType provides SkullTypography, content = content)
    }
}

object SkullTheme {
    val colors: SkullColors @Composable @ReadOnlyComposable get() = LocalSkullColors.current
    val type: SkullType @Composable @ReadOnlyComposable get() = LocalSkullType.current
}
