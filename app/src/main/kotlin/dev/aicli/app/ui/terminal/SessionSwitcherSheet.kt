package dev.aicli.app.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.data.SessionMeta
import dev.aicli.app.data.SessionRunState
import dev.aicli.app.data.TerminalSessionController
import dev.aicli.app.ui.components.SectionHeader
import dev.aicli.app.ui.components.StatusChip
import dev.aicli.app.ui.components.StatusTone
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.provider.api.AIProvider

/** Provider + project + status per session — the polished switcher the brief asks for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSwitcherSheet(
    sessions: List<SessionMeta>,
    providersById: Map<String, AIProvider>,
    controllerFor: (String) -> TerminalSessionController?,
    onSelect: (String) -> Unit,
    onNewShell: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SectionHeader("Sessions", modifier = Modifier.padding(horizontal = Dimens.space16))
        LazyColumn {
            items(sessions, key = { it.id }) { session ->
                SessionRow(
                    session = session,
                    provider = session.providerId?.let { providersById[it] },
                    controller = controllerFor(session.id),
                    onClick = { onSelect(session.id); onDismiss() },
                )
            }
        }
        TextButton(
            onClick = { onNewShell(); onDismiss() },
            modifier = Modifier.padding(horizontal = Dimens.space16, vertical = Dimens.space8),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = Dimens.space4))
            Text("New shell")
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionMeta,
    provider: AIProvider?,
    controller: TerminalSessionController?,
    onClick: () -> Unit,
) {
    val runState = controller?.runState?.collectAsStateWithLifecycle()?.value
    val (label, tone) = statusDescriptor(runState)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Filled.Terminal, contentDescription = null) },
        headlineContent = { Text(session.title) },
        supportingContent = { Text(provider?.displayName ?: "Shell · ${session.workingDirectory}") },
        trailingContent = { StatusChip(label, tone) },
    )
}

private fun statusDescriptor(runState: SessionRunState?): Pair<String, StatusTone> = when (runState) {
    SessionRunState.RUNNING -> "Running" to StatusTone.SUCCESS
    SessionRunState.ERROR -> "Error" to StatusTone.ERROR
    SessionRunState.KILLED_BY_OS -> "Stopped" to StatusTone.WARNING
    SessionRunState.EXITED -> "Exited" to StatusTone.NEUTRAL
    null -> "Unavailable" to StatusTone.NEUTRAL
}
