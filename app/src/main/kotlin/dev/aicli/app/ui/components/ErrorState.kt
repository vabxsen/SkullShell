package dev.aicli.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import dev.aicli.app.ui.theme.Dimens

/**
 * Calm, actionable Material 3 error presentation — never a giant red panel. Status is
 * communicated with an icon + text, not color alone.
 */
@Composable
fun ErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.ErrorOutline,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Retry",
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    technicalDetails: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Dimens.space32),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(bottom = Dimens.space16),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimens.space8),
        )
        if (onRetry != null || (secondaryLabel != null && onSecondary != null)) {
            Row(modifier = Modifier.padding(top = Dimens.space20)) {
                if (onRetry != null) {
                    Button(onClick = onRetry) { Text(retryLabel) }
                }
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(onClick = onSecondary, modifier = Modifier.padding(start = Dimens.space12)) { Text(secondaryLabel) }
                }
            }
        }
        if (technicalDetails != null) {
            ExpandableDetails(
                label = "Technical details",
                content = technicalDetails,
                modifier = Modifier.padding(top = Dimens.space16).fillMaxWidth(),
            )
        }
    }
}
