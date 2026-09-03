package dev.aicli.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.aicli.app.ui.common.ProviderCard
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.provider.api.ProviderState

/** [ProviderCardVariant.Compact] = Home's grid tile. [ProviderCardVariant.Full] = Providers screen row. */
enum class ProviderCardVariant { Compact, Full }

/** One overflow action ("Update", "Repair", "Uninstall", "Authenticate", "Diagnostics", ...). */
data class ProviderOverflowAction(val label: String, val onClick: () -> Unit)

private data class ProviderStatusDescriptor(
    val label: String,
    val tone: StatusTone,
    val icon: ImageVector?,
    val actionLabel: String,
    val actionEnabled: Boolean,
    val progressFraction: Float?,
)

private fun descriptorFor(state: ProviderState): ProviderStatusDescriptor = when (state) {
    is ProviderState.NotInstalled -> ProviderStatusDescriptor("Not installed", StatusTone.NEUTRAL, null, "Install", true, null)
    is ProviderState.Installing -> ProviderStatusDescriptor(state.stepDescription, StatusTone.INFO, null, "Installing…", false, state.progressFraction)
    is ProviderState.Installed -> ProviderStatusDescriptor("Installed · v${state.version}", StatusTone.NEUTRAL, Icons.Filled.CheckCircle, "Open", true, null)
    is ProviderState.UpdateAvailable -> ProviderStatusDescriptor("Update available", StatusTone.WARNING, Icons.Filled.Warning, "Update", true, null)
    is ProviderState.AuthRequired -> ProviderStatusDescriptor("Sign-in required", StatusTone.WARNING, Icons.Filled.Warning, "Sign in", true, null)
    is ProviderState.Ready -> ProviderStatusDescriptor("Ready · v${state.version}", StatusTone.SUCCESS, Icons.Filled.CheckCircle, "Open", true, null)
    is ProviderState.Error -> ProviderStatusDescriptor("Error", StatusTone.ERROR, Icons.Filled.ErrorOutline, "Retry", true, null)
    is ProviderState.Incompatible -> ProviderStatusDescriptor("Incompatible", StatusTone.ERROR, Icons.Filled.Info, "Details", true, null)
}

/** Short, static presentation copy — not a claim about runtime state, so safe to hardcode here. */
private val providerTaglines = mapOf(
    "claude_code" to "Anthropic's coding agent",
    "codex_cli" to "OpenAI's coding agent",
    "opencode" to "Open-source coding agent",
    "antigravity_cli" to "Google's coding agent",
)

/**
 * The one reusable, state-driven provider card — covers every [ProviderState]. [variant]
 * controls density: [ProviderCardVariant.Compact] for Home's grid, [ProviderCardVariant.Full]
 * for the Providers screen's list rows (adds description/auth detail, larger touch target).
 */
@Composable
fun ProviderCard(
    card: ProviderCard,
    variant: ProviderCardVariant,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    overflowActions: List<ProviderOverflowAction> = emptyList(),
) {
    val descriptor = descriptorFor(card.state)
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(Dimens.space16)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderGlyph(card.provider.displayName)
                    Column(Modifier.padding(start = Dimens.space12)) {
                        Text(card.provider.displayName, style = MaterialTheme.typography.titleSmall)
                        if (variant == ProviderCardVariant.Full) {
                            providerTaglines[card.provider.id]?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (overflowActions.isNotEmpty()) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More actions for ${card.provider.displayName}")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            overflowActions.forEach { action ->
                                DropdownMenuItem(text = { Text(action.label) }, onClick = { menuOpen = false; action.onClick() })
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.space12),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(descriptor.label, descriptor.tone, icon = descriptor.icon)
                if (descriptor.actionEnabled) {
                    if (variant == ProviderCardVariant.Compact) {
                        TextButton(onClick = onPrimaryAction) { Text(descriptor.actionLabel) }
                    } else {
                        FilledTonalButton(onClick = onPrimaryAction) { Text(descriptor.actionLabel) }
                    }
                } else if (descriptor.progressFraction != null) {
                    CircularProgressIndicator(progress = { descriptor.progressFraction }, modifier = Modifier.size(20.dp))
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ProviderGlyph(displayName: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
