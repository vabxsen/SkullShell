package dev.aicli.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aicli.app.ui.common.UiState
import dev.aicli.provider.api.InstallEvent
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
    val state by viewModel.uiState.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    var incompatibleDetails by remember { mutableStateOf<ProviderState.Incompatible?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Coding Workspace") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is UiState.Loading -> LoadingBody(padding)
            is UiState.Offline -> MessageBody(padding, "You're offline", "Some provider status checks need network access.", onRetry = viewModel::refresh)
            is UiState.Error -> MessageBody(padding, "Couldn't load the dashboard", s.message, onRetry = viewModel::refresh)
            is UiState.Success -> HomeContent(
                data = s.data,
                padding = padding,
                onOpenProjects = onOpenProjects,
                onOpenSettings = onOpenSettings,
                onOpenDiagnostics = onOpenDiagnostics,
                onLaunchProvider = onLaunchProvider,
                onOpenProject = onOpenProject,
                onInstallOrUpdate = viewModel::installOrUpdateProvider,
                onShowIncompatible = { incompatibleDetails = it },
            )
        }
    }

    installProgress?.let { progress ->
        InstallProgressDialog(progress, onDismiss = viewModel::dismissInstallProgress)
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
private fun InstallProgressDialog(progress: InstallProgressUi, onDismiss: () -> Unit) {
    val event = progress.latestEvent
    AlertDialog(
        onDismissRequest = { if (progress.done) onDismiss() },
        title = { Text(if (progress.done) "${progress.displayName}" else "Installing ${progress.displayName}") },
        text = {
            Column {
                when (event) {
                    is InstallEvent.Progress -> {
                        Text(event.step, style = MaterialTheme.typography.bodyMedium)
                        event.logLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(8.dp))
                        val fraction = event.fraction
                        if (fraction != null) {
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is InstallEvent.Completed -> Text("Installed successfully.")
                    is InstallEvent.Failed -> Text("Failed at '${event.step}': ${event.reason}", color = MaterialTheme.colorScheme.error)
                    null -> {
                        Text("Starting…", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (progress.done) {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
    )
}

@Composable
private fun HomeContent(
    data: HomeUiData,
    padding: PaddingValues,
    onOpenProjects: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onLaunchProvider: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onInstallOrUpdate: (String) -> Unit,
    onShowIncompatible: (ProviderState.Incompatible) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Runtime health", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${data.healthPassCount}/${data.healthTotalCount} checks passing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (data.healthPassCount == data.healthTotalCount) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = null,
                        tint = if (data.healthPassCount == data.healthTotalCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item { Text("AI providers", style = MaterialTheme.typography.titleMedium) }
        items(data.providerCards) { card ->
            ProviderCardView(
                card,
                onClick = {
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

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recent projects", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onOpenProjects) { Text("All projects") }
            }
        }
        if (data.recentProjects.isEmpty()) {
            item {
                Card(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
                    Text("No projects yet — tap to create one", modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            items(data.recentProjects) { project ->
                Card(onClick = { onOpenProject(project.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            project.root.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
        }
    }
}

@Composable
private fun ProviderCardView(card: ProviderCard, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(card.provider.displayName, style = MaterialTheme.typography.titleMedium)
                Text(stateLabel(card.state), style = MaterialTheme.typography.bodyMedium, color = stateColor(card.state))
            }
            Text(stateAction(card.state), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun stateLabel(state: ProviderState): String = when (state) {
    is ProviderState.NotInstalled -> "Not installed"
    is ProviderState.Installing -> state.stepDescription
    is ProviderState.Installed -> "Installed ${state.version}"
    is ProviderState.UpdateAvailable -> "Update available (${state.currentVersion} → ${state.latestVersion})"
    is ProviderState.AuthRequired -> "Sign-in required"
    is ProviderState.Ready -> "Ready — ${state.version}"
    is ProviderState.Error -> "Error: ${state.reason}"
    is ProviderState.Incompatible -> "Incompatible: ${state.reason}"
}

private fun stateAction(state: ProviderState): String = when (state) {
    is ProviderState.NotInstalled -> "INSTALL"
    is ProviderState.Installing -> "…"
    is ProviderState.Installed -> "OPEN"
    is ProviderState.UpdateAvailable -> "UPDATE"
    is ProviderState.AuthRequired -> "SIGN IN"
    is ProviderState.Ready -> "LAUNCH"
    is ProviderState.Error -> "RETRY"
    is ProviderState.Incompatible -> "DETAILS"
}

@Composable
private fun stateColor(state: ProviderState) = when (state) {
    is ProviderState.Ready -> MaterialTheme.colorScheme.primary
    is ProviderState.Error, is ProviderState.Incompatible -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun LoadingBody(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageBody(padding: PaddingValues, title: String, message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
