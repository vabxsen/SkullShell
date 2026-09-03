package dev.aicli.app.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.common.UiState
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.components.LoadingState
import dev.aicli.app.ui.components.ProviderCard
import dev.aicli.app.ui.components.ProviderCardVariant
import dev.aicli.app.ui.components.SectionHeader
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.runtime.health.CheckStatus
import dev.aicli.runtime.health.HealthCheckResult

/** UI-layer grouping only — [dev.aicli.runtime.health.RuntimeHealthChecker] itself stays
 *  provider-agnostic; this mapping does not change what's actually checked. */
private val runtimeHealthIds = setOf("bootstrap", "termux_exec")
private val systemIds = setOf("abi", "cmd_node", "cmd_npm", "cmd_git", "pty", "fs", "network")

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Scaffold(topBar = { TopAppBar(title = { Text("Diagnostics") }) }) { padding ->
        when (val s = state) {
            is UiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding), label = "Running diagnostics…")
            is UiState.Error -> ErrorState(
                title = "Diagnostics failed",
                body = s.message,
                onRetry = viewModel::runDiagnostics,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Offline -> ErrorState(
                title = "You're offline",
                body = "The network check will fail until you're back online; everything else still ran.",
                icon = Icons.Filled.WifiOff,
                onRetry = viewModel::runDiagnostics,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Success -> Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(Dimens.space16), horizontalArrangement = Arrangement.spacedBy(Dimens.space12)) {
                    Button(onClick = viewModel::runDiagnostics) { Text("Run Full Diagnostics") }
                    Button(onClick = { clipboard.setText(AnnotatedString(viewModel.exportText(s.data))) }) { Text("Copy Diagnostics") }
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Dimens.space16, vertical = Dimens.space8),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space8),
                ) {
                    val runtimeHealth = s.data.healthChecks.filter { it.id in runtimeHealthIds }
                    val system = s.data.healthChecks.filter { it.id in systemIds }

                    if (runtimeHealth.isNotEmpty()) {
                        item { SectionHeader("Runtime health", modifier = Modifier.padding(vertical = Dimens.space8)) }
                        items(runtimeHealth, key = { it.id }) { CheckRow(it) }
                    }
                    if (s.data.providerStates.isNotEmpty()) {
                        item { SectionHeader("AI providers", modifier = Modifier.padding(vertical = Dimens.space8)) }
                        items(s.data.providerStates, key = { it.provider.id }) { card ->
                            ProviderCard(card = card, variant = ProviderCardVariant.Compact, onPrimaryAction = {})
                        }
                    }
                    if (system.isNotEmpty()) {
                        item { SectionHeader("System", modifier = Modifier.padding(vertical = Dimens.space8)) }
                        items(system, key = { it.id }) { CheckRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(result: HealthCheckResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(Dimens.space16), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            val (statusIcon, tint) = when (result.status) {
                CheckStatus.PASS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
                CheckStatus.FAIL -> Icons.Filled.Error to MaterialTheme.colorScheme.error
                CheckStatus.NOT_CHECKED -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(statusIcon, contentDescription = result.status.name, tint = tint, modifier = Modifier.padding(end = Dimens.space12))
            Column {
                Text(result.label, style = MaterialTheme.typography.titleSmall)
                Text(result.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
