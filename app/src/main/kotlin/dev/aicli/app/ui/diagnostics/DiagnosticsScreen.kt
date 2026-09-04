package dev.aicli.app.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.common.UiState
import dev.aicli.app.ui.components.*
import dev.aicli.app.ui.design.*
import dev.aicli.runtime.health.CheckStatus
import dev.aicli.runtime.health.HealthCheckResult

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Screen(topBar = { TopBar("Diagnostics", onBack = onBack) }) {
        Box(Modifier.widthIn(max = 760.dp).fillMaxSize().align(Alignment.TopCenter)) {
            when (val s = state) {
                is UiState.Loading -> LoadingBody(Modifier.fillMaxSize(), "Checking environment")
                is UiState.Error -> ErrorState("Could not complete checks", s.message, onRetry = viewModel::runDiagnostics)
                is UiState.Offline -> ErrorState("Offline", "Reconnect to complete the network checks.", onRetry = viewModel::runDiagnostics)
                is UiState.Success -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Metrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                    item {
                        val passed = s.data.healthChecks.count { it.status == CheckStatus.PASS }
                        val failed = s.data.healthChecks.count { it.status == CheckStatus.FAIL }
                        Panel(fill = scheme.secondaryContainer) {
                            Column(Modifier.padding(Space.x6)) {
                                Glyph(Glyphs.Info, null, tint = scheme.onSecondaryContainer)
                                Text("Environment checks", style = SkullTheme.type.title, color = scheme.onSecondaryContainer,
                                    modifier = Modifier.padding(top = Space.x3))
                                Text("$passed passed · $failed failed · ${s.data.healthChecks.size} total", style = SkullTheme.type.bodySm,
                                    color = scheme.onSecondaryContainer, modifier = Modifier.padding(top = Space.x2, bottom = Space.x4))
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                                    PrimaryButton("Run again", { copied = false; viewModel.runDiagnostics() }, glyph = Glyphs.Refresh)
                                    GhostButton(if (copied) "Copied" else "Copy report", {
                                        clipboard.setText(AnnotatedString(viewModel.exportText(s.data))); copied = true
                                    })
                                }
                            }
                        }
                    }
                    item {
                        Panel {
                            s.data.healthChecks.forEachIndexed { index, result ->
                                CheckRow(result)
                                if (index < s.data.healthChecks.lastIndex) Rule(Modifier.padding(horizontal = Space.x4))
                            }
                        }
                    }
                    if (s.data.providerStates.isNotEmpty()) {
                        item { SectionHeader("Agent status", Modifier.padding(horizontal = Space.x2)) }
                        items(s.data.providerStates, key = { it.provider.id }) { ProviderPanel(it, ProviderPanelVariant.Compact, onPrimaryAction = {}) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(result: HealthCheckResult) {
    val tone = when (result.status) { CheckStatus.PASS -> StatusTone.SUCCESS; CheckStatus.FAIL -> StatusTone.ERROR; CheckStatus.NOT_CHECKED -> StatusTone.NEUTRAL }
    Column(Modifier.fillMaxWidth().padding(Space.x4)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(result.label, style = SkullTheme.type.heading, modifier = Modifier.weight(1f).padding(end = Space.x2))
            StatusChip(when (result.status) { CheckStatus.PASS -> "Passed"; CheckStatus.FAIL -> "Failed"; CheckStatus.NOT_CHECKED -> "Not checked" }, tone)
        }
        Text(result.detail, style = SkullTheme.type.monoSm, color = SkullTheme.colors.inkMuted, modifier = Modifier.padding(top = Space.x2))
    }
}
