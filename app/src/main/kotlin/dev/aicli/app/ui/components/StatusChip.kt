package dev.aicli.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.app.ui.theme.LocalExtendedColors

/** Semantic status meaning, kept separate from any specific screen's state enum. */
enum class StatusTone { NEUTRAL, SUCCESS, WARNING, ERROR, INFO }

/**
 * A subtle, low-emphasis status indicator — never a "giant glowing badge". Status is never
 * conveyed by color alone: pass [icon] whenever one is available so the chip still reads
 * correctly without color (accessibility, colorblind users).
 */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val extended = LocalExtendedColors.current
    val (container, onContainer) = when (tone) {
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.SUCCESS -> extended.successContainer to extended.onSuccessContainer
        StatusTone.WARNING -> extended.warningContainer to extended.onWarningContainer
        StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.INFO -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = container,
        contentColor = onContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.space8, vertical = Dimens.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSmall - 4.dp).padding(end = Dimens.space4),
                    tint = onContainer,
                )
            }
            Text(text, style = MaterialTheme.typography.labelSmall, color = onContainer)
        }
    }
}
