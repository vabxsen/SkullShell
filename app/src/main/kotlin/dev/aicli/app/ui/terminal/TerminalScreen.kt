package dev.aicli.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.data.SessionRunState
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.design.*
import dev.aicli.terminal.TerminalView

@Composable
fun TerminalScreen(viewModel: TerminalViewModel, sessionArg: String, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val launchError by viewModel.launchError.collectAsStateWithLifecycle()
    val preferences by viewModel.terminalSettings.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var showKeys by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(sessionArg) { viewModel.resolveAndOpen(sessionArg) }

    TerminalTheme {
        val colors = SkullTheme.colors
        val session = sessions.firstOrNull { it.id == activeId }
        val controller = session?.let { viewModel.controllerFor(it.id) }
        val runState = controller?.runState?.collectAsStateWithLifecycle()?.value
        Screen(topBar = {
            TopBar("SkullShell / Terminal", onBack = onBack, actions = {
                if (controller != null) {
                    IconAction(Glyphs.Keyboard, "Show keyboard", { focus.requestFocus(); keyboard?.show() })
                    IconAction(Glyphs.Sliders, "Toggle shortcut keys", { showKeys = !showKeys },
                        tint = if (showKeys) colors.accent else colors.inkMuted)
                }
                IconAction(Glyphs.Plus, "New terminal session", viewModel::newShellInCurrentContext)
            })
        }) {
            Column(Modifier.fillMaxSize().imePadding()) {
                if (sessions.isNotEmpty()) {
                    androidx.compose.material3.ScrollableTabRow(
                        selectedTabIndex = sessions.indexOfFirst { it.id == activeId }.coerceAtLeast(0),
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                        edgePadding = 0.dp,
                    ) {
                        sessions.forEachIndexed { index, item ->
                            androidx.compose.material3.Tab(selected = item.id == activeId, onClick = { viewModel.selectSession(item.id) },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.Text("${item.title} ${index + 1}")
                                        IconAction(Glyphs.Close, "Close ${item.title} ${index + 1}", { viewModel.closeSession(item.id) }, size = 18.dp)
                                    }
                                })
                        }
                    }
                }
                if (session != null && controller != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x2), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(Glyphs.Folder, null, size = 14.dp, tint = colors.inkFaint)
                        Text(session.workingDirectory.substringAfter("/files/", session.workingDirectory), style = SkullTheme.type.monoSm,
                            color = colors.inkFaint, modifier = Modifier.weight(1f).padding(horizontal = Space.x2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusChip(if (runState == SessionRunState.RUNNING) "Running" else "Ended",
                            if (runState == SessionRunState.RUNNING) StatusTone.SUCCESS else StatusTone.NEUTRAL)
                    }
                    key(session.id) {
                        TerminalView(controller.buffer, Modifier.fillMaxWidth().weight(1f),
                            fontSize = preferences.fontSize.sp, cursorBlink = preferences.cursorBlink, copyOnSelect = preferences.copyOnSelect,
                            contentPadding = Space.x3, backgroundColor = colors.bg.toArgb(), defaultForeground = colors.ink.toArgb(),
                            focusRequester = focus,
                            onInput = { if (runState == SessionRunState.RUNNING) controller.sendInput(it) },
                            onSizeChanged = controller::resize)
                    }
                    if (runState != SessionRunState.RUNNING) {
                        val exitCode by controller.exitCode.collectAsStateWithLifecycle()
                        Column(Modifier.fillMaxWidth().background(colors.panel).padding(Space.x4)) {
                            Text(if (exitCode == null || exitCode == 0) "This session has ended." else "Process exited with code $exitCode.",
                                style = SkullTheme.type.bodySm, color = colors.inkMuted)
                            OutlineButton("Start another session", viewModel::newShellInCurrentContext, Modifier.fillMaxWidth().padding(top = Space.x3))
                        }
                    } else if (showKeys) TerminalKeyboardBar(onSend = controller::sendInput,
                        applicationCursorMode = controller.buffer.applicationCursorMode)
                } else if (launchError != null) {
                    ErrorState("Terminal unavailable", launchError.orEmpty(),
                        onRetry = onOpenSettings, retryLabel = "Open settings", glyph = Glyphs.Terminal,
                        secondaryLabel = "Try again", onSecondary = { viewModel.clearLaunchError(); viewModel.newShellInCurrentContext() })
                } else {
                    dev.aicli.app.ui.components.EmptyState(Glyphs.Terminal, "No open sessions",
                        "Open a session to start working in your terminal.", actionLabel = "New session",
                        onAction = viewModel::newShellInCurrentContext)
                }
            }
        }
    }
}
