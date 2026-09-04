package dev.aicli.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import dev.aicli.app.ui.design.GhostButton
import dev.aicli.app.ui.design.Glyph
import dev.aicli.app.ui.design.Glyphs
import dev.aicli.app.ui.design.Label
import dev.aicli.app.ui.design.Metrics
import dev.aicli.app.ui.design.OutlineButton
import dev.aicli.app.ui.design.Panel
import dev.aicli.app.ui.design.PrimaryButton
import dev.aicli.app.ui.design.Rule
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.Text

/**
 * The three "no content" bodies - empty, error, and the expandable technical disclosure they
 * both use. They share one silhouette on purpose: a large faint glyph, a short heading, a line
 * of prose, then actions. Once you have seen one you can read the others at a glance, which is
 * the whole point of having a state vocabulary rather than bespoke screens.
 */
@Composable
fun EmptyState(
    glyph: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateBody(
        glyph = glyph,
        title = title,
        body = body,
        modifier = modifier,
        primaryLabel = actionLabel,
        onPrimary = onAction,
    )
}

@Composable
fun ErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    glyph: ImageVector = Glyphs.Alert,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Retry",
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    technicalDetails: String? = null,
) {
    StateBody(
        glyph = glyph,
        title = title,
        body = body,
        modifier = modifier,
        primaryLabel = if (onRetry != null) retryLabel else null,
        onPrimary = onRetry,
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary,
        details = technicalDetails,
    )
}

@Composable
private fun StateBody(
    glyph: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    details: String? = null,
) {
    // Centred in whatever space the caller gives it. An empty state pinned to the top of a tall
    // phone screen reads as a rendering failure rather than as a deliberate message.
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Metrics.gutter, vertical = Space.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconTile(glyph, size = 80.dp)
            Text(
                title,
                style = SkullTheme.type.title,
                color = SkullTheme.colors.ink,
                align = TextAlign.Center,
                modifier = Modifier.padding(top = Space.x5),
            )
            Text(
                body,
                style = SkullTheme.type.body,
                color = SkullTheme.colors.inkMuted,
                align = TextAlign.Center,
                modifier = Modifier.padding(top = Space.x2),
            )
            if (primaryLabel != null && onPrimary != null) {
                Row(
                    modifier = Modifier.padding(top = Space.x6),
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    PrimaryButton(primaryLabel, onPrimary)
                    if (secondaryLabel != null && onSecondary != null) {
                        OutlineButton(secondaryLabel, onSecondary)
                    }
                }
            }
            if (details != null) {
                ExpandableDetails(
                    label = "Technical details",
                    content = details,
                    modifier = Modifier.padding(top = Space.x6).fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Collapsed-by-default disclosure for raw machine text - logs, stack traces, CLI output. The
 * content is set in mono inside a hairline panel and scrolls horizontally rather than wrapping,
 * because a wrapped stack trace is harder to read than a scrolled one.
 */
@Composable
fun ExpandableDetails(
    label: String,
    content: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(modifier = modifier) {
        GhostButton(
            label = label,
            onClick = { expanded = !expanded },
            glyph = if (expanded) Glyphs.ChevronUp else Glyphs.ChevronDown,
        )
        AnimatedVisibility(visible = expanded) {
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    content,
                    style = SkullTheme.type.monoSm,
                    color = SkullTheme.colors.inkMuted,
                    softWrap = false,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(Space.x3),
                )
            }
        }
    }
}

/** A key/value line: tracked label on the left, machine value in mono on the right. */
@Composable
fun MetaRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label, color = SkullTheme.colors.inkFaint)
        Rule(Modifier.padding(horizontal = Space.x3).weight(1f))
        Text(value, style = SkullTheme.type.monoSm, color = SkullTheme.colors.inkMuted, maxLines = 1)
    }
}
