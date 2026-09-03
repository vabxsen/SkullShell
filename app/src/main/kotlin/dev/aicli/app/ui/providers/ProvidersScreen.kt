package dev.aicli.app.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.common.UiState
import dev.aicli.app.ui.components.EmptyState
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.components.InstallProgressSheet
import dev.aicli.app.ui.components.LoadingState
import dev.aicli.app.ui.components.ProviderCard
import dev.aicli.app.ui.components.ProviderCardVariant
import dev.aicli.app.ui.components.ProviderOverflowAction
import dev.aicli.provider.api.ProviderState

@Composable
fun ProvidersScreen(
    viewModel: ProvidersViewModel,
    onLaunchProvider: (providerId: String) -> Unit,
    onAuthenticate: (providerId: String) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    var incompatibleDetails by remember { mutableStateOf<ProviderState.Incompatible?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Providers") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is UiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            is UiState.Offline -> ErrorState(
                title = "You're offline",
                body = "Provider status checks need network access.",
                icon = Icons.Filled.WifiOff,
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Error -> ErrorState(
                title = "Couldn't load providers",
                body = s.message,
                onRetry = viewModel::refresh,
                secondaryLabel = "Run Diagnostics",
                onSecondary = onOpenDiagnostics,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Success -> if (s.data.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Refresh,
                    title = "No providers found",
                    body = "Something's wrong with this build — no AI providers are registered.",
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                ProvidersList(
                    cards = s.data,
                    padding = padding,
                    onPrimaryAction = { providerId, providerState ->
                        when (providerState) {
                            is ProviderState.NotInstalled, is ProviderState.UpdateAvailable, is ProviderState.Error ->
                                viewModel.installOrUpdate(providerId)
                            is ProviderState.Incompatible -> incompatibleDetails = providerState
                            is ProviderState.AuthRequired -> onAuthenticate(providerId)
                            is ProviderState.Ready, is ProviderState.Installed -> onLaunchProvider(providerId)
                            is ProviderState.Installing -> { /* already in progress */ }
                        }
                    },
                    onRepair = viewModel::repair,
                    onUninstall = viewModel::uninstall,
                    onAuthenticate = onAuthenticate,
                    onOpenDiagnostics = onOpenDiagnostics,
                )
            }
        }
    }

    installProgress?.let { progress ->
        InstallProgressSheet(progress, onDismiss = viewModel::dismissInstallProgress)
    }

    incompatibleDetails?.let { incompatible ->
        AlertDialog(
            onDismissRequest = { incompatibleDetails = null },
            title = { Text("Not compatible with this device") },
            text = { Text(incompatible.reason) },
            confirmButton = { TextButton(onClick = { incompatibleDetails = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun ProvidersList(
    cards: List<dev.aicli.app.ui.common.ProviderCard>,
    padding: PaddingValues,
    onPrimaryAction: (providerId: String, state: ProviderState) -> Unit,
    onRepair: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onAuthenticate: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { it.provider.id }) { card ->
            val installed = card.state !is ProviderState.NotInstalled && card.state !is ProviderState.Incompatible
            val overflow = buildList {
                if (card.state is ProviderState.AuthRequired || card.state is ProviderState.Ready || card.state is ProviderState.Installed) {
                    add(ProviderOverflowAction("Authenticate") { onAuthenticate(card.provider.id) })
                }
                if (installed) {
                    add(ProviderOverflowAction("Repair") { onRepair(card.provider.id) })
                    add(ProviderOverflowAction("Uninstall") { onUninstall(card.provider.id) })
                }
                add(ProviderOverflowAction("Diagnostics") { onOpenDiagnostics() })
            }
            ProviderCard(
                card = card,
                variant = ProviderCardVariant.Full,
                onPrimaryAction = { onPrimaryAction(card.provider.id, card.state) },
                overflowActions = overflow,
            )
        }
    }
}
