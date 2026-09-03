package dev.aicli.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

/**
 * Material 3's own default type scale (`Typography()` with no overrides) — Roboto at Google's
 * official sizes/weights/line-heights, the same scale used across Android system UI and stock
 * Google apps. The one deviation is functional, not stylistic: [labelMedium] goes monospace so
 * version numbers/paths/provider ids read as the code-like values they are — Google's own apps
 * do the same for hashes/code snippets (Play Console, Android Studio). The terminal surface
 * itself is unrelated to this: it draws its own text via android.graphics.Paint on a Canvas,
 * never through Compose Text/MaterialTheme.typography.
 */
val AppTypography = Typography().let { base ->
    base.copy(labelMedium = base.labelMedium.copy(fontFamily = FontFamily.Monospace))
}
