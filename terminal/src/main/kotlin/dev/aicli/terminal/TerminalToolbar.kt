package dev.aicli.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A session's connection state, as the toolbar needs it — deliberately smaller than the app's
 *  own SessionRunState so this module stays free of an app-module dependency. */
enum class ConnectionStatus { RUNNING, IDLE, EXITED, ERROR }

private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.RUNNING -> "Running"
    ConnectionStatus.IDLE -> "Idle"
    ConnectionStatus.EXITED -> "Exited"
    ConnectionStatus.ERROR -> "Error"
}

/**
 * Compact toolbar for the terminal screen — provider identity, session name, working directory,
 * and connection status, plus the small set of actions a session needs. Stateless: the caller
 * (app module) resolves provider icon/name from [dev.aicli.provider.api.AIProvider] since this
 * module has no dependency on provider-api.
 */
@Composable
fun TerminalToolbar(
    providerIcon: ImageVector?,
    providerName: String?,
    sessionTitle: String,
    workingDirectorySuffix: String?,
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier,
    onSessionSwitcher: () -> Unit = {},
    onNewSession: () -> Unit = {},
    onClear: () -> Unit = {},
    onRestart: () -> Unit = {},
    onStop: (() -> Unit)? = null,
) {
    var moreOpen by remember { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSessionSwitcher) {
                Icon(providerIcon ?: Icons.Filled.Terminal, contentDescription = "Switch session")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    if (providerName != null) "$providerName — $sessionTitle" else sessionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConnectionDot(connectionStatus)
                    Text(
                        buildString {
                            append(connectionLabel(connectionStatus))
                            if (!workingDirectorySuffix.isNullOrBlank()) append(" · $workingDirectorySuffix")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onNewSession) { Icon(Icons.Filled.Add, contentDescription = "New session") }
            Box {
                IconButton(onClick = { moreOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More session actions") }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(text = { Text("Clear") }, onClick = { moreOpen = false; onClear() })
                    DropdownMenuItem(text = { Text("Restart") }, onClick = { moreOpen = false; onRestart() })
                    if (onStop != null) {
                        DropdownMenuItem(text = { Text("Stop") }, onClick = { moreOpen = false; onStop() })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionDot(status: ConnectionStatus) {
    val color = when (status) {
        ConnectionStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        ConnectionStatus.IDLE, ConnectionStatus.EXITED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(6.dp)
            .background(color, CircleShape),
    )
}

