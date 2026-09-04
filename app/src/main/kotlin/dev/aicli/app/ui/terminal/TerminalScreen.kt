package dev.aicli.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.data.SessionRunState
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.terminal.TerminalView

/**
 * The terminal is a blank canvas, full-bleed, no chrome beyond a bare "Terminal" heading and a
 * back button: the [TerminalView] itself already focuses on tap and pops the IME (see its own
 * pointerInput), so there is nothing left for this screen to add except that minimal header and
 * the two states where there's no live PTY to draw — no session yet, or one that just ended. Both
 * render as plain centered text, never a Card/Button/icon, to stay consistent with "blank canvas,
 * no extra UI"; tapping the ended-session text restarts it, since a fully blank screen after a
 * crash was a real, previously-fixed bug (see ARCHITECTURE.md §8) — this keeps that fix without
 * adding a button.
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

    val baseTypography = MaterialTheme.typography
    val baseShapes = MaterialTheme.shapes

    MaterialTheme(colorScheme = TerminalDarkColorScheme, typography = baseTypography, shapes = baseShapes) {
        val session = sessions.firstOrNull { it.id == activeId }
        val controller = session?.let { viewModel.controllerFor(it.id) }
        val runState = controller?.runState?.collectAsStateWithLifecycle()?.value

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Dimens.space8, end = Dimens.space8, top = Dimens.space16, bottom = Dimens.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .pointerInput(onBack) { detectTapGestures(onTap = { onBack() }) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    "Terminal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Dimens.space8),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .navigationBarsPadding()
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
            when {
                session == null || controller == null -> Text(
                    launchError ?: "No session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(Dimens.space24),
                )
                runState != null && runState != SessionRunState.RUNNING -> {
                    val exitCode by controller.exitCode.collectAsStateWithLifecycle()
                    Text(
                        endedMessage(runState, exitCode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                viewModel.closeSession(session.id)
                                viewModel.newShellInCurrentContext()
                            }
                            .padding(Dimens.space24),
                    )
                }
                else -> TerminalView(
                    buffer = controller.buffer,
                    modifier = Modifier.fillMaxSize(),
                    backgroundColor = MaterialTheme.colorScheme.background.toArgb(),
                    focusRequester = terminalFocusRequester,
                    onInput = { bytes -> controller.sendInput(bytes) },
                    onSizeChanged = { cols, rows -> controller.resize(cols, rows) },
                )
            }
            }
        }
    }
}

private fun endedMessage(runState: SessionRunState, exitCode: Int?): String = when (runState) {
    SessionRunState.KILLED_BY_OS -> "Session ended — the OS stopped this process.\n\nTap to start a new session"
    SessionRunState.ERROR -> "Session error.\n\nTap to start a new session"
    SessionRunState.EXITED -> when (exitCode) {
        127 -> "Command not found (exit 127).\n\nTap to start a new session"
        0 -> "Process exited.\n\nTap to start a new session"
        null -> "Process exited.\n\nTap to start a new session"
        else -> "Process exited (code $exitCode).\n\nTap to start a new session"
    }
    SessionRunState.RUNNING -> ""
}
