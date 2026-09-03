package dev.aicli.app.ui.terminal

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A terminal is conventionally a fixed-dark, tool-like surface regardless of the host app's own
 * theme — real terminal emulators don't follow a "light mode" toggle. This screen pins its own
 * violet-accented dark [androidx.compose.material3.ColorScheme] instead of following
 * [dev.aicli.app.ui.theme.AiCliTheme]'s Material You / baseline choice, matching the palette
 * already documented (but never wired up before this screen) in docs/DESIGN_SYSTEM.md.
 */
private val TerminalViolet = Color(0xFF8B5CF6)
private val TerminalCyan = Color(0xFF22D3EE)
private val TerminalBackground = Color(0xFF0B0B12)
private val TerminalSurface = Color(0xFF15151F)
private val TerminalSurfaceHigh = Color(0xFF1C1C29)
private val TerminalSurfaceHighest = Color(0xFF242436)
private val TerminalOutline = Color(0xFF2A2A3D)
private val TerminalOnSurface = Color(0xFFF2F2F5)
private val TerminalOnSurfaceVariant = Color(0xFF9CA3AF)

val TerminalDarkColorScheme = darkColorScheme(
    primary = TerminalViolet,
    onPrimary = Color.White,
    primaryContainer = TerminalViolet,
    onPrimaryContainer = Color.White,
    secondary = TerminalCyan,
    onSecondary = Color(0xFF00363D),
    background = TerminalBackground,
    onBackground = TerminalOnSurface,
    surface = TerminalBackground,
    onSurface = TerminalOnSurface,
    surfaceVariant = TerminalSurface,
    onSurfaceVariant = TerminalOnSurfaceVariant,
    surfaceContainer = TerminalSurface,
    surfaceContainerLow = TerminalBackground,
    surfaceContainerHigh = TerminalSurfaceHigh,
    surfaceContainerHighest = TerminalSurfaceHighest,
    outline = TerminalOutline,
    outlineVariant = TerminalOutline,
    error = Color(0xFFF87171),
    onError = Color.White,
)
