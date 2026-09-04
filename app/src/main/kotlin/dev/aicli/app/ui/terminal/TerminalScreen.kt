package dev.aicli.app.ui.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.data.SessionRunState
import dev.aicli.app.ui.design.Label
import dev.aicli.app.ui.design.Metrics
import dev.aicli.app.ui.design.OutlineButton
import dev.aicli.app.ui.design.Screen
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.TerminalTheme
import dev.aicli.app.ui.design.Text
import dev.aicli.app.ui.design.TopBar
import dev.aicli.app.ui.design.pressable
import dev.aicli.terminal.TerminalView

/**
 * The terminal is the one screen that is mostly not this design system: below the bar it is a
 * [TerminalView] canvas drawing a PTY's own output, and the app has no business decorating it.
 * So the chrome is a single bar - back, breadcrumb, live run state - and everything else is
 * grid.
 *
 * It also pins [TerminalTheme] rather than following the user's light/dark preference: a
 * terminal is a fixed-dark instrument, and a white page full of ANSI output is not a thing
 * anyone wants. The colours the PTY itself emits are left alone - that output is a program's
 * data, not this app's chrome, and greyscaling a `git diff` or an error line would destroy
 * information the design has no right to touch.
 *
 * Two states have no live PTY to draw: no session yet, and one that has ended. A fully blank
 * screen after a crash was a real, previously-fixed bug (see ARCHITECTURE.md section 8), so the
 * ended state stays both explicit and recoverable - the whole area restarts on tap, and there is
 * a labelled button for anyone who does not think to try that.
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
    val terminalFocusRequester = remember { FocusRequester() }

    LaunchedEffect(sessionArg) { viewModel.resolveAndOpen(sessionArg) }

    TerminalTheme {
        val session = sessions.firstOrNull { it.id == activeId }
        val controller = session?.let { viewModel.controllerFor(it.id) }
        val runState = controller?.runState?.collectAsStateWithLifecycle()?.value

        Screen(
            topBar = {
                TopBar(
                    crumb = "SkullShell / Terminal",
                    onBack = onBack,
                    actions = {
                        Label(
                            runStateLabel(runState),
                            color = SkullTheme.colors.inkFaint,
                            modifier = Modifier.padding(end = Space.x4),
                        )
                    },
                )
            },
        ) {
            Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
                when {
                    session == null || controller == null -> Text(
                        launchError ?: "No session",
                        style = SkullTheme.type.mono,
                        color = SkullTheme.colors.inkMuted,
                        align = TextAlign.Center,
                        modifier = Modifier.padding(Metrics.gutter),
                    )

                    runState != null && runState != SessionRunState.RUNNING -> {
                        val exitCode by controller.exitCode.collectAsStateWithLifecycle()
                        val restart = {
                            viewModel.closeSession(session.id)
                            viewModel.newShellInCurrentContext()
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .pressable(onClick = restart)
                                .padding(Metrics.gutter),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                endedMessage(runState, exitCode),
                                style = SkullTheme.type.mono,
                                color = SkullTheme.colors.inkMuted,
                                align = TextAlign.Center,
                            )
                            OutlineButton(
                                label = "New session",
                                onClick = restart,
                                modifier = Modifier.padding(top = Space.x6),
                            )
                        }
                    }

                    else -> TerminalView(
                        buffer = controller.buffer,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = Space.x3,
                        backgroundColor = SkullTheme.colors.bg.toArgb(),
                        defaultForeground = SkullTheme.colors.ink.toArgb(),
                        focusRequester = terminalFocusRequester,
                        onInput = { bytes -> controller.sendInput(bytes) },
                        onSizeChanged = { cols, rows -> controller.resize(cols, rows) },
                    )
                }
            }
        }
    }
}

private fun runStateLabel(runState: SessionRunState?): String = when (runState) {
    SessionRunState.RUNNING -> "Running"
    SessionRunState.EXITED -> "Exited"
    SessionRunState.ERROR -> "Error"
    SessionRunState.KILLED_BY_OS -> "Stopped"
    null -> ""
}

private fun endedMessage(runState: SessionRunState, exitCode: Int?): String = when (runState) {
    SessionRunState.KILLED_BY_OS -> "Session ended - the OS stopped this process."
    SessionRunState.ERROR -> "Session error."
    SessionRunState.EXITED -> when (exitCode) {
        127 -> "Command not found (exit 127)."
        0, null -> "Process exited."
        else -> "Process exited (code " + exitCode + ")."
    }
    SessionRunState.RUNNING -> ""
}
