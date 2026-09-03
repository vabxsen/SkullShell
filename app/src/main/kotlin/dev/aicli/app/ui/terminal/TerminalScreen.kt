package dev.aicli.app.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aicli.app.data.SessionRunState
import dev.aicli.terminal.TerminalKeyboardBar
import dev.aicli.terminal.TerminalView

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    sessionArg: String,
    onBack: () -> Unit,
) {
    val sessions by viewModel.sessions.collectAsState()
    val activeId by viewModel.activeSessionId.collectAsState()
    val launchError by viewModel.launchError.collectAsState()

    LaunchedEffect(sessionArg) { viewModel.resolveAndOpen(sessionArg) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Terminal") })
                if (sessions.isNotEmpty()) {
                    ScrollableTabRow(selectedTabIndex = sessions.indexOfFirst { it.id == activeId }.coerceAtLeast(0)) {
                        sessions.forEach { session ->
                            Tab(
                                selected = session.id == activeId,
                                onClick = { viewModel.selectSession(session.id) },
                                text = { Text(session.title) },
                            )
                        }
                        IconButton(onClick = { viewModel.newShellInCurrentContext() }) {
                            Icon(Icons.Filled.Add, contentDescription = "New session")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                val session = sessions.firstOrNull { it.id == activeId }
                if (session == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No active session") }
                } else {
                    val controller = viewModel.controllerFor(session.id)
                    if (controller == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Session unavailable") }
                    } else {
                        val runState by controller.runState.collectAsState()
                        val exitCode by controller.exitCode.collectAsState()
                        if (runState != SessionRunState.RUNNING) {
                            SessionEndedBanner(
                                runState = runState,
                                exitCode = exitCode,
                                onRestart = {
                                    viewModel.closeSession(session.id)
                                    viewModel.newShellInCurrentContext()
                                },
                            )
                        }
                        TerminalView(
                            buffer = controller.buffer,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onInput = { bytes -> controller.sendInput(bytes) },
                            onSizeChanged = { cols, rows -> controller.resize(cols, rows) },
                        )
                        TerminalKeyboardBar(
                            modifier = Modifier.fillMaxWidth(),
                            onSend = { bytes -> controller.sendInput(bytes) },
                        )
                        Row {
                            IconButton(onClick = { viewModel.closeSession(session.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close session")
                            }
                        }
                    }
                }
            }

            if (launchError != null) {
                Snackbar(
                    modifier = Modifier.fillMaxWidth(),
                    action = { IconButton(onClick = viewModel::clearLaunchError) { Icon(Icons.Filled.Close, null) } },
                ) { Text(launchError ?: "") }
            }
        }
    }
}

/**
 * A process ending is never allowed to just leave a blank terminal with no explanation — this
 * is the one place that turns a raw exit code into a message the user can act on. Never claims a
 * session is still running when it isn't (see ARCHITECTURE.md §8).
 */
@Composable
private fun SessionEndedBanner(runState: SessionRunState, exitCode: Int?, onRestart: () -> Unit) {
    val (title, detail) = when (runState) {
        SessionRunState.KILLED_BY_OS -> "Session ended: runtime terminated by the OS" to
            "Android stopped this process (likely low memory). Nothing was lost that a fresh session can't pick back up."
        SessionRunState.ERROR -> "Session error" to "The process could not be started or communicated with. See Diagnostics for details."
        SessionRunState.EXITED -> when (exitCode) {
            127 -> "Command not found (exit 127)" to
                "The shell or CLI binary isn't installed yet. Set up the runtime from Home, or install this provider first."
            0 -> "Process exited normally" to "The session ended on its own (exit code 0)."
            null -> "Process exited" to "The session ended."
            else -> "Process exited (code $exitCode)" to "The session ended with a non-zero exit code — check Diagnostics if this is unexpected."
        }
        SessionRunState.RUNNING -> return
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRestart, modifier = Modifier.padding(top = 8.dp)) { Text("Start new session") }
        }
    }
}
