package dev.aicli.app.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.data.SessionRunState
import dev.aicli.app.ui.components.EmptyState
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.terminal.ConnectionStatus
import dev.aicli.terminal.TerminalKeyboardBar
import dev.aicli.terminal.TerminalToolbar
import dev.aicli.terminal.TerminalView
import dev.aicli.terminal.ctrlByte
import java.io.File

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    sessionArg: String,
    onBack: () -> Unit,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val launchError by viewModel.launchError.collectAsStateWithLifecycle()
    var switcherOpen by remember { mutableStateOf(false) }

    LaunchedEffect(sessionArg) { viewModel.resolveAndOpen(sessionArg) }

    Scaffold(
        topBar = {
            Column {
                val session = sessions.firstOrNull { it.id == activeId }
                val controller = session?.let { viewModel.controllerFor(it.id) }
                val runState = controller?.runState?.collectAsStateWithLifecycle()?.value
                TerminalToolbar(
                    providerIcon = null,
                    providerName = session?.providerId?.let { viewModel.providersById[it]?.displayName },
                    sessionTitle = session?.title ?: "Terminal",
                    workingDirectorySuffix = session?.workingDirectory?.let { File(it).name },
                    connectionStatus = when (runState) {
                        SessionRunState.RUNNING -> ConnectionStatus.RUNNING
                        SessionRunState.EXITED -> ConnectionStatus.EXITED
                        SessionRunState.KILLED_BY_OS, SessionRunState.ERROR -> ConnectionStatus.ERROR
                        null -> ConnectionStatus.IDLE
                    },
                    onSessionSwitcher = { switcherOpen = true },
                    onNewSession = { viewModel.newShellInCurrentContext() },
                    onClear = { controller?.sendInput(byteArrayOf(ctrlByte('L'))) },
                    onRestart = {
                        session?.let {
                            viewModel.closeSession(it.id)
                            viewModel.newShellInCurrentContext()
                        }
                    },
                    onStop = if (runState == SessionRunState.RUNNING) {
                        { session?.let { viewModel.closeSession(it.id) } }
                    } else null,
                )
                SessionTabRow(
                    sessions = sessions,
                    activeId = activeId,
                    controllerFor = viewModel::controllerFor,
                    onSelect = viewModel::selectSession,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                val session = sessions.firstOrNull { it.id == activeId }
                if (session == null) {
                    EmptyState(
                        icon = Icons.Filled.Terminal,
                        title = "No active session",
                        body = "Open a project or provider to start one.",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val controller = viewModel.controllerFor(session.id)
                    if (controller == null) {
                        EmptyState(
                            icon = Icons.Filled.Terminal,
                            title = "Session unavailable",
                            body = "This session's process is no longer reachable.",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        val runState by controller.runState.collectAsStateWithLifecycle()
                        val exitCode by controller.exitCode.collectAsStateWithLifecycle()
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
                            backgroundColor = MaterialTheme.colorScheme.background.toArgb(),
                            onInput = { bytes -> controller.sendInput(bytes) },
                            onSizeChanged = { cols, rows -> controller.resize(cols, rows) },
                        )
                        TerminalKeyboardBar(
                            modifier = Modifier.fillMaxWidth().imePadding(),
                            onSend = { bytes -> controller.sendInput(bytes) },
                        )
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

    if (switcherOpen) {
        SessionSwitcherSheet(
            sessions = sessions,
            providersById = viewModel.providersById,
            controllerFor = viewModel::controllerFor,
            onSelect = viewModel::selectSession,
            onNewShell = { viewModel.newShellInCurrentContext() },
            onDismiss = { switcherOpen = false },
        )
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
        modifier = Modifier.fillMaxWidth().padding(Dimens.space12),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(Dimens.space16)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRestart, modifier = Modifier.padding(top = Dimens.space8)) { Text("Start new session") }
        }
    }
}
