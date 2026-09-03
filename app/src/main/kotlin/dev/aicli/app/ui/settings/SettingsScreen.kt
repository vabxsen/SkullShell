package dev.aicli.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aicli.provider.api.ProviderState

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val data by viewModel.uiData.collectAsState()
    val repairState by viewModel.repairState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { androidx.compose.material3.Text("Settings") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item { SectionHeader("Terminal") }
            data?.terminal?.let { terminal ->
                item {
                    Column {
                        Text("Font size: ${terminal.fontSize.toInt()}sp")
                        Slider(
                            value = terminal.fontSize,
                            onValueChange = { newSize -> viewModel.updateTerminal { it.copy(fontSize = newSize) } },
                            valueRange = 9f..24f,
                        )
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Cursor blink")
                        Switch(checked = terminal.cursorBlink, onCheckedChange = { viewModel.updateTerminal { s -> s.copy(cursorBlink = it) } })
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Copy on select")
                        Switch(checked = terminal.copyOnSelect, onCheckedChange = { viewModel.updateTerminal { s -> s.copy(copyOnSelect = it) } })
                    }
                }
            }

            item { Divider(Modifier.padding(vertical = 12.dp)) }
            item { SectionHeader("Runtime") }
            item {
                Column {
                    Text("Repair runtime re-installs the Linux userland from scratch.", style = MaterialTheme.typography.bodySmall)
                    repairState?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = viewModel::repairRuntime) { Text("Repair runtime") }
                }
            }

            item { Divider(Modifier.padding(vertical = 12.dp)) }
            item { SectionHeader("AI Providers") }
            items(viewModel.providers) { provider ->
                Text("${provider.displayName}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
            }

            item { Divider(Modifier.padding(vertical = 12.dp)) }
            item { SectionHeader("Advanced") }
            data?.advanced?.let { advanced ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Debug logging")
                        Switch(checked = advanced.debugLogging, onCheckedChange = viewModel::setDebugLogging)
                    }
                }
            }
            item {
                TextButton(onClick = viewModel::resetAllSettings) { Text("Reset all settings") }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
}
