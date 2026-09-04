package dev.aicli.app.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.common.UiState
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.components.ProviderPanel
import dev.aicli.app.ui.components.ProviderPanelVariant
import dev.aicli.app.ui.design.Glyph
import dev.aicli.app.ui.design.Glyphs
import dev.aicli.app.ui.design.LoadingBody
import dev.aicli.app.ui.design.Metrics
import dev.aicli.app.ui.design.OutlineButton
import dev.aicli.app.ui.design.PageTitle
import dev.aicli.app.ui.design.PrimaryButton
import dev.aicli.app.ui.design.Rule
import dev.aicli.app.ui.design.Screen
import dev.aicli.app.ui.design.SectionHeader
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.Text
import dev.aicli.app.ui.design.TopBar
import dev.aicli.runtime.health.CheckStatus
import dev.aicli.runtime.health.HealthCheckResult

/** UI-layer grouping only - [dev.aicli.runtime.health.RuntimeHealthChecker] itself stays
 *  provider-agnostic; this mapping does not change what is actually checked. */
private val runtimeHealthIds = setOf("bootstrap", "termux_exec")
private val systemIds = setOf("abi", "cmd_node", "cmd_npm", "cmd_git", "pty", "fs", "network")

private val MAX_MEASURE = 720.dp

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Screen(topBar = { TopBar(crumb = "SkullShell / Diagnostics", onBack = onBack) }) {
        Box(Modifier.widthIn(max = MAX_MEASURE).fillMaxSize().align(Alignment.TopCenter)) {
            when (val s = state) {
                is UiState.Loading -> LoadingBody(Modifier.fillMaxSize(), label = "Running diagnostics")
                is UiState.Error -> ErrorState(
                    title = "Diagnostics failed",
                    body = s.message,
                    onRetry = viewModel::runDiagnostics,
                )
                is UiState.Offline -> ErrorState(
                    title = "Offline",
                    body = "The network check will fail until you are back online. Everything else still ran.",
                    glyph = Glyphs.NoSignal,
                    onRetry = viewModel::runDiagnostics,
                )
                is UiState.Success -> {
                    val runtimeHealth = s.data.healthChecks.filter { it.id in runtimeHealthIds }
                    val system = s.data.healthChecks.filter { it.id in systemIds }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Space.x12),
                    ) {
                        item {
                            Column(Modifier.padding(horizontal = Metrics.gutter)) {
                                PageTitle(
                                    title = "Diagnostics",
                                    subtitle = "What the runtime reports about this device.",
                                    modifier = Modifier.padding(vertical = Space.x6),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.x4),
                                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                                ) {
                                    PrimaryButton("Run all", viewModel::runDiagnostics, glyph = Glyphs.Refresh)
                                    OutlineButton(
                                        label = "Copy",
                                        onClick = { clipboard.setText(AnnotatedString(viewModel.exportText(s.data))) },
                                        glyph = Glyphs.Copy,
                                    )
                                }
                            }
                        }

                        if (runtimeHealth.isNotEmpty()) {
                            item { DiagnosticsSection("Runtime health") }
                            items(runtimeHealth, key = { it.id }) { CheckRow(it); Rule() }
                        }
                        if (s.data.providerStates.isNotEmpty()) {
                            item { DiagnosticsSection("Providers") }
                            items(s.data.providerStates, key = { it.provider.id }) { card ->
                                ProviderPanel(
                                    card = card,
                                    variant = ProviderPanelVariant.Compact,
                                    onPrimaryAction = {},
                                    modifier = Modifier.padding(
                                        horizontal = Metrics.gutter,
                                        vertical = Space.x2,
                                    ),
                                )
                            }
                        }
                        if (system.isNotEmpty()) {
                            item { DiagnosticsSection("System") }
                            items(system, key = { it.id }) { CheckRow(it); Rule() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(title: String) {
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

/**
 * A check reads as a table line: status glyph, label, then the raw detail in mono. The detail is
 * machine output, so it is set in the machine face - that distinction is what lets you skim the
 * left column for failures and only drop into the right column when one turns up.
 */
@Composable
private fun CheckRow(result: HealthCheckResult) {
    val colors = SkullTheme.colors
    val glyph = when (result.status) {
        CheckStatus.PASS -> Glyphs.CheckCircle
        CheckStatus.FAIL -> Glyphs.ErrorCircle
        CheckStatus.NOT_CHECKED -> Glyphs.Clock
    }
    val tint = when (result.status) {
        CheckStatus.PASS -> colors.ink
        CheckStatus.FAIL -> colors.ink
        CheckStatus.NOT_CHECKED -> colors.inkFaint
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Metrics.gutter, vertical = Space.x4),
        verticalAlignment = Alignment.Top,
    ) {
        Glyph(glyph, result.status.name, size = Metrics.glyphMd, tint = tint)
        Column(Modifier.weight(1f).padding(start = Space.x4)) {
            Text(result.label, style = SkullTheme.type.body, color = colors.ink)
            Text(
                result.detail,
                style = SkullTheme.type.monoSm,
                color = colors.inkMuted,
                modifier = Modifier.padding(top = Space.x1),
            )
        }
    }
}
