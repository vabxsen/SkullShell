package dev.aicli.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aicli.app.ui.design.*
import dev.aicli.core.filesystem.Project
import dev.aicli.core.filesystem.WorkspaceRoot

@Composable
fun ProjectRow(project: Project, onClick: () -> Unit, modifier: Modifier = Modifier, onDelete: (() -> Unit)? = null,
               onSaveToFolder: (() -> Unit)? = null) {
    val external = project.root !is WorkspaceRoot.AppWorkspace
    ListItem(modifier = modifier.pressable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(if (external) "External folder" else "On this device", style = SkullTheme.type.bodySm, color = SkullTheme.colors.inkMuted) },
        leadingContent = { IconTile(if (external) Glyphs.FolderExternal else Glyphs.Folder) },
        trailingContent = {
            if (onDelete != null) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconAction(Glyphs.Dots, "Actions for ${project.name}", { open = true })
                    Menu(open, { open = false }, buildList {
                        add(MenuItem("Open terminal", onClick))
                        onSaveToFolder?.let { add(MenuItem("Save changes to folder", it)) }
                        add(MenuItem("Remove project", onDelete))
                    })
                }
            } else Glyph(Glyphs.ArrowRight, null, tint = SkullTheme.colors.inkMuted)
        })
}

@Composable
fun SettingsRow(title: String, modifier: Modifier = Modifier, description: String? = null, value: String? = null,
                onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, icon: ImageVector? = null) {
    ListItem(modifier = modifier.let { if (onClick != null) it.pressable(onClick = onClick) else it },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it, style = SkullTheme.type.bodySm, color = SkullTheme.colors.inkMuted) } },
        leadingContent = icon?.let { { Glyph(it, null, tint = SkullTheme.colors.inkMuted) } },
        trailingContent = {
            Box(Modifier.widthIn(max = 130.dp), contentAlignment = Alignment.CenterEnd) {
                when {
                    trailing != null -> trailing()
                    value != null -> Text(value, style = SkullTheme.type.bodySm, color = SkullTheme.colors.inkMuted, maxLines = 1)
                    onClick != null -> Glyph(Glyphs.ArrowRight, null, size = 20.dp, tint = SkullTheme.colors.inkMuted)
                }
            }
        })
}

@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 48.dp,
             color: Color = MaterialTheme.colorScheme.secondaryContainer, tint: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(modifier.size(size), shape = MaterialTheme.shapes.large, color = color, contentColor = tint) {
        Box(contentAlignment = Alignment.Center) { Glyph(icon, null, tint = tint) }
    }
}
@Composable
fun GlyphTile(modifier: Modifier = Modifier, size: Dp = 48.dp) { IconTile(Glyphs.Terminal, modifier, size) }
@Composable
fun ProviderMark(id: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val icon = when (id) { "claude_code" -> Glyphs.Spark; "codex_cli" -> Glyphs.Terminal; "opencode" -> Glyphs.Code; else -> Glyphs.Globe }
    val container = when (id) { "opencode", "antigravity_cli" -> scheme.tertiaryContainer; else -> scheme.secondaryContainer }
    val ink = when (id) { "opencode", "antigravity_cli" -> scheme.onTertiaryContainer; else -> scheme.onSecondaryContainer }
    IconTile(icon, modifier, color = container, tint = ink)
}
