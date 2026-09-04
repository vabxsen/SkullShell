package dev.aicli.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.data.SessionRunState
import dev.aicli.app.ui.components.IconTile
import dev.aicli.app.ui.components.ProjectRow
import dev.aicli.app.ui.design.*
import dev.aicli.core.filesystem.Project

@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenTerminal: () -> Unit, onOpenProjects: () -> Unit,
               onOpenSettings: () -> Unit, onOpenProject: (Project) -> Unit, onOpenSession: (String) -> Unit,
               onOpenProviders: () -> Unit) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val runtimeInstalled by viewModel.runtimeInstalled.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    LifecycleResumeEffect(Unit) { viewModel.refreshRuntime(); onPauseOrDispose { } }
    Screen(topBar = { TopBar("SkullShell") }) {
        LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = Metrics.gutter, end = Metrics.gutter, top = Space.x2, bottom = Space.x6),
            verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            item { Text("Your workspace", style = SkullTheme.type.display, modifier = Modifier.padding(start = Space.x2, bottom = Space.x2)) }
            item {
                Panel(fill = scheme.primaryContainer) {
                    Column(Modifier.padding(Space.x6)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Glyphs.Terminal, color = scheme.primary, tint = scheme.onPrimary, size = 48.dp)
                            Text("Terminal", style = SkullTheme.type.title, color = scheme.onPrimaryContainer, modifier = Modifier.padding(start = Space.x4))
                        }
                        Text(if (runtimeInstalled == false) "Set up the Linux environment to run shells and coding agents." else "Start a shell or pick up an open session.",
                            color = scheme.onPrimaryContainer, style = SkullTheme.type.bodySm, modifier = Modifier.padding(top = Space.x4, bottom = Space.x4))
                        PrimaryButton(if (runtimeInstalled == false) "Set up terminal" else "New terminal",
                            if (runtimeInstalled == false) onOpenSettings else onOpenTerminal,
                            glyph = if (runtimeInstalled == false) Glyphs.Download else Glyphs.Plus)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    WorkspaceShortcut("Projects", "${projects.size} saved", Glyphs.Folder, onOpenProjects, Modifier.weight(1f),
                        scheme.secondaryContainer, scheme.onSecondaryContainer)
                    WorkspaceShortcut("Coding agents", "Install and manage", Glyphs.Grid, onOpenProviders, Modifier.weight(1f),
                        scheme.tertiaryContainer, scheme.onTertiaryContainer)
                }
            }
            item {
                SectionHeader("Recent projects", Modifier.padding(start = Space.x2), action = { GhostButton("View all", onOpenProjects) })
                Panel(Modifier.fillMaxWidth()) {
                    if (projects.isEmpty()) {
                        ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("No projects yet") },
                            supportingContent = { Text("Create a project or open a folder.", style = SkullTheme.type.bodySm) },
                            leadingContent = { IconTile(Glyphs.Folder) })
                    } else projects.take(3).forEachIndexed { index, project ->
                        ProjectRow(project, { onOpenProject(project) })
                        if (index < minOf(projects.size, 3) - 1) Rule(Modifier.padding(start = 80.dp, end = Space.x4))
                    }
                }
            }
            item { SectionHeader("Sessions", Modifier.padding(horizontal = Space.x2)) }
            if (sessions.isEmpty()) item {
                Panel(Modifier.fillMaxWidth()) {
                    ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("No active sessions") },
                        supportingContent = { Text("Your terminal sessions will appear here.", style = SkullTheme.type.bodySm) },
                        leadingContent = { IconTile(Glyphs.Clock) })
                }
            } else items(sessions, key = { it.id }) { session ->
                val state = viewModel.controllerFor(session.id)?.runState?.collectAsStateWithLifecycle()?.value
                Panel(onClick = { onOpenSession(session.id) }) {
                    ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(session.title) },
                        supportingContent = { Text(session.workingDirectory.substringAfter("/files/"), style = SkullTheme.type.monoSm, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { IconTile(Glyphs.Terminal) },
                        trailingContent = { StatusChip(if (state == SessionRunState.RUNNING) "Running" else "Ended",
                            if (state == SessionRunState.RUNNING) StatusTone.SUCCESS else StatusTone.NEUTRAL) })
                }
            }
        }
    }
}

@Composable
private fun WorkspaceShortcut(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit,
                              modifier: Modifier, color: Color, tint: Color) {
    Panel(modifier, fill = color, onClick = onClick) {
        Column(Modifier.padding(Space.x4)) {
            Glyph(icon, null, tint = tint, modifier = Modifier.padding(bottom = Space.x3))
            Text(title, style = SkullTheme.type.heading, color = tint)
            Text(subtitle, style = SkullTheme.type.bodySm, color = tint, modifier = Modifier.padding(top = Space.x1))
        }
    }
}
