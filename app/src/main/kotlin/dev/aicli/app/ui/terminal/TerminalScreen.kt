package dev.aicli.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.data.SessionRunState
import dev.aicli.app.ui.components.EmptyState
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.app.ui.theme.LocalExtendedColors
import dev.aicli.terminal.TerminalKeyboardBar
import dev.aicli.terminal.TerminalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The terminal screen pins its own dark, violet-accented look ([TerminalDarkColorScheme])
 * regardless of the app's own light/dark setting — a terminal is conventionally always a
 * fixed-dark, tool-like surface. See TerminalPalette.kt.
 */
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
    val snackbarHostState = remember { SnackbarHostState() }
    val terminalFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(sessionArg) { viewModel.resolveAndOpen(sessionArg) }

    LaunchedEffect(launchError) {
        launchError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearLaunchError()
        }
    }

    val baseTypography = MaterialTheme.typography
    val baseShapes = MaterialTheme.shapes

    MaterialTheme(colorScheme = TerminalDarkColorScheme, typography = baseTypography, shapes = baseShapes) {
        val session = sessions.firstOrNull { it.id == activeId }
        val controller = session?.let { viewModel.controllerFor(it.id) }
        val runState = controller?.runState?.collectAsStateWithLifecycle()?.value

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TerminalHeader(
                    onMenuClick = onBack,
                    onClear = { controller?.sendInput(byteArrayOf(0x0C)) },
                    onRestart = session?.let {
                        {
                            viewModel.closeSession(it.id)
                            viewModel.newShellInCurrentContext()
                        }
                    },
                    onClose = session?.let { s -> { viewModel.closeSession(s.id) } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.space12)) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = Dimens.space8),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        SessionCardHeader(
                            title = session?.title ?: "No session",
                            runState = runState,
                            onOpenSwitcher = { switcherOpen = true },
                            onNewSession = { viewModel.newShellInCurrentContext() },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                session == null -> EmptyState(
                                    icon = Icons.Filled.Terminal,
                                    title = "No active session",
                                    body = "Open a project or provider to start one.",
                                    modifier = Modifier.fillMaxSize(),
                                )
                                controller == null -> EmptyState(
                                    icon = Icons.Filled.Terminal,
                                    title = "Session unavailable",
                                    body = "This session's process is no longer reachable.",
                                    modifier = Modifier.fillMaxSize(),
                                )
                                else -> Column(Modifier.fillMaxSize()) {
                                    if (runState != SessionRunState.RUNNING && runState != null) {
                                        val exitCode by controller.exitCode.collectAsStateWithLifecycle()
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
                                        focusRequester = terminalFocusRequester,
                                        onInput = { bytes -> controller.sendInput(bytes) },
                                        onSizeChanged = { cols, rows -> controller.resize(cols, rows) },
                                    )
                                }
                            }
                        }
                        if (session != null && controller != null) {
                            val uptime = rememberUptime(session.createdAtEpochMillis, runState == SessionRunState.RUNNING)
                            StatusChipRow(
                                cwd = session.workingDirectory,
                                uptime = uptime,
                                isRunning = runState == SessionRunState.RUNNING,
                            )
                            TerminalInputBar(
                                focusRequester = terminalFocusRequester,
                                keyboardController = keyboardController,
                                onSendEnter = { controller.sendInput(byteArrayOf(0x0D)) },
                            )
                        }
                    }
                }
                if (session != null && controller != null) {
                    TerminalKeyboardBar(
                        modifier = Modifier.fillMaxWidth().imePadding(),
                        onSend = { bytes -> controller.sendInput(bytes) },
                    )
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
}

@Composable
private fun TerminalHeader(
    onMenuClick: () -> Unit,
    onClear: () -> Unit,
    onRestart: (() -> Unit)?,
    onClose: (() -> Unit)?,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.space12, vertical = Dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Filled.Menu, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
        Icon(
            Icons.Filled.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Dimens.space8).size(Dimens.iconMedium),
        )
        Text(
            "Terminal",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { overflowOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                DropdownMenuItem(text = { Text("Clear") }, onClick = { overflowOpen = false; onClear() })
                if (onRestart != null) {
                    DropdownMenuItem(text = { Text("Restart") }, onClick = { overflowOpen = false; onRestart() })
                }
                if (onClose != null) {
                    DropdownMenuItem(text = { Text("Close session") }, onClick = { overflowOpen = false; onClose() })
                }
            }
        }
    }
}

@Composable
private fun SessionCardHeader(
    title: String,
    runState: SessionRunState?,
    onOpenSwitcher: () -> Unit,
    onNewSession: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.space16, vertical = Dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onOpenSwitcher),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(runState)
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Dimens.space8),
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = "Switch session",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconSmall),
            )
        }
        IconButton(onClick = onNewSession) {
            Icon(Icons.Filled.Add, contentDescription = "New session", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusDot(runState: SessionRunState?) {
    val color = when (runState) {
        SessionRunState.RUNNING -> LocalExtendedColors.current.success
        SessionRunState.ERROR -> MaterialTheme.colorScheme.error
        SessionRunState.KILLED_BY_OS, SessionRunState.EXITED, null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
private fun StatusChipRow(cwd: String, uptime: String, isRunning: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.space16, vertical = Dimens.space8),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
    ) {
        InfoChip(Icons.Filled.Folder, cwd)
        InfoChip(
            Icons.Filled.Schedule,
            uptime,
            dotColor = if (isRunning) LocalExtendedColors.current.success else null,
        )
    }
}

@Composable
private fun InfoChip(icon: ImageVector, label: String, dotColor: Color? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = Dimens.space12, vertical = Dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Dimens.iconSmall))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Dimens.space8),
        )
        if (dotColor != null) {
            Box(Modifier.padding(start = Dimens.space8).size(6.dp).background(dotColor, CircleShape))
        }
    }
}

/**
 * A styled affordance for the same live-keystroke input [TerminalView] already captures — not a
 * line-buffered "type then send" field. Tapping it focuses the terminal's hidden input connection
 * so typed characters keep going straight to the PTY as they do today (interactive CLIs like
 * vim or Claude Code's own prompts need every keystroke live, not a buffered line). Its own text
 * is therefore always the placeholder; what you type shows up in the terminal pane above, exactly
 * as a real terminal echoes it. The send button is a convenience for pressing Enter.
 */
@Composable
private fun TerminalInputBar(
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    onSendEnter: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.space16, vertical = Dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                .padding(horizontal = Dimens.space16, vertical = Dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Type to send input…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onSendEnter,
            modifier = Modifier
                .padding(start = Dimens.space12)
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun rememberUptime(createdAtEpochMillis: Long, isRunning: Boolean): String {
    var elapsedText by remember(createdAtEpochMillis) { mutableStateOf(formatElapsed(System.currentTimeMillis() - createdAtEpochMillis)) }
    LaunchedEffect(createdAtEpochMillis, isRunning) {
        while (isActive) {
            elapsedText = formatElapsed(System.currentTimeMillis() - createdAtEpochMillis)
            if (!isRunning) break
            delay(1000)
        }
    }
    return elapsedText
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
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
