package dev.aicli.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.aicli.app.BuildConfig
import dev.aicli.app.ui.components.InstallProgressSheet
import dev.aicli.app.ui.components.SettingsRow
import dev.aicli.app.ui.design.GhostButton
import dev.aicli.app.ui.design.Label
import dev.aicli.app.ui.design.Metrics
import dev.aicli.app.ui.design.Modal
import dev.aicli.app.ui.design.PageTitle
import dev.aicli.app.ui.design.RadioMark
import dev.aicli.app.ui.design.Rule
import dev.aicli.app.ui.design.Screen
import dev.aicli.app.ui.design.SectionHeader
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Slider
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.Text
import dev.aicli.app.ui.design.Toggle
import dev.aicli.app.ui.design.TopBar
import dev.aicli.app.ui.design.pressable
import dev.aicli.core.settings.ThemeMode

private val MAX_MEASURE = 720.dp

/**
 * The "Dynamic color" preference is gone, and not by oversight: it derived the palette from the
 * user's wallpaper, which cannot mean anything in an app with no palette to derive. Theme mode
 * stays, because light and dark are both real here - the scheme inverts rather than recolouring.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val data by viewModel.uiData.collectAsStateWithLifecycle()
    val repairProgress by viewModel.repairProgress.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }

    Screen(topBar = { TopBar(crumb = "SkullShell / Settings") }) {
        Box(Modifier.widthIn(max = MAX_MEASURE).fillMaxSize().align(Alignment.TopCenter)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Space.x12),
            ) {
                item {
                    PageTitle(
                        title = "Settings",
                        modifier = Modifier.padding(horizontal = Metrics.gutter, vertical = Space.x6),
                    )
                }

                section("Appearance")
                data?.appearance?.let { appearance ->
                    item {
                        SettingsRow(
                            title = "Theme",
                            value = themeModeLabel(appearance.themeMode),
                            onClick = { showThemeDialog = true },
                        )
                        Rule()
                    }
                }

                section("Terminal")
                data?.terminal?.let { terminal ->
                    item {
                        Column(Modifier.padding(horizontal = Metrics.gutter, vertical = Space.x4)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Font size", style = SkullTheme.type.body, color = SkullTheme.colors.ink)
                                Rule(Modifier.padding(horizontal = Space.x3).weight(1f))
                                Text(
                                    terminal.fontSize.toInt().toString() + "sp",
                                    style = SkullTheme.type.mono,
                                    color = SkullTheme.colors.inkMuted,
                                )
                            }
                            Slider(
                                value = terminal.fontSize,
                                onValueChange = { newSize -> viewModel.updateTerminal { it.copy(fontSize = newSize) } },
                                valueRange = 9f..24f,
                            )
                        }
                        Rule()
                    }
                    item {
                        SettingsRow(
                            title = "Cursor blink",
                            trailing = {
                                Toggle(
                                    checked = terminal.cursorBlink,
                                    onCheckedChange = { on -> viewModel.updateTerminal { it.copy(cursorBlink = on) } },
                                )
                            },
                        )
                        Rule()
                    }
                    item {
                        SettingsRow(
                            title = "Copy on select",
                            trailing = {
                                Toggle(
                                    checked = terminal.copyOnSelect,
                                    onCheckedChange = { on -> viewModel.updateTerminal { it.copy(copyOnSelect = on) } },
                                )
                            },
                        )
                        Rule()
                    }
                }

                section("Runtime")
                item {
                    SettingsRow(
                        title = "Repair runtime",
                        description = "Re-installs the Linux userland from scratch.",
                        onClick = viewModel::repairRuntime,
                    )
                    Rule()
                }

                section("Providers")
                items(viewModel.providers, key = { it.id }) { provider ->
                    SettingsRow(title = provider.displayName, value = provider.id)
                    Rule()
                }

                section("Advanced")
                data?.advanced?.let { advanced ->
                    item {
                        SettingsRow(
                            title = "Debug logging",
                            trailing = {
                                Toggle(checked = advanced.debugLogging, onCheckedChange = viewModel::setDebugLogging)
                            },
                        )
                        Rule()
                    }
                }
                item {
                    SettingsRow(title = "Reset all settings", onClick = viewModel::resetAllSettings)
                    Rule()
                }

                section("About")
                item {
                    SettingsRow(title = "SkullShell", value = "v" + BuildConfig.VERSION_NAME)
                    Rule()
                }
                item {
                    SettingsRow(
                        title = "Check for update",
                        description = updateStatus,
                        onClick = viewModel::checkForUpdate,
                    )
                    Rule()
                }
            }
        }
    }

    repairProgress?.let { InstallProgressSheet(it, onDismiss = viewModel::dismissRepairProgress) }
    updateProgress?.let { InstallProgressSheet(it, onDismiss = viewModel::dismissUpdateProgress) }

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

/** Section headings sit above the rules that separate the rows beneath them. */
private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String) {
    item {
        SectionHeader(
            title,
            Modifier.padding(
                start = Metrics.gutter,
                end = Metrics.gutter,
                top = Space.x6,
                bottom = Space.x3,
            ),
        )
        Rule()
    }
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun ThemeModeDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    Modal(
        title = "Theme",
        onDismiss = onDismiss,
        actions = { GhostButton("Close", onDismiss) },
    ) {
        ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { onSelect(mode) }
                    .padding(vertical = Space.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioMark(selected = mode == current)
                Label(
                    themeModeLabel(mode),
                    color = if (mode == current) SkullTheme.colors.ink else SkullTheme.colors.inkMuted,
                    modifier = Modifier.padding(start = Space.x4),
                )
            }
        }
    }
}
