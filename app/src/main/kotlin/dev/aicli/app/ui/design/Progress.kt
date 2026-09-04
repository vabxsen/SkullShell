package dev.aicli.app.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 32.dp, color: Color = SkullTheme.colors.accent,
            track: Color = SkullTheme.colors.panelHi, strokeWidth: Dp = 3.dp) {
    CircularProgressIndicator(modifier.size(size), color = color, trackColor = track, strokeWidth = strokeWidth)
}
@Composable
fun LinearProgress(modifier: Modifier = Modifier, fraction: Float? = null, color: Color = SkullTheme.colors.accent,
                   track: Color = SkullTheme.colors.panelHi) {
    if (fraction == null) LinearProgressIndicator(modifier.fillMaxWidth(), color = color, trackColor = track)
    else LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier.fillMaxWidth(), color = color, trackColor = track)
}
@Composable
fun LoadingBody(modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            Spinner()
            if (label != null) Text(label, color = SkullTheme.colors.inkMuted, style = SkullTheme.type.bodySm)
        }
    }
}
