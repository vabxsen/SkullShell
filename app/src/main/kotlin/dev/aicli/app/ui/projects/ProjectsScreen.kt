package dev.aicli.app.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.common.UiState
import dev.aicli.app.ui.components.EmptyState
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.components.ProjectRow
import dev.aicli.app.ui.design.*
import dev.aicli.core.filesystem.Project
import dev.aicli.core.filesystem.WorkspaceRoot

@Composable
fun ProjectsScreen(viewModel: ProjectsViewModel, onOpenProject: (Project) -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableIntStateOf(0) }
    var removeProject by remember { mutableStateOf<Project?>(null) }
    var saveProject by remember { mutableStateOf<Project?>(null) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.registerExternalProject(uri.lastPathSegment ?: "External project", uri)
    }
    val projects = (state as? UiState.Success)?.data.orEmpty()
    val filtered = projects.filter { project -> project.name.contains(query, ignoreCase = true) && when (location) {
        1 -> project.root is WorkspaceRoot.AppWorkspace
        2 -> project.root !is WorkspaceRoot.AppWorkspace
        else -> true
    } }
    Screen(topBar = { TopBar("Projects", onBack = onBack) }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showCreateDialog = true }, icon = { Icon(Glyphs.Plus, null) },
            text = { androidx.compose.material3.Text("New project") })
    }) {
        LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = Metrics.gutter, end = Metrics.gutter, top = Space.x2, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            item { SearchField(query, { query = it }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    listOf("All", "On device", "External").forEachIndexed { index, label ->
                        FilterChip(selected = location == index, onClick = { location = index }, label = { androidx.compose.material3.Text(label) },
                            leadingIcon = if (location == index) { { Icon(Glyphs.Check, null, Modifier.size(18.dp)) } } else null)
                    }
                }
            }
            item { SectionHeader(if (query.isBlank()) "Your projects" else "Search results", Modifier.padding(start = Space.x2),
                action = { GhostButton("Open folder", { pickFolder.launch(null) }, glyph = Glyphs.FolderExternal) }) }
            when (val current = state) {
                is UiState.Loading -> item { LoadingBody(Modifier.fillMaxWidth().height(200.dp), "Loading projects") }
                is UiState.Error -> item { ErrorState("Could not load projects", current.message) }
                is UiState.Offline -> item { ErrorState("Storage unavailable", "Check access to your project folders.") }
                is UiState.Success -> {
                    if (projects.isEmpty()) item { EmptyState(Glyphs.Folder, "Your projects live here", "Create a project or open a folder to start working.", Modifier.height(320.dp)) }
                    else if (filtered.isEmpty()) item {
                        EmptyState(Glyphs.Search, "No projects found", "Try another name or location.", Modifier.height(300.dp),
                            actionLabel = "Clear filters", onAction = { query = ""; location = 0 })
                    } else items(filtered, key = { it.id }) { project ->
                        Panel { ProjectRow(project, { onOpenProject(project) }, onDelete = { removeProject = project },
                            onSaveToFolder = if (project.root is WorkspaceRoot.ExternalProject) ({ saveProject = project }) else null) }
                    }
                }
            }
        }
    }
    if (busy) Modal("Working with folder", {}, actions = {}) {
        LoadingBody(Modifier.fillMaxWidth().height(100.dp), "Copying project files…")
    }
    event?.let { message ->
        Modal("Project", viewModel::clearEvent, actions = { GhostButton("Close", viewModel::clearEvent) }) { Text(message) }
    }
    saveProject?.let { project ->
        Modal("Save changes to folder?", { saveProject = null }, actions = {
            GhostButton("Cancel", { saveProject = null })
            GhostButton("Save", { viewModel.saveToFolder(project.id); saveProject = null })
        }) { Text("Save edits, new files and file deletions from ${project.name} to the original folder. Finish terminal commands before saving. Conflicting external changes will stop the save.") }
    }
    if (showCreateDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        Modal("New project", { showCreateDialog = false }, actions = {
            GhostButton("Cancel", { showCreateDialog = false })
            GhostButton("Create", { viewModel.createAppWorkspace(name.trim()); showCreateDialog = false }, enabled = name.isNotBlank())
        }) {
            InputField(name, { name = it }, label = "Project name", placeholder = "My project")
            Text("Saved on this device.", style = SkullTheme.type.bodySm, color = SkullTheme.colors.inkMuted, modifier = Modifier.padding(top = Space.x3))
        }
    }
    removeProject?.let { project ->
        Modal("Remove project?", { removeProject = null }, actions = {
            GhostButton("Cancel", { removeProject = null })
            GhostButton("Remove", { viewModel.removeProject(project.id); removeProject = null })
        }) { Text("Remove ${project.name} from your project list? Its files will stay on this device.", color = SkullTheme.colors.inkMuted) }
    }
}
