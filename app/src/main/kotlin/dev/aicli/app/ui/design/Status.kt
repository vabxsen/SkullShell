package dev.aicli.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Semantic status, independent of any one screen's state enum. */
enum class StatusTone { NEUTRAL, SUCCESS, WARNING, ERROR, INFO }

/**
 * Status without colour. Every tone carries a distinct glyph *and* a distinct box treatment, so
 * the chip survives a greyscale palette, a colourblind reader and a glance:
 *
 *  - NEUTRAL - hairline box, muted ink, no glyph. Nothing is happening.
 *  - INFO    - hairline box, muted ink, info glyph.
 *  - SUCCESS - hairline box, full ink, check glyph.
 *  - WARNING - emphasised hairline, full ink, triangle glyph.
 *  - ERROR   - fully inverted block. The only chip that inverts, so it is impossible to miss.
 *
 * That escalation (grey -> ink -> stronger border -> inversion) is the whole severity scale.
 */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    glyph: ImageVector? = defaultGlyph(tone),
) {
    val colors = SkullTheme.colors
    val shape = Shapes.pill
    val fill = if (tone == StatusTone.ERROR) colors.ink else Color.Transparent
    val border = when (tone) {
        StatusTone.ERROR -> colors.ink
        StatusTone.WARNING -> colors.lineStrong
        else -> colors.line
    }
    val content = when (tone) {
        StatusTone.ERROR -> colors.onInk
        StatusTone.SUCCESS, StatusTone.WARNING -> colors.ink
        StatusTone.NEUTRAL, StatusTone.INFO -> colors.inkMuted
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(Metrics.hairline, border, shape)
            .padding(start = Space.x3, end = Space.x3, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(
                glyph,
                null,
                size = Metrics.glyphSm,
                tint = content,
                modifier = Modifier.padding(end = Space.x2),
            )
        }
        Label(text, color = content, style = SkullTheme.type.labelSm)
    }
}

private fun defaultGlyph(tone: StatusTone): ImageVector? = when (tone) {
    StatusTone.NEUTRAL -> null
    StatusTone.SUCCESS -> Glyphs.CheckCircle
    StatusTone.WARNING -> Glyphs.Alert
    StatusTone.ERROR -> Glyphs.ErrorCircle
    StatusTone.INFO -> Glyphs.Info
}

