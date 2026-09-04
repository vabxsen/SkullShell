package dev.aicli.app.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

@Composable
fun Glyph(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
          size: Dp = Metrics.glyphMd, tint: Color = SkullTheme.colors.ink) {
    Icon(icon, contentDescription, modifier.size(size), tint = tint)
}

@Composable
fun Modifier.pressable(enabled: Boolean = true, highlight: Color = SkullTheme.colors.panelHi, onClick: () -> Unit): Modifier =
    clickable(enabled = enabled, onClick = onClick)

@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = SkullTheme.colors.line) { HorizontalDivider(modifier, color = color) }
@Composable
fun VRule(modifier: Modifier = Modifier, color: Color = SkullTheme.colors.line) { VerticalDivider(modifier, color = color) }

@Composable
fun Panel(modifier: Modifier = Modifier, fill: Color = MaterialTheme.colorScheme.surfaceContainerLow,
          border: Color = Color.Transparent, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val stroke = if (border == Color.Transparent) null else BorderStroke(Metrics.hairline, border)
    val colors = CardDefaults.cardColors(containerColor = fill)
    if (onClick == null) Card(modifier.fillMaxWidth(), shape = Shapes.panel, colors = colors, border = stroke, content = content)
    else Card(onClick, modifier.fillMaxWidth(), shape = Shapes.panel, colors = colors, border = stroke, content = content)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = Metrics.touch), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = SkullTheme.type.heading)
        Spacer(Modifier.weight(1f))
        action?.invoke(this)
    }
}

@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = SkullTheme.type.display)
        if (subtitle != null) Text(subtitle, color = SkullTheme.colors.inkMuted, modifier = Modifier.padding(top = Space.x2))
    }
}
