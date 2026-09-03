package dev.aicli.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.BuildConfig
import dev.aicli.app.ui.components.InstallProgressSheet
import dev.aicli.app.ui.components.SectionHeader
import dev.aicli.app.ui.components.SettingsItem
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.core.settings.ThemeMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val data by viewModel.uiData.collectAsStateWithLifecycle()
    val repairProgress by viewModel.repairProgress.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding), contentPadding = PaddingValues(Dimens.space16)) {
            item { SectionHeader("Appearance") }
            data?.appearance?.let { appearance ->
                item {
                    SettingsItem(
                        title = "Theme",
                        description = themeModeLabel(appearance.themeMode),
                        onClick = { showThemeDialog = true },
                    )
                }
                item {
                    SettingsItem(
                        title = "Dynamic color",
                        description = "Derive app colors from your wallpaper",
                        trailing = {
                            Switch(checked = appearance.dynamicColorEnabled, onCheckedChange = viewModel::setDynamicColorEnabled)
                        },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = Dimens.space12)) }
            item { SectionHeader("Terminal") }
            data?.terminal?.let { terminal ->
                item {
                    Column(Modifier.padding(vertical = Dimens.space8)) {
                        Text("Font size: ${terminal.fontSize.toInt()}sp", style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value = terminal.fontSize,
                            onValueChange = { newSize -> viewModel.updateTerminal { it.copy(fontSize = newSize) } },
                            valueRange = 9f..24f,
                        )
                    }
                }
                item {
                    SettingsItem(
                        title = "Cursor blink",
                        trailing = { Switch(checked = terminal.cursorBlink, onCheckedChange = { viewModel.updateTerminal { s -> s.copy(cursorBlink = it) } }) },
                    )
                }
                item {
                    SettingsItem(
                        title = "Copy on select",
                        trailing = { Switch(checked = terminal.copyOnSelect, onCheckedChange = { viewModel.updateTerminal { s -> s.copy(copyOnSelect = it) } }) },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = Dimens.space12)) }
            item { SectionHeader("Runtime") }
            item {
                SettingsItem(
                    title = "Repair runtime",
                    description = "Re-installs the Linux userland from scratch.",
                    onClick = viewModel::repairRuntime,
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = Dimens.space12)) }
            item { SectionHeader("AI Providers") }
            items(viewModel.providers, key = { it.id }) { provider ->
                SettingsItem(title = provider.displayName)
            }

            item { HorizontalDivider(Modifier.padding(vertical = Dimens.space12)) }
            item { SectionHeader("Advanced") }
            data?.advanced?.let { advanced ->
                item {
                    SettingsItem(
                        title = "Debug logging",
                        trailing = { Switch(checked = advanced.debugLogging, onCheckedChange = viewModel::setDebugLogging) },
                    )
                }
            }
            item {
                SettingsItem(title = "Reset all settings", onClick = viewModel::resetAllSettings)
            }

            item { HorizontalDivider(Modifier.padding(vertical = Dimens.space12)) }
            item { SectionHeader("About") }
            item { SettingsItem(title = "SkullShell", description = "Version ${BuildConfig.VERSION_NAME}") }
        }
    }

    repairProgress?.let { progress ->
        InstallProgressSheet(progress, onDismiss = viewModel::dismissRepairProgress)
    }

    if (showThemeDialog) {
        data?.appearance?.let { appearance ->
            ThemeModeDialog(
                current = appearance.themeMode,
                onSelect = { viewModel.setThemeMode(it); showThemeDialog = false },
                onDismiss = { showThemeDialog = false },
            )
        }
    }
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun ThemeModeDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = { onSelect(mode) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
                        Text(themeModeLabel(mode))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
