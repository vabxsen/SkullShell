package dev.aicli.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.aicli.app.ui.common.InstallProgressUi
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.provider.api.InstallEvent

/**
 * Unified install/update/repair/uninstall progress presentation — shared by every Providers- and
 * Settings-screen action that triggers one, so there is one install experience, not several.
 * [event]'s shape today is [InstallEvent] directly; other runtime progress types (e.g.
 * [dev.aicli.runtime.bootstrap.BootstrapState]) fold into the same visual shape via a UI-layer
 * mapper (see `ui/install/InstallEventMapper.kt`) without changing this
 * component's public contract.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallProgressSheet(
    progress: InstallProgressUi,
    onDismiss: () -> Unit,
    onOpenProvider: (() -> Unit)? = null,
) {
    val event = progress.latestEvent
    ModalBottomSheet(onDismissRequest = { if (progress.done) onDismiss() }) {
        Column(Modifier.padding(horizontal = Dimens.space16).padding(bottom = Dimens.space24)) {
            Text(
                if (progress.done) progress.displayName else "Installing ${progress.displayName}",
                style = MaterialTheme.typography.titleMedium,
            )
            Column(Modifier.padding(top = Dimens.space12)) {
                when (event) {
                    is InstallEvent.Progress -> {
                        Text(event.step, style = MaterialTheme.typography.bodyMedium)
                        val fraction = event.fraction
                        if (fraction != null) {
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = Dimens.space8))
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = Dimens.space8))
                        }
                        event.logLine?.let {
                            ExpandableDetails("Technical details", it, modifier = Modifier.padding(top = Dimens.space12).fillMaxWidth())
                        }
                    }
                    is InstallEvent.Completed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "${progress.displayName} is ready",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = Dimens.space8),
                            )
                        }
                    }
                    is InstallEvent.Failed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                "Failed at '${event.step}'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = Dimens.space8),
                            )
                        }
                        ExpandableDetails("Technical details", event.reason, modifier = Modifier.padding(top = Dimens.space12).fillMaxWidth())
                    }
                    null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (progress.done) {
                Row(modifier = Modifier.padding(top = Dimens.space16)) {
                    if (event is InstallEvent.Completed && onOpenProvider != null) {
                        Button(onClick = { onOpenProvider(); onDismiss() }) { Text("Open ${progress.displayName}") }
                    } else {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}
