package dev.aicli.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A dark-first palette built for an AI coding workspace, not a generic terminal emulator:
// desaturated indigo/violet accent (distinguishes "AI" surfaces — provider cards, running
// sessions — from plain shell chrome), a near-black (not pure black) canvas so OLED contrast
// doesn't fight legibility, and a single warm accent reserved for state that needs attention
// (errors, auth-required) so it doesn't compete with syntax-colored terminal output.
private val AccentIndigo = Color(0xFF7C8CFF)
private val AccentIndigoDim = Color(0xFF4A4FE0)
private val SurfaceDark = Color(0xFF0E0F13)
private val SurfaceDarkElevated = Color(0xFF16181F)
private val SurfaceDarkHighest = Color(0xFF1F212B)
private val OnSurfaceDark = Color(0xFFE7E8EC)
private val OnSurfaceDim = Color(0xFF9A9CAB)
private val WarnAmber = Color(0xFFE0A93A)
private val ErrorRed = Color(0xFFE0616B)
private val SuccessGreen = Color(0xFF5FCE8F)

private val DarkColors = darkColorScheme(
    primary = AccentIndigo,
    onPrimary = Color(0xFF0B0B14),
    primaryContainer = AccentIndigoDim,
    onPrimaryContainer = Color(0xFFE9EAFF),
    secondary = OnSurfaceDim,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = OnSurfaceDim,
    surfaceContainer = SurfaceDarkElevated,
    surfaceContainerHigh = SurfaceDarkHighest,
    surfaceContainerHighest = SurfaceDarkHighest,
    error = ErrorRed,
    onError = Color(0xFF1A0405),
    outline = Color(0xFF34363F),
    outlineVariant = Color(0xFF24262E),
)

private val LightColors = lightColorScheme(
    primary = AccentIndigoDim,
    background = Color(0xFFFAFAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F0F5),
    error = Color(0xFFB3261E),
)

object AiCliColors {
    val warning = WarnAmber
    val success = SuccessGreen
}

private val AppTypography = androidx.compose.material3.Typography(
    titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun AiCliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
