package dev.aicli.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aicli.app.ui.design.Glyph
import dev.aicli.app.ui.design.Glyphs
import dev.aicli.app.ui.design.IconAction
import dev.aicli.app.ui.design.Label
import dev.aicli.app.ui.design.Metrics
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.Text
import dev.aicli.app.ui.design.pressable
import dev.aicli.core.filesystem.Project
import dev.aicli.core.filesystem.WorkspaceRoot

/**
 * Lists in this app are hairline-separated rows, not stacks of cards. Cards would need borders
 * of their own, and a column of bordered boxes separated by gaps reads as clutter at phone
 * width; a single rule between full-bleed rows is quieter and far easier to scan. Panels are
 * reserved for objects that carry their own actions, like a provider.
 */
@Composable
fun ProjectRow(
    project: Project,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val external = project.root !is WorkspaceRoot.AppWorkspace
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(start = Metrics.gutter, end = Space.x2, top = Space.x4, bottom = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(
            if (external) Glyphs.FolderExternal else Glyphs.Folder,
            null,
            size = Metrics.glyphMd,
            tint = SkullTheme.colors.inkMuted,
        )
        Column(Modifier.weight(1f).padding(start = Space.x4)) {
            Text(
                project.name,
                style = SkullTheme.type.heading,
                color = SkullTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Label(
                if (external) "External folder" else "App workspace",
                color = SkullTheme.colors.inkFaint,
                modifier = Modifier.padding(top = Space.x1),
            )
        }
        if (onDelete != null) {
            IconAction(
                icon = Glyphs.Trash,
                contentDescription = "Remove " + project.name,
                onClick = onDelete,
                size = Metrics.glyphSm,
                tint = SkullTheme.colors.inkFaint,
            )
        } else {
            Glyph(Glyphs.ArrowRight, null, size = Metrics.glyphSm, tint = SkullTheme.colors.inkFaint)
        }
    }
}

/**
 * One preference row: title, optional description, and a trailing control supplied by the
 * caller (a [dev.aicli.app.ui.design.Toggle], a value, a chevron).
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.pressable(onClick = onClick) else it }
            .padding(horizontal = Metrics.gutter, vertical = Space.x4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SkullTheme.type.body, color = SkullTheme.colors.ink)
            if (description != null) {
                Text(
                    description,
                    style = SkullTheme.type.bodySm,
                    color = SkullTheme.colors.inkMuted,
                    modifier = Modifier.padding(top = Space.x1),
                )
            }
        }
        Box(Modifier.padding(start = Space.x4), contentAlignment = Alignment.CenterEnd) {
            when {
                trailing != null -> trailing()
                value != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value, style = SkullTheme.type.mono, color = SkullTheme.colors.inkMuted)
                    if (onClick != null) {
                        Glyph(
                            Glyphs.ChevronDown,
                            null,
                            size = Metrics.glyphSm,
                            tint = SkullTheme.colors.inkFaint,
                            modifier = Modifier.padding(start = Space.x2),
                        )
                    }
                }
                onClick != null -> Glyph(
                    Glyphs.ArrowRight,
                    null,
                    size = Metrics.glyphSm,
                    tint = SkullTheme.colors.inkFaint,
                )
            }
        }
    }
}

/**
 * The bordered disc that stands in for a provider logo. Deliberately generic: shipping four
 * different vendor marks would drag colour and a foreign visual language back into a design
 * whose entire premise is that it has neither.
 */
@Composable
fun GlyphTile(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(Metrics.hairline, SkullTheme.colors.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(Glyphs.Terminal, null, size = Metrics.glyphMd, tint = SkullTheme.colors.ink)
    }
}
