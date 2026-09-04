@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.aicli.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.BuildConfig
import dev.aicli.app.ui.components.InstallProgressSheet
import dev.aicli.app.ui.components.SettingsRow
import dev.aicli.app.ui.design.*
import dev.aicli.core.settings.ThemeMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenHome: () -> Unit,
                   onOpenProjects: () -> Unit, onOpenTerminal: () -> Unit, onOpenProviders: () -> Unit,
                   onOpenDiagnostics: () -> Unit) {
    val data by viewModel.uiData.collectAsStateWithLifecycle()
    val repairProgress by viewModel.repairProgress.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
    var confirmRepair by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Screen(topBar = { TopBar("Settings", onBack = onBack, showSettings = false) }) {
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = Metrics.gutter, end = Metrics.gutter, top = Space.x2, bottom = Space.x8),
            verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            item { SettingsSection("Workspace") }
            item {
                Panel {
                    SettingsRow("Home", icon = Glyphs.Home, onClick = onOpenHome)
                    SettingsRow("Projects", icon = Glyphs.Folder, onClick = onOpenProjects)
                    SettingsRow("Terminal", icon = Glyphs.Terminal, onClick = onOpenTerminal)
                    SettingsRow("Agents", icon = Glyphs.Grid, onClick = onOpenProviders)
                    SettingsRow("Diagnostics", icon = Glyphs.Info, onClick = onOpenDiagnostics)
                }
            }
            item { SettingsSection("Appearance") }
            item {
                Panel {
                    Column(Modifier.padding(Space.x4)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(Glyphs.Palette, null, tint = scheme.primary)
                            Text("App theme", style = SkullTheme.type.heading, modifier = Modifier.padding(start = Space.x4))
                        }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Space.x4)) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(selected = (data?.appearance?.themeMode ?: ThemeMode.SYSTEM) == mode,
                                    onClick = { viewModel.setThemeMode(mode) }, shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)) {
                                    androidx.compose.material3.Text(when (mode) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" })
                                }
                            }
                        }
                    }
                    SettingsRow("Wallpaper colors", description = "Match your device's color palette", icon = Glyphs.Palette, trailing = {
                        Toggle(data?.appearance?.dynamicColorEnabled ?: true, viewModel::setDynamicColorEnabled,
                            Modifier.semantics { contentDescription = "Wallpaper colors" })
                    })
                }
            }
            item { SettingsSection("Terminal") }
            data?.terminal?.let { terminal ->
                item {
                    Panel {
                        Column(Modifier.padding(Space.x4)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Text size", modifier = Modifier.weight(1f))
                                Text("${terminal.fontSize.toInt()} sp", style = SkullTheme.type.label, color = scheme.primary)
                            }
                            Slider(terminal.fontSize, { viewModel.updateTerminal { settings -> settings.copy(fontSize = it) } },
                                9f..24f, Modifier.semantics { contentDescription = "Terminal text size" })
                            Surface(shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainerHighest) {
                                Text("Aa Bb 0123456789", style = SkullTheme.type.mono.copy(fontSize = terminal.fontSize.sp),
                                    modifier = Modifier.fillMaxWidth().padding(Space.x4), maxLines = 1)
                            }
                        }
                        SettingsRow("Blinking cursor", icon = Glyphs.Terminal, trailing = {
                            Toggle(terminal.cursorBlink, { viewModel.updateTerminal { settings -> settings.copy(cursorBlink = it) } },
                                Modifier.semantics { contentDescription = "Blinking cursor" })
                        })
                        SettingsRow("Copy on selection", icon = Glyphs.Copy, trailing = {
                            Toggle(terminal.copyOnSelect, { viewModel.updateTerminal { settings -> settings.copy(copyOnSelect = it) } },
                                Modifier.semantics { contentDescription = "Copy on selection" })
                        })
                    }
                }
            }
            item { SettingsSection("Environment") }
            item {
                Panel { SettingsRow("Set up or repair runtime", description = "Install the Linux terminal environment", icon = Glyphs.Download, onClick = { confirmRepair = true }) }
            }
            item { SettingsSection("Advanced") }
            item {
                Panel {
                    SettingsRow("Debug logging", description = "Include additional diagnostic details", icon = Glyphs.Code, trailing = {
                        Toggle(data?.advanced?.debugLogging ?: false, viewModel::setDebugLogging,
                            Modifier.semantics { contentDescription = "Debug logging" })
                    })
                    SettingsRow("Reset preferences", icon = Glyphs.Refresh, onClick = { confirmReset = true })
                }
            }
            item { SettingsSection("About") }
            item {
                Panel {
                    SettingsRow("SkullShell", icon = Glyphs.Terminal, value = "v${BuildConfig.VERSION_NAME}")
                    SettingsRow("Check for updates", description = updateStatus, icon = Glyphs.Info, onClick = viewModel::checkForUpdate)
                }
            }
        }
    }
    repairProgress?.let { InstallProgressSheet(it, viewModel::dismissRepairProgress) }
    updateProgress?.let { InstallProgressSheet(it, viewModel::dismissUpdateProgress) }
    if (confirmRepair) Modal("Set up the runtime", { confirmRepair = false }, actions = {
        GhostButton("Cancel", { confirmRepair = false })
        GhostButton("Continue", { confirmRepair = false; viewModel.repairRuntime() })
    }) { Text("This installs a fresh Linux environment. Existing packages and agent binaries in the runtime are replaced; your projects and home files are kept. Close your terminal sessions first, then reinstall any agents you use.", color = scheme.onSurfaceVariant) }
    if (confirmReset) Modal("Reset preferences?", { confirmReset = false }, actions = {
        GhostButton("Cancel", { confirmReset = false })
        GhostButton("Reset", { viewModel.resetAllSettings(); confirmReset = false })
    }) { Text("Appearance and terminal preferences will return to their defaults.", color = scheme.onSurfaceVariant) }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Space.x4, top = Space.x3, bottom = Space.x1))
}
