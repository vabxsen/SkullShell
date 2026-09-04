package dev.aicli.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.aicli.app.ui.common.InstallProgressUi
import dev.aicli.app.ui.design.GhostButton
import dev.aicli.app.ui.design.Glyph
import dev.aicli.app.ui.design.Glyphs
import dev.aicli.app.ui.design.Label
import dev.aicli.app.ui.design.LinearProgress
import dev.aicli.app.ui.design.Metrics
import dev.aicli.app.ui.design.PrimaryButton
import dev.aicli.app.ui.design.Rule
import dev.aicli.app.ui.design.Sheet
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.Text
import dev.aicli.provider.api.InstallEvent

/**
 * The single install/update/repair/uninstall progress surface - every Providers- and Settings-
 * screen action that starts one shows this, so there is one install experience rather than
 * several.
 *
 * The sheet cannot be dismissed by tapping away while work is in flight ([InstallProgressUi.done]
 * is false): a half-finished userland install is not something to lose track of behind a screen.
 */
@Composable
fun InstallProgressSheet(
    progress: InstallProgressUi,
    onDismiss: () -> Unit,
    onOpenProvider: (() -> Unit)? = null,
) {
    val event = progress.latestEvent
    val colors = SkullTheme.colors
    Sheet(
        onDismiss = { if (progress.done) onDismiss() },
        dismissOnScrimTap = progress.done,
    ) {
        Label(
            if (progress.done) progress.displayName else "Installing " + progress.displayName,
            color = colors.inkMuted,
        )
        Rule(Modifier.padding(top = Space.x3, bottom = Space.x5))

        when (event) {
            is InstallEvent.Progress -> {
                Text(event.step, style = SkullTheme.type.body, color = colors.ink)
                LinearProgress(
                    fraction = event.fraction,
                    modifier = Modifier.padding(top = Space.x4),
                )
                event.logLine?.let {
                    ExpandableDetails(
                        label = "Technical details",
                        content = it,
                        modifier = Modifier.padding(top = Space.x4).fillMaxWidth(),
                    )
                }
            }
            is InstallEvent.Completed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.CheckCircle, null, size = Metrics.glyphMd, tint = colors.ink)
                Text(
                    progress.displayName + " is ready",
                    style = SkullTheme.type.body,
                    color = colors.ink,
                    modifier = Modifier.padding(start = Space.x3),
                )
            }
            is InstallEvent.Failed -> Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Glyphs.ErrorCircle, null, size = Metrics.glyphMd, tint = colors.ink)
                    Text(
                        "Failed at '" + event.step + "'",
                        style = SkullTheme.type.body,
                        color = colors.ink,
                        modifier = Modifier.padding(start = Space.x3),
                    )
                }
                ExpandableDetails(
                    label = "Technical details",
                    content = event.reason,
                    initiallyExpanded = true,
                    modifier = Modifier.padding(top = Space.x4).fillMaxWidth(),
                )
            }
            null -> LinearProgress()
        }

        if (progress.done) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.x6),
                horizontalArrangement = Arrangement.End,
            ) {
                if (event is InstallEvent.Completed && onOpenProvider != null) {
                    GhostButton("Close", onDismiss)
                    PrimaryButton("Open " + progress.displayName, { onOpenProvider(); onDismiss() })
                } else {
                    PrimaryButton("Close", onDismiss)
                }
            }
        }
    }
}
