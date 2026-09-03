package dev.aicli.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.common.ProviderCard
import dev.aicli.app.ui.common.UiState
import dev.aicli.app.ui.components.EmptyState
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.components.InstallProgressSheet
import dev.aicli.app.ui.components.LoadingState
import dev.aicli.app.ui.components.ProjectItem
import dev.aicli.app.ui.components.ProviderCardVariant
import dev.aicli.app.ui.components.SectionHeader
import dev.aicli.app.ui.components.StatusChip
import dev.aicli.app.ui.components.StatusTone
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.provider.api.ProviderState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenProjects: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onLaunchProvider: (providerId: String) -> Unit,
    onOpenProject: (projectId: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    var incompatibleDetails by remember { mutableStateOf<ProviderState.Incompatible?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ternix") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is UiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            is UiState.Offline -> ErrorState(
                title = "You're offline",
                body = "Some provider status checks need network access.",
                icon = Icons.Filled.WifiOff,
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Error -> ErrorState(
                title = "Couldn't load the dashboard",
                body = s.message,
                onRetry = viewModel::refresh,
                secondaryLabel = "Run Diagnostics",
                onSecondary = onOpenDiagnostics,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Success -> HomeContent(
                data = s.data,
                padding = padding,
                onOpenProjects = onOpenProjects,
                onOpenDiagnostics = onOpenDiagnostics,
                onLaunchProvider = onLaunchProvider,
                onOpenProject = onOpenProject,
                onInstallOrUpdate = viewModel::installOrUpdateProvider,
                onShowIncompatible = { incompatibleDetails = it },
            )
        }
    }

    installProgress?.let { progress ->
        InstallProgressSheet(
            progress,
            onDismiss = viewModel::dismissInstallProgress,
            onOpenProvider = { onLaunchProvider(progress.providerId) },
        )
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
private fun HomeContent(
    data: HomeUiData,
    padding: PaddingValues,
    onOpenProjects: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onLaunchProvider: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onInstallOrUpdate: (String) -> Unit,
    onShowIncompatible: (ProviderState.Incompatible) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(Dimens.space16),
        verticalArrangement = Arrangement.spacedBy(Dimens.space24),
    ) {
        val currentProject = data.recentProjects.firstOrNull()
        if (currentProject != null) {
            item {
                Column {
                    SectionHeader("Current project")
                    Card(
                        onClick = { onOpenProject(currentProject.id) },
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.space8),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Row(Modifier.padding(Dimens.space16), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = Dimens.space12)) {
                                Text(currentProject.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    currentProject.root.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("AI providers") {
                StatusChip(
                    text = "${data.healthPassCount}/${data.healthTotalCount} runtime checks",
                    tone = if (data.healthPassCount == data.healthTotalCount) StatusTone.SUCCESS else StatusTone.WARNING,
                    icon = if (data.healthPassCount == data.healthTotalCount) Icons.Filled.CheckCircle else Icons.Filled.Error,
                )
            }
        }
        item {
            LazyProviderGrid(
                cards = data.providerCards,
                onInstallOrUpdate = onInstallOrUpdate,
                onLaunchProvider = onLaunchProvider,
                onShowIncompatible = onShowIncompatible,
            )
        }

        item { SectionHeader("Recent projects") { TextButton(onClick = onOpenProjects) { Text("All projects") } } }
        if (data.recentProjects.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Folder,
                    title = "No projects yet",
                    body = "Create or import your first coding project.",
                    actionLabel = "Create project",
                    onAction = onOpenProjects,
                )
            }
        } else {
            items(data.recentProjects) { project ->
                ProjectItem(project, onClick = { onOpenProject(project.id) })
            }
        }
    }
}

/**
 * A grid nested inside the outer LazyColumn — small, fixed-size content (4 providers), so a
 * non-lazy [androidx.compose.foundation.layout.Column]-of-rows would work too, but
 * [LazyVerticalGrid] gives free adaptive column count (1 on phone, 2+ on wide screens) via
 * [GridCells.Adaptive] without hand-rolled width branching.
 */
@Composable
private fun LazyProviderGrid(
    cards: List<ProviderCard>,
    onInstallOrUpdate: (String) -> Unit,
    onLaunchProvider: (String) -> Unit,
    onShowIncompatible: (ProviderState.Incompatible) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 200.dp),
        modifier = Modifier.fillMaxWidth().height((96 * (cards.size / 2 + 1).coerceAtLeast(1)).dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space12),
        verticalArrangement = Arrangement.spacedBy(Dimens.space12),
    ) {
        items(cards, key = { it.provider.id }) { card ->
            dev.aicli.app.ui.components.ProviderCard(
                card = card,
                variant = ProviderCardVariant.Compact,
                onPrimaryAction = {
                    when (val s = card.state) {
                        is ProviderState.NotInstalled, is ProviderState.UpdateAvailable, is ProviderState.Error ->
                            onInstallOrUpdate(card.provider.id)
                        is ProviderState.Incompatible -> onShowIncompatible(s)
                        is ProviderState.Ready, is ProviderState.AuthRequired, is ProviderState.Installed ->
                            onLaunchProvider(card.provider.id)
                        is ProviderState.Installing -> { /* already in progress, ignore taps */ }
                    }
                },
            )
        }
    }
}
