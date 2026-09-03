package dev.aicli.app.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aicli.app.ui.common.UiState
import dev.aicli.app.ui.components.EmptyState
import dev.aicli.app.ui.components.ErrorState
import dev.aicli.app.ui.components.LoadingState
import dev.aicli.app.ui.components.ProjectItem
import dev.aicli.app.ui.components.SectionHeader
import dev.aicli.app.ui.theme.Dimens
import dev.aicli.core.filesystem.Project

private val WIDE_BREAKPOINT = 600.dp

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (Project) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.registerExternalProject(uri.lastPathSegment ?: "External project", uri)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Projects") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showCreateDialog = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New") })
        },
    ) { padding ->
        when (val s = state) {
            is UiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            is UiState.Offline -> ErrorState(
                title = "You're offline",
                body = "Projects stored on this device are still available; opening external folders needs storage access, not network.",
                icon = Icons.Filled.WifiOff,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Error -> ErrorState(
                title = "Couldn't load projects",
                body = s.message,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Folder,
                        title = "No projects yet",
                        body = "Create or import your first coding project.",
                        actionLabel = "Create project",
                        onAction = { showCreateDialog = true },
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                } else {
                    ProjectsContent(s.data, padding, onOpenProject, onDelete = viewModel::removeProject)
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
 *  in [projects] is always "current" — no separate query needed. */
@Composable
private fun ProjectsContent(
    projects: List<Project>,
    padding: PaddingValues,
    onOpenProject: (Project) -> Unit,
    onDelete: (String) -> Unit,
) {
    val current = projects.first()
    val recent = projects.drop(1)

    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        if (maxWidth < WIDE_BREAKPOINT) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Dimens.space16),
                verticalArrangement = Arrangement.spacedBy(Dimens.space12),
            ) {
                item { SectionHeader("Current") }
                item { ProjectItem(current, onClick = { onOpenProject(current) }, onDelete = { onDelete(current.id) }) }
                if (recent.isNotEmpty()) {
                    item { SectionHeader("Recent", modifier = Modifier.padding(top = Dimens.space8)) }
                    items(recent, key = { it.id }) { project ->
                        ProjectItem(project, onClick = { onOpenProject(project) }, onDelete = { onDelete(project.id) })
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(Dimens.space16), verticalArrangement = Arrangement.spacedBy(Dimens.space12)) {
                SectionHeader("Current")
                ProjectItem(current, onClick = { onOpenProject(current) }, onDelete = { onDelete(current.id) })
                if (recent.isNotEmpty()) {
                    SectionHeader("Recent", modifier = Modifier.padding(top = Dimens.space8))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        modifier = Modifier.fillMaxWidth().fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space12),
                        verticalArrangement = Arrangement.spacedBy(Dimens.space12),
                    ) {
                        items(recent, key = { it.id }) { project ->
                            ProjectItem(project, onClick = { onOpenProject(project) }, onDelete = { onDelete(project.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreateAppWorkspace: (String) -> Unit, onOpenExternal: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Or open an existing folder from your device instead of creating an app workspace.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimens.space12),
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onCreateAppWorkspace(name) }, enabled = name.isNotBlank()) { Text("Create workspace") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenExternal) { Text("Open folder…") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
