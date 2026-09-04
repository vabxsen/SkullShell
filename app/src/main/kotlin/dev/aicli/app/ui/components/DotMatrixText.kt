package dev.aicli.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import dev.aicli.app.ui.design.SkullTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/** 5-wide x 7-tall bitmap glyphs — '1' = lit dot, anything else = unlit. Classic LED-signage font. */
private val DOT_FONT: Map<Char, List<String>> = mapOf(
    'S' to listOf("01110", "10000", "10000", "01110", "00001", "00001", "01110"),
    'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    ' ' to listOf("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
)

private const val GLYPH_COLS = 5
private const val GLYPH_ROWS = 7

/**
 * Renders [text] as a genuine dot-matrix / LED-signage display: every dot position in the grid
 * is drawn (both lit and unlit), not just a blocky font — that's what actually reads as
 * "dot matrix" rather than a stylized typeface. Sized via [aspectRatio] driven by the character
 * count rather than a fixed dot size, so it scales to fit whatever width [modifier] gives it
 * (phone, tablet, landscape) instead of assuming one screen size — pass e.g.
 * `Modifier.fillMaxWidth(0.7f)` to control how wide the display reads on screen.
 */
@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    litColor: Color = SkullTheme.colors.ink,
    unlitColor: Color = SkullTheme.colors.inkFaint.copy(alpha = 0.28f),
) {
    val chars = text.uppercase().filter { it == ' ' || DOT_FONT.containsKey(it) }
    if (chars.isEmpty()) return

    // One "cell" = one dot + the gap after it. Each glyph is GLYPH_COLS cells wide plus one
    // extra cell of inter-character spacing (dropped after the last character).
    val cellsWide = chars.length * (GLYPH_COLS + 1) - 1

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(cellsWide.toFloat() / GLYPH_ROWS.toFloat())) {
        val cellPx = size.width / cellsWide
        val dotRadius = cellPx * 0.32f
        var cellX = 0
        for (ch in chars) {
            val glyph = DOT_FONT.getValue(ch)
            for (row in 0 until GLYPH_ROWS) {
                val bits = glyph[row]
                for (col in 0 until GLYPH_COLS) {
                    val lit = bits.getOrNull(col) == '1'
                    val cx = (cellX + col + 0.5f) * cellPx
                    val cy = (row + 0.5f) * cellPx
                    drawCircle(
                        color = if (lit) litColor else unlitColor,
                        radius = dotRadius,
                        center = Offset(cx, cy),
                    )
                }
            }
            cellX += GLYPH_COLS + 1
        }
    }
}
