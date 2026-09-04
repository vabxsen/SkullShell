package dev.aicli.app.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.common.UiState
import dev.aicli.app.ui.components.EmptyState
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.components.ProjectRow
import dev.aicli.app.ui.design.GhostButton
import dev.aicli.app.ui.design.Glyphs
import dev.aicli.app.ui.design.IconAction
import dev.aicli.app.ui.design.InputField
import dev.aicli.app.ui.design.LoadingBody
import dev.aicli.app.ui.design.Metrics
import dev.aicli.app.ui.design.Modal
import dev.aicli.app.ui.design.PageTitle
import dev.aicli.app.ui.design.PrimaryButton
import dev.aicli.app.ui.design.Rule
import dev.aicli.app.ui.design.Screen
import dev.aicli.app.ui.design.SectionHeader
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.app.ui.design.Text
import dev.aicli.app.ui.design.TopBar

/**
 * Wide screens get a centred measure rather than a grid of tiles. Hairline-separated rows are
 * already an efficient way to show a list; stretching them to a 10-inch width would just make
 * them hard to track across, and turning them into cards to fill the space would contradict the
 * row/panel rule the rest of the app follows.
 */
private val MAX_MEASURE = 720.dp

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (dev.aicli.core.filesystem.Project) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.registerExternalProject(uri.lastPathSegment ?: "External project", uri)
        }
    }

    Screen(
        topBar = {
            TopBar(
                crumb = "SkullShell / Projects",
                actions = {
                    IconAction(
                        icon = Glyphs.Plus,
                        contentDescription = "New project",
                        onClick = { showCreateDialog = true },
                        tint = SkullTheme.colors.ink,
                    )
                },
            )
        },
    ) {
        Box(Modifier.widthIn(max = MAX_MEASURE).fillMaxSize().align(Alignment.TopCenter)) {
            when (val s = state) {
                is UiState.Loading -> LoadingBody(Modifier.fillMaxSize(), label = "Loading projects")
                is UiState.Offline -> ErrorState(
                    title = "Offline",
                    body = "Projects on this device are still available. Opening an external folder needs storage access, not a network.",
                    glyph = Glyphs.NoSignal,
                )
                is UiState.Error -> ErrorState(title = "Could not load projects", body = s.message)
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        glyph = Glyphs.Folder,
                        title = "No projects yet",
                        body = "Create a workspace inside the app, or open a folder that already exists on this device.",
                        actionLabel = "New project",
                        onAction = { showCreateDialog = true },
                    )
                } else {
                    ProjectsList(s.data, onOpenProject, viewModel::removeProject)
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreateAppWorkspace = { name -> viewModel.createAppWorkspace(name); showCreateDialog = false },
            onOpenExternal = { showCreateDialog = false; pickFolder.launch(null) },
        )
    }
}

/** [dev.aicli.core.db.ProjectDao.observeAll] orders by last-opened desc, so the first project
 *  in [projects] is always "current" - no separate query needed. */
@Composable
private fun ProjectsList(
    projects: List<dev.aicli.core.filesystem.Project>,
    onOpenProject: (dev.aicli.core.filesystem.Project) -> Unit,
    onDelete: (String) -> Unit,
) {
    val current = projects.first()
    val recent = projects.drop(1)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.x10),
    ) {
        item {
            PageTitle(
                title = "Projects",
                subtitle = "Workspaces the terminal can open into.",
                modifier = Modifier.padding(horizontal = Metrics.gutter, vertical = Space.x6),
            )
        }
        item {
            SectionHeader("Current", Modifier.padding(horizontal = Metrics.gutter, vertical = Space.x4))
        }
        item {
            ProjectRow(current, onClick = { onOpenProject(current) }, onDelete = { onDelete(current.id) })
            Rule()
        }
        if (recent.isNotEmpty()) {
            item {
                SectionHeader("Recent", Modifier.padding(horizontal = Metrics.gutter, vertical = Space.x4))
            }
            items(recent, key = { it.id }) { project ->
                ProjectRow(project, onClick = { onOpenProject(project) }, onDelete = { onDelete(project.id) })
                Rule()
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreateAppWorkspace: (String) -> Unit,
    onOpenExternal: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    Modal(
        title = "New project",
        onDismiss = onDismiss,
        actions = {
            GhostButton("Cancel", onDismiss)
            PrimaryButton(
                label = "Create",
                onClick = { if (name.isNotBlank()) onCreateAppWorkspace(name) },
                enabled = name.isNotBlank(),
            )
        },
    ) {
        InputField(
            value = name,
            onValueChange = { name = it },
            label = "Workspace name",
            placeholder = "my-project",
        )
        Text(
            "Creates a workspace inside the app's own storage.",
            style = SkullTheme.type.bodySm,
            color = SkullTheme.colors.inkMuted,
            modifier = Modifier.padding(top = Space.x3),
        )
        Rule(Modifier.padding(vertical = Space.x5))
        GhostButton(
            label = "Open an existing folder",
            onClick = onOpenExternal,
            glyph = Glyphs.FolderExternal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
