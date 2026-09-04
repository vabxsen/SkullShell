package dev.aicli.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.aicli.app.ui.common.ProviderCard
import dev.aicli.app.ui.design.Glyphs
import dev.aicli.app.ui.design.IconAction
import dev.aicli.app.ui.design.Label
import dev.aicli.app.ui.design.LinearProgress
import dev.aicli.app.ui.design.Menu
import dev.aicli.app.ui.design.MenuItem
import dev.aicli.app.ui.design.OutlineButton
import dev.aicli.app.ui.design.Panel
import dev.aicli.app.ui.design.PrimaryButton
import dev.aicli.app.ui.design.Rule
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.StatusChip
import dev.aicli.app.ui.design.StatusTone
import dev.aicli.app.ui.design.Text
import dev.aicli.provider.api.ProviderState

/** [ProviderPanelVariant.Compact] drops the tagline and the overflow; used in dense lists. */
enum class ProviderPanelVariant { Compact, Full }

/** One overflow action ("Update", "Repair", "Uninstall", "Authenticate", "Diagnostics", ...). */
data class ProviderOverflowAction(val label: String, val onClick: () -> Unit)

private data class StatusDescriptor(
    val label: String,
    val tone: StatusTone,
    val actionLabel: String,
    val actionEnabled: Boolean,
    /**
     * True only for states that are asking for attention *relative to normal operation* -
     * an update waiting, a sign-in expired, a failure to retry. A fresh install is not an
     * alert, it is the starting state, so it gets a quiet outline; otherwise a first run
     * would be a column of four identical inverted blocks and the inversion would stop
     * meaning anything at all.
     */
    val actionPrimary: Boolean,
    val progressFraction: Float?,
)

private fun descriptorFor(state: ProviderState): StatusDescriptor = when (state) {
    is ProviderState.NotInstalled ->
        StatusDescriptor("Not installed", StatusTone.NEUTRAL, "Install", true, false, null)
    is ProviderState.Installing ->
        StatusDescriptor(state.stepDescription, StatusTone.INFO, "Installing", false, false, state.progressFraction)
    is ProviderState.Installed ->
        StatusDescriptor("Installed - v" + state.version, StatusTone.SUCCESS, "Open", true, false, null)
    is ProviderState.UpdateAvailable ->
        StatusDescriptor("Update available", StatusTone.WARNING, "Update", true, true, null)
    is ProviderState.AuthRequired ->
        StatusDescriptor("Sign-in required", StatusTone.WARNING, "Sign in", true, true, null)
    is ProviderState.Ready ->
        StatusDescriptor("Ready - v" + state.version, StatusTone.SUCCESS, "Open", true, false, null)
    is ProviderState.Error ->
        StatusDescriptor("Error", StatusTone.ERROR, "Retry", true, true, null)
    is ProviderState.Incompatible ->
        StatusDescriptor("Incompatible", StatusTone.ERROR, "Details", true, false, null)
}

/** Short, static presentation copy - not a claim about runtime state, so safe to hardcode. */
private val providerTaglines = mapOf(
    "claude_code" to "Anthropic coding agent",
    "codex_cli" to "OpenAI coding agent",
    "opencode" to "Open-source coding agent",
    "antigravity_cli" to "Google coding agent",
)

/**
 * A provider is one of the few things in this UI that gets a [Panel] rather than a plain row:
 * it carries a state, a primary action and an overflow, and needs to be readable as a single
 * object. The layout is fixed - identity on top, a rule, then status on the left and the action
 * on the right - so a column of four providers scans as a table, not as four little posters.
 *
 * Only the states that want the user to *do* something (install, update, sign in, retry) get an
 * inverted [PrimaryButton]; a working provider gets a quiet outline, because "Open" is an
 * invitation, not a task.
 */
@Composable
fun ProviderPanel(
    card: ProviderCard,
    variant: ProviderPanelVariant,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    overflowActions: List<ProviderOverflowAction> = emptyList(),
) {
    val descriptor = descriptorFor(card.state)
    val colors = SkullTheme.colors
    Panel(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Space.x4, top = Space.x4, bottom = Space.x4, end = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphTile()
            Column(Modifier.weight(1f).padding(start = Space.x4)) {
                Text(card.provider.displayName, style = SkullTheme.type.heading, color = colors.ink, maxLines = 1)
                if (variant == ProviderPanelVariant.Full) {
                    providerTaglines[card.provider.id]?.let {
                        Label(it, color = colors.inkFaint, modifier = Modifier.padding(top = Space.x1))
                    }
                }
            }
            if (overflowActions.isNotEmpty()) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconAction(
                        icon = Glyphs.Dots,
                        contentDescription = "More actions for " + card.provider.displayName,
                        onClick = { menuOpen = true },
                    )
                    Menu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        items = overflowActions.map { MenuItem(it.label, it.onClick) },
                    )
                }
            }
        }

        Rule()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(descriptor.label, descriptor.tone, modifier = Modifier.weight(1f, fill = false))
            Box(Modifier.padding(start = Space.x3)) {
                when {
                    descriptor.actionEnabled && descriptor.actionPrimary ->
                        PrimaryButton(descriptor.actionLabel, onPrimaryAction)
                    descriptor.actionEnabled ->
                        OutlineButton(descriptor.actionLabel, onPrimaryAction)
                    else -> Label(descriptor.actionLabel, color = colors.inkMuted)
                }
            }
        }

        if (!descriptor.actionEnabled) {
            LinearProgress(fraction = descriptor.progressFraction)
        }
    }
}
