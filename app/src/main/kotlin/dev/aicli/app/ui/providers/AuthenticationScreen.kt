package dev.aicli.app.ui.providers

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.design.Glyph
import dev.aicli.app.ui.design.Glyphs
import dev.aicli.app.ui.design.Label
import dev.aicli.app.ui.design.LoadingBody
import dev.aicli.app.ui.design.Metrics
import dev.aicli.app.ui.design.PageTitle
import dev.aicli.app.ui.design.PrimaryButton
import dev.aicli.app.ui.design.Screen
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.Text
import dev.aicli.app.ui.design.TopBar
import dev.aicli.app.ui.terminal.TerminalKeyboardBar
import dev.aicli.provider.api.AuthState
import dev.aicli.terminal.TerminalView

/**
 * A provider's real login flow, rendered as a real terminal - never a fabricated success state
 * and never a WebView intercepting credentials (see [dev.aicli.provider.api.ProviderAuth]).
 * Raw CLI output stays visible while signing in, since OAuth device-code flows print a URL and
 * code the user has to read, and only collapses once [AuthState.SignedIn] is actually observed.
 *
 * The embedded terminal keeps a hairline frame here rather than going full-bleed as it does on
 * the terminal screen: on this page it is one element among several, so it needs an edge.
 */
@Composable
fun AuthenticationScreen(
    viewModel: AuthenticationViewModel,
    providerId: String,
    onDone: () -> Unit,
) {
    val sessionId by viewModel.sessionId.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val providerName = viewModel.providersById[providerId]?.displayName ?: providerId

    LaunchedEffect(providerId) { viewModel.startLogin(providerId) }

    Screen(topBar = { TopBar(crumb = "SkullShell / Sign in", onBack = onDone) }) {
        when {
            authState is AuthState.SignedIn -> SignedInBody(providerName, onDone)
            error != null -> ErrorState(
                title = "Could not sign in",
                body = error ?: "",
                onRetry = { viewModel.startLogin(providerId) },
            )
            authState is AuthState.Error -> ErrorState(
                title = "Sign-in failed",
                body = (authState as AuthState.Error).reason,
                onRetry = { viewModel.startLogin(providerId) },
            )
            sessionId == null -> LoadingBody(
                Modifier.fillMaxSize(),
                label = "Starting sign-in for " + providerName,
            )
            else -> InProgressBody(providerName, sessionId, viewModel)
        }
    }
}

@Composable
private fun InProgressBody(providerName: String, sessionId: String?, viewModel: AuthenticationViewModel) {
    val controller = sessionId?.let { viewModel.controllerFor(it) }
    Column(Modifier.fillMaxSize()) {
        PageTitle(
            title = "Sign in",
            subtitle = "Connect your " + providerName + " account. Follow the instructions below - a URL or code may appear.",
            modifier = Modifier.padding(horizontal = Metrics.gutter, vertical = Space.x6),
        )
        if (controller == null) {
            LoadingBody(Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Metrics.gutter, vertical = Space.x4)
                    .border(Metrics.hairline, SkullTheme.colors.line),
            ) {
                TerminalView(
                    buffer = controller.buffer,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = Space.x2,
                    backgroundColor = SkullTheme.colors.bg.toArgb(),
                    defaultForeground = SkullTheme.colors.ink.toArgb(),
                    onInput = { bytes -> controller.sendInput(bytes) },
                    onSizeChanged = { cols, rows -> controller.resize(cols, rows) },
                )
            }
            TerminalKeyboardBar(
                modifier = Modifier.fillMaxWidth(),
                onSend = { bytes -> controller.sendInput(bytes) },
            )
        }
    }
}

@Composable
private fun SignedInBody(providerName: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Metrics.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(Glyphs.CheckCircle, null, size = Metrics.glyphXl, tint = SkullTheme.colors.ink)
        Label(
            "Authenticated",
            color = SkullTheme.colors.ink,
            modifier = Modifier.padding(top = Space.x5),
        )
        Text(
            providerName + " is signed in and ready to use.",
            style = SkullTheme.type.body,
            color = SkullTheme.colors.inkMuted,
            modifier = Modifier.padding(top = Space.x2),
        )
        PrimaryButton("Done", onDone, modifier = Modifier.padding(top = Space.x6))
    }
}
