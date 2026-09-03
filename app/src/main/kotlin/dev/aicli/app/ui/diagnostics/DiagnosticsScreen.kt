package dev.aicli.app.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.aicli.app.ui.common.UiState
import dev.aicli.runtime.health.CheckStatus
import dev.aicli.runtime.health.HealthCheckResult

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    Scaffold(topBar = { TopAppBar(title = { Text("Diagnostics") }) }) { padding ->
        when (val s = state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Running diagnostics…", modifier = Modifier.padding(top = 12.dp))
                }
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Diagnostics failed: ${s.message}")
                    Button(onClick = viewModel::runDiagnostics, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
                }
            }
            is UiState.Offline -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Offline") }
            is UiState.Success -> Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = viewModel::runDiagnostics) { Text("Run Full Diagnostics") }
                    Button(onClick = { clipboard.setText(AnnotatedString(viewModel.exportText(s.data))) }) { Text("Copy Diagnostics") }
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.data) { result -> CheckRow(result) }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(result: HealthCheckResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = when (result.status) {
                CheckStatus.PASS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
                CheckStatus.FAIL -> Icons.Filled.Error to MaterialTheme.colorScheme.error
                CheckStatus.NOT_CHECKED -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = result.status.name, tint = tint, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text(result.label, style = MaterialTheme.typography.titleMedium)
                Text(result.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
