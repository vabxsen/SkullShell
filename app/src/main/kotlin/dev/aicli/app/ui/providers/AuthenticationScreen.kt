package dev.aicli.app.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.components.LoadingState
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.provider.api.AuthState
import dev.aicli.terminal.TerminalKeyboardBar
import dev.aicli.terminal.TerminalView

/**
 * A provider's real login flow, rendered as a real terminal — never a fabricated success state
 * and never a WebView intercepting credentials (see [dev.aicli.provider.api.ProviderAuth]).
 * Raw CLI output stays visible while signing in (OAuth device-code flows print a URL+code the
 * user must read), then collapses behind a plain success card once [AuthState.SignedIn] is
 * actually observed.
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

    Scaffold(topBar = { TopAppBar(title = { Text("Sign in") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                authState is AuthState.SignedIn -> SignedInBody(providerName, onDone)
                error != null -> ErrorState(
                    title = "Couldn't sign in",
                    body = error ?: "",
                    onRetry = { viewModel.startLogin(providerId) },
                    modifier = Modifier.fillMaxSize(),
                )
                authState is AuthState.Error -> ErrorState(
                    title = "Sign-in failed",
                    body = (authState as AuthState.Error).reason,
                    onRetry = { viewModel.startLogin(providerId) },
                    modifier = Modifier.fillMaxSize(),
                )
                sessionId == null -> LoadingState(Modifier.fillMaxSize(), label = "Starting sign-in for $providerName…")
                else -> InProgressBody(providerName, sessionId, viewModel)
            }
        }
    }
}

@Composable
private fun InProgressBody(providerName: String, sessionId: String?, viewModel: AuthenticationViewModel) {
    val controller = sessionId?.let { viewModel.controllerFor(it) }
    Column(Modifier.fillMaxSize().padding(Dimens.space16)) {
        Text("Authentication required", style = MaterialTheme.typography.titleLarge)
        Text(
            "Connect your $providerName account to continue. Follow any instructions below — a URL or code may be shown.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.space8, bottom = Dimens.space16),
        )
        if (controller == null) {
            LoadingState(Modifier.fillMaxSize())
        } else {
            TerminalView(
                buffer = controller.buffer,
                modifier = Modifier.fillMaxWidth().weight(1f),
                backgroundColor = MaterialTheme.colorScheme.background.toArgb(),
                onInput = { bytes -> controller.sendInput(bytes) },
                onSizeChanged = { cols, rows -> controller.resize(cols, rows) },
            )
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
        modifier = Modifier.fillMaxSize().padding(Dimens.space32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            "Authenticated",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Dimens.space12),
        )
        Text(
            "$providerName is signed in and ready to use.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.space4),
        )
        Button(onClick = onDone, modifier = Modifier.padding(top = Dimens.space20)) { Text("Done") }
    }
}
