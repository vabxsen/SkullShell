package dev.aicli.app.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class SkullColors(
    val bg: Color, val panel: Color, val panelHi: Color,
    val line: Color, val lineStrong: Color,
    val ink: Color, val inkMuted: Color, val inkFaint: Color, val onInk: Color,
    val scrim: Color, val isDark: Boolean,
    val accent: Color, val onAccent: Color, val accentSoft: Color,
    val success: Color, val warning: Color, val error: Color, val violet: Color,
)

val DarkInk = SkullColors(
    bg = Color(0xFF141518), panel = Color(0xFF1A1C20), panelHi = Color(0xFF25282E),
    line = Color(0xFF2C2F35), lineStrong = Color(0xFF454A54),
    ink = Color(0xFFE8EAF0), inkMuted = Color(0xFFA3A8B3), inkFaint = Color(0xFF858B98),
    onInk = Color(0xFF17191E), scrim = Color(0x99000000), isDark = true,
    accent = Color(0xFFA7C4FF), onAccent = Color(0xFF152C50), accentSoft = Color(0xFF242F43),
    success = Color(0xFF92BFA2), warning = Color(0xFFD6B57C), error = Color(0xFFE3A2A5), violet = Color(0xFFA7C4FF),
)

val LightInk = SkullColors(
    bg = Color(0xFFFFFFFF), panel = Color(0xFFF7F8FA), panelHi = Color(0xFFEBEDF1),
    line = Color(0xFFE1E4E9), lineStrong = Color(0xFFB9C0CA),
    ink = Color(0xFF21252D), inkMuted = Color(0xFF626B79), inkFaint = Color(0xFF707989),
    onInk = Color(0xFFFFFFFF), scrim = Color(0x66000000), isDark = false,
    accent = Color(0xFF345D9E), onAccent = Color(0xFFFFFFFF), accentSoft = Color(0xFFECF1FA),
    success = Color(0xFF3B6D4E), warning = Color(0xFF865F23), error = Color(0xFFAA4248), violet = Color(0xFF345D9E),
)

object Space {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x10 = 40.dp
    val x12 = 48.dp
    val x16 = 64.dp
}

object Shapes {
    val pill = RoundedCornerShape(percent = 50)
    val small = RoundedCornerShape(12.dp)
    val control = RoundedCornerShape(16.dp)
    val panel = RoundedCornerShape(24.dp)
    val modal = RoundedCornerShape(28.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

object Metrics {
    val hairline = 1.dp
    val glyphSm = 16.dp
    val glyphMd = 24.dp
    val glyphLg = 24.dp
    val glyphXl = 28.dp
    val control = 48.dp
    val touch = 48.dp
    val gutter = 16.dp
    val topBar = 64.dp
    val navBar = 80.dp
    val rail = 96.dp
    val maxContent = 920.dp
}
