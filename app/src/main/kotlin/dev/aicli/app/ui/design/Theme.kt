package dev.aicli.app.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalSkullColors = staticCompositionLocalOf { DarkInk }
val LocalSkullType = staticCompositionLocalOf { SkullTypography }

/**
 * The design system's entry point, and the only theme in the app — there is no MaterialTheme
 * anywhere in this codebase, and no Material component library on the classpath. Every widget
 * used by the screens is defined in this package on top of Compose Foundation.
 *
 * Two schemes, both monochrome, exact inversions of each other in role if not in value
 * (see [DarkInk]/[LightInk]). There is no dynamic/wallpaper colour: a palette derived from
 * someone's wallpaper is the opposite of what this design is.
 */
@Composable
fun SkullTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkInk else LightInk

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalSkullColors provides colors,
        LocalSkullType provides SkullTypography,
        content = content,
    )
}

/**
 * Pinned dark scheme for the terminal surface. A terminal is a fixed-dark tool regardless of
 * the host app's setting — real emulators don't follow a light-mode toggle — so the terminal
 * screen wraps itself in this rather than inheriting the user's [SkullTheme] choice.
 */
@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSkullColors provides DarkInk,
        LocalSkullType provides SkullTypography,
        content = content,
    )
}

object SkullTheme {
    val colors: SkullColors
        @Composable @ReadOnlyComposable get() = LocalSkullColors.current

    val type: SkullType
        @Composable @ReadOnlyComposable get() = LocalSkullType.current
}
