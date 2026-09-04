package dev.aicli.app.ui.providers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.aicli.app.ui.common.ProviderCard
import dev.aicli.app.ui.common.UiState
import dev.aicli.app.ui.components.*
import dev.aicli.app.ui.design.*
import dev.aicli.provider.api.ProviderState

@Composable
fun ProvidersScreen(viewModel: ProvidersViewModel, onLaunchProvider: (String) -> Unit,
                    onAuthenticate: (String) -> Unit, onOpenDiagnostics: () -> Unit, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val progress by viewModel.installProgress.collectAsStateWithLifecycle()
    var incompatible by remember { mutableStateOf<ProviderState.Incompatible?>(null) }
    var removeAgent by remember { mutableStateOf<ProviderCard?>(null) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    Screen(topBar = {
        TopBar("Agents", onBack = onBack, actions = {
            IconAction(Glyphs.Info, "Open diagnostics", onOpenDiagnostics)
            IconAction(Glyphs.Refresh, "Refresh agents", viewModel::refresh)
        })
    }) {
        BoxWithConstraints(Modifier.widthIn(max = Metrics.maxContent).fillMaxSize().align(Alignment.TopCenter)) {
            val columns = if (maxWidth >= 680.dp) 2 else 1
            when (val s = state) {
                is UiState.Loading -> LoadingBody(Modifier.fillMaxSize(), "Checking agents")
                is UiState.Error -> ErrorState("Could not load agents", s.message, onRetry = viewModel::refresh,
                    secondaryLabel = "Diagnostics", onSecondary = onOpenDiagnostics)
                is UiState.Offline -> ErrorState("Offline", "Connect to the internet to check your coding agents.",
                    glyph = Glyphs.NoSignal, onRetry = viewModel::refresh)
                is UiState.Success -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Metrics.gutter), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(bottom = Space.x4), verticalAlignment = Alignment.CenterVertically) {
                            Text("Coding agents", style = SkullTheme.type.title, modifier = Modifier.weight(1f))
                            Text("${s.data.count { it.state is ProviderState.Ready }} ready", style = SkullTheme.type.bodySm,
                                color = SkullTheme.colors.inkMuted)
                        }

                    }
                    items(s.data.chunked(columns), key = { it.first().provider.id }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                            row.forEach { card ->
                                val installed = card.state !is ProviderState.NotInstalled && card.state !is ProviderState.Incompatible
                                val overflow = buildList {
                                    if (card.state is ProviderState.AuthRequired || card.state is ProviderState.Ready || card.state is ProviderState.Installed) {
                                        add(ProviderOverflowAction("Sign in") { onAuthenticate(card.provider.id) })
                                    }
                                    if (installed && card.state !is ProviderState.Installing) {
                                        add(ProviderOverflowAction("Repair agent") { viewModel.repair(card.provider.id) })
                                        add(ProviderOverflowAction("Uninstall agent") { removeAgent = card })
                                    }
                                    add(ProviderOverflowAction("Diagnostics", onOpenDiagnostics))
                                }
                                ProviderPanel(card, ProviderPanelVariant.Full, modifier = Modifier.weight(1f), overflowActions = overflow,
                                    onPrimaryAction = {
                                        when (val value = card.state) {
                                            is ProviderState.NotInstalled, is ProviderState.UpdateAvailable, is ProviderState.Error -> viewModel.installOrUpdate(card.provider.id)
                                            is ProviderState.AuthRequired -> onAuthenticate(card.provider.id)
                                            is ProviderState.Ready, is ProviderState.Installed -> onLaunchProvider(card.provider.id)
                                            is ProviderState.Incompatible -> incompatible = value
                                            is ProviderState.Installing -> Unit
                                        }
                                    })
                            }
                            if (row.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (s.data.isEmpty()) item { EmptyState(Glyphs.Grid, "No agents available", "Check diagnostics for details about this build.",
                        actionLabel = "Open diagnostics", onAction = onOpenDiagnostics) }
                    item {
                        Column(Modifier.padding(top = Space.x5)) {
                            Text("Agents run in the Linux environment.", style = SkullTheme.type.bodySm, color = SkullTheme.colors.inkFaint)
                            GhostButton("Environment settings", onOpenSettings, glyph = Glyphs.ArrowRight)
                        }
                    }
                }
            }
        }
    }
    progress?.let { InstallProgressSheet(it, viewModel::dismissInstallProgress) }
    incompatible?.let { value ->
        Modal("Not available on this device", { incompatible = null }, actions = { GhostButton("Close", { incompatible = null }) }) {
            Text(value.reason, color = SkullTheme.colors.inkMuted)
        }
    }
    removeAgent?.let { card ->
        Modal("Uninstall ${card.provider.displayName}?", { removeAgent = null }, actions = {
            GhostButton("Cancel", { removeAgent = null })
            GhostButton("Uninstall", { viewModel.uninstall(card.provider.id); removeAgent = null })
        }) { Text("The installed agent will be removed. You can install it again from this screen.", color = SkullTheme.colors.inkMuted) }
    }
}
