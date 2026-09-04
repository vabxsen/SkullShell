package dev.aicli.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.aicli.app.ui.common.ProviderCard
import dev.aicli.app.ui.design.*
import dev.aicli.provider.api.ProviderState

enum class ProviderPanelVariant { Compact, Full }
data class ProviderOverflowAction(val label: String, val onClick: () -> Unit)
private data class AgentPresentation(val label: String, val tone: StatusTone, val action: String, val enabled: Boolean = true)
private fun presentation(state: ProviderState): AgentPresentation = when (state) {
    is ProviderState.NotInstalled -> AgentPresentation("Not installed", StatusTone.NEUTRAL, "Install")
    is ProviderState.Installing -> AgentPresentation("Installing", StatusTone.INFO, "Installing…", false)
    is ProviderState.Installed -> AgentPresentation("Installed", StatusTone.SUCCESS, "Open")
    is ProviderState.Ready -> AgentPresentation("Ready", StatusTone.SUCCESS, "Open")
    is ProviderState.UpdateAvailable -> AgentPresentation("Update available", StatusTone.WARNING, "Update")
    is ProviderState.AuthRequired -> AgentPresentation("Sign in required", StatusTone.WARNING, "Sign in")
    is ProviderState.Error -> AgentPresentation("Needs attention", StatusTone.ERROR, "Retry")
    is ProviderState.Incompatible -> AgentPresentation("Unavailable", StatusTone.WARNING, "Details")
}
private val makers = mapOf("claude_code" to "Anthropic", "codex_cli" to "OpenAI", "opencode" to "Open source", "antigravity_cli" to "Google")

@Composable
fun ProviderPanel(card: ProviderCard, variant: ProviderPanelVariant, onPrimaryAction: () -> Unit,
                  modifier: Modifier = Modifier, overflowActions: List<ProviderOverflowAction> = emptyList()) {
    val state = presentation(card.state)
    Panel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.x4)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ProviderMark(card.provider.id)
                Column(Modifier.weight(1f).padding(horizontal = Space.x4)) {
                    Text(card.provider.displayName, style = SkullTheme.type.heading)
                    val version = when (val s = card.state) { is ProviderState.Ready -> s.version; is ProviderState.Installed -> s.version; else -> null }
                    Text(listOfNotNull(makers[card.provider.id], version?.let { "v$it" }).joinToString(" · "),
                        style = SkullTheme.type.bodySm, color = SkullTheme.colors.inkMuted)
                }
                if (overflowActions.isNotEmpty()) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        IconAction(Glyphs.Dots, "More actions for ${card.provider.displayName}", { open = true })
                        Menu(open, { open = false }, overflowActions.map { MenuItem(it.label, it.onClick) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.x3), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { StatusChip(state.label, state.tone) }
                if (variant == ProviderPanelVariant.Full) TonalButton(state.action, onPrimaryAction, enabled = state.enabled,
                    glyph = if (card.state is ProviderState.NotInstalled) Glyphs.Download else null)
            }
            if (card.state is ProviderState.Installing) LinearProgress(fraction = card.state.progressFraction, modifier = Modifier.padding(top = Space.x3))
        }
    }
}
