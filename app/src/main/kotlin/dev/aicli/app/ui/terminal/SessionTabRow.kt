package dev.aicli.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.data.SessionMeta
import dev.aicli.app.data.SessionRunState
import dev.aicli.app.data.TerminalSessionController

/** Quick-switch tabs for open sessions, each carrying a live run-state dot. */
@Composable
fun SessionTabRow(
    sessions: List<SessionMeta>,
    activeId: String?,
    controllerFor: (String) -> TerminalSessionController?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return
    ScrollableTabRow(
        selectedTabIndex = sessions.indexOfFirst { it.id == activeId }.coerceAtLeast(0),
        modifier = modifier,
    ) {
        sessions.forEach { session ->
            val controller = controllerFor(session.id)
            val runState = controller?.runState?.collectAsStateWithLifecycle()?.value
            Tab(
                selected = session.id == activeId,
                onClick = { onSelect(session.id) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SessionStatusDot(runState)
                        Text(
                            session.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                },
            )
        }
    }
}

@Composable
internal fun SessionStatusDot(runState: SessionRunState?) {
    val color = when (runState) {
        SessionRunState.RUNNING -> MaterialTheme.colorScheme.primary
        SessionRunState.ERROR -> MaterialTheme.colorScheme.error
        SessionRunState.KILLED_BY_OS, SessionRunState.EXITED, null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
}
