package dev.aicli.app.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, glyph: ImageVector? = null) {
    Button(onClick, modifier.heightIn(min = Metrics.touch), enabled = enabled) { ButtonLabel(label, glyph) }
}

@Composable
fun TonalButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, glyph: ImageVector? = null) {
    FilledTonalButton(onClick, modifier.heightIn(min = Metrics.touch), enabled = enabled) { ButtonLabel(label, glyph) }
}

@Composable
fun OutlineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, glyph: ImageVector? = null) {
    OutlinedButton(onClick, modifier.heightIn(min = Metrics.touch), enabled = enabled) { ButtonLabel(label, glyph) }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, glyph: ImageVector? = null) {
    TextButton(onClick, modifier.heightIn(min = Metrics.touch), enabled = enabled) { ButtonLabel(label, glyph) }
}

@Composable
private fun RowScope.ButtonLabel(label: String, glyph: ImageVector?) {
    if (glyph != null) {
        androidx.compose.material3.Icon(glyph, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    androidx.compose.material3.Text(label, maxLines = 2)
}

@Composable
fun IconAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier,
               enabled: Boolean = true, tint: Color = SkullTheme.colors.inkMuted, size: Dp = 24.dp) {
    IconButton(onClick, modifier.size(Metrics.touch), enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint)) {
        androidx.compose.material3.Icon(icon, contentDescription, Modifier.size(size))
    }
}

@Composable
fun KeyCap(label: String, modifier: Modifier = Modifier, active: Boolean = false, width: Dp = 58.dp, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier.size(width, Metrics.touch), shape = MaterialTheme.shapes.small,
        color = if (active) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (active) scheme.onPrimaryContainer else scheme.onSurface) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
