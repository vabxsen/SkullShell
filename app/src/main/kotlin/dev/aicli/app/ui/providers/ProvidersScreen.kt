package dev.aicli.app.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.common.UiState
import dev.aicli.app.ui.components.EmptyState
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.components.InstallProgressSheet
import dev.aicli.app.ui.components.ProviderOverflowAction
import dev.aicli.app.ui.components.ProviderPanel
import dev.aicli.app.ui.components.ProviderPanelVariant
import dev.aicli.app.ui.design.Glyphs
import dev.aicli.app.ui.design.IconAction
import dev.aicli.app.ui.design.LoadingBody
import dev.aicli.app.ui.design.Metrics
import dev.aicli.app.ui.design.Modal
import dev.aicli.app.ui.design.PageTitle
import dev.aicli.app.ui.design.PrimaryButton
import dev.aicli.app.ui.design.Screen
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.Text
import dev.aicli.app.ui.design.TopBar
import dev.aicli.provider.api.ProviderState

private val MAX_MEASURE = 720.dp

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

    Screen(
        topBar = {
            TopBar(
                crumb = "SkullShell / Providers",
                actions = {
                    IconAction(
                        icon = Glyphs.Refresh,
                        contentDescription = "Refresh",
                        onClick = { viewModel.refresh() },
                    )
                },
            )
        },
    ) {
        Box(Modifier.widthIn(max = MAX_MEASURE).fillMaxSize().align(Alignment.TopCenter)) {
            when (val s = state) {
                is UiState.Loading -> LoadingBody(Modifier.fillMaxSize(), label = "Checking providers")
                is UiState.Offline -> ErrorState(
                    title = "Offline",
                    body = "Provider status checks need network access.",
                    glyph = Glyphs.NoSignal,
                    onRetry = viewModel::refresh,
                )
                is UiState.Error -> ErrorState(
                    title = "Could not load providers",
                    body = s.message,
                    onRetry = viewModel::refresh,
                    secondaryLabel = "Diagnostics",
                    onSecondary = onOpenDiagnostics,
                )
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        glyph = Glyphs.Grid,
                        title = "No providers registered",
                        body = "This build shipped without any AI providers, which should not happen. Diagnostics has the details.",
                        actionLabel = "Diagnostics",
                        onAction = onOpenDiagnostics,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Metrics.gutter,
                            end = Metrics.gutter,
                            bottom = Space.x10,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Space.x3),
                    ) {
                        item {
                            PageTitle(
                                title = "Providers",
                                subtitle = "Coding agents installed into the Linux userland.",
                                modifier = Modifier.padding(top = Space.x6, bottom = Space.x2),
                            )
                        }
                        items(s.data, key = { it.provider.id }) { card ->
                            val installed = card.state !is ProviderState.NotInstalled &&
                                card.state !is ProviderState.Incompatible
                            val overflow = buildList {
                                if (card.state is ProviderState.AuthRequired ||
                                    card.state is ProviderState.Ready ||
                                    card.state is ProviderState.Installed
                                ) {
                                    add(ProviderOverflowAction("Authenticate") { onAuthenticate(card.provider.id) })
                                }
                                if (installed) {
                                    add(ProviderOverflowAction("Repair") { viewModel.repair(card.provider.id) })
                                    add(ProviderOverflowAction("Uninstall") { viewModel.uninstall(card.provider.id) })
                                }
                                add(ProviderOverflowAction("Diagnostics") { onOpenDiagnostics() })
                            }
                            ProviderPanel(
                                card = card,
                                variant = ProviderPanelVariant.Full,
                                overflowActions = overflow,
                                onPrimaryAction = {
                                    when (val providerState = card.state) {
                                        is ProviderState.NotInstalled,
                                        is ProviderState.UpdateAvailable,
                                        is ProviderState.Error,
                                        -> viewModel.installOrUpdate(card.provider.id)
                                        is ProviderState.Incompatible -> incompatibleDetails = providerState
                                        is ProviderState.AuthRequired -> onAuthenticate(card.provider.id)
                                        is ProviderState.Ready,
                                        is ProviderState.Installed,
                                        -> onLaunchProvider(card.provider.id)
                                        is ProviderState.Installing -> Unit
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    installProgress?.let { progress ->
        InstallProgressSheet(progress, onDismiss = viewModel::dismissInstallProgress)
    }

    incompatibleDetails?.let { incompatible ->
        Modal(
            title = "Not compatible",
            onDismiss = { incompatibleDetails = null },
            actions = { PrimaryButton("Close", { incompatibleDetails = null }) },
        ) {
            Text(incompatible.reason, style = SkullTheme.type.body, color = SkullTheme.colors.inkMuted)
        }
    }
}
