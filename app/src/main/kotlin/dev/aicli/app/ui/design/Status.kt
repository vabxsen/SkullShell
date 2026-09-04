package dev.aicli.app.ui.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class StatusTone { NEUTRAL, SUCCESS, WARNING, ERROR, INFO }

/** Status uses a label and symbol; it does not need a separate container. */
@Composable
fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier, glyph: ImageVector? = defaultGlyph(tone)) {
    val colors = SkullTheme.colors
    val tint = when (tone) {
        StatusTone.SUCCESS -> colors.success
        StatusTone.WARNING -> colors.warning
        StatusTone.ERROR -> colors.error
        StatusTone.INFO -> colors.accent
        StatusTone.NEUTRAL -> colors.inkMuted
    }
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) Glyph(glyph, null, size = 13.dp, tint = tint, modifier = Modifier.padding(end = 5.dp))
        Label(text, color = tint, style = SkullTheme.type.bodySm)
    }
}

private fun defaultGlyph(tone: StatusTone): ImageVector? = when (tone) {
    StatusTone.NEUTRAL -> null
    StatusTone.SUCCESS -> Glyphs.CheckCircle
    StatusTone.WARNING -> Glyphs.Alert
    StatusTone.ERROR -> Glyphs.ErrorCircle
    StatusTone.INFO -> Glyphs.Info
}
