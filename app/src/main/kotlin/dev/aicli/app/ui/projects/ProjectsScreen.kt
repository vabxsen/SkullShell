package dev.aicli.app.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aicli.app.ui.common.UiState
import dev.aicli.core.filesystem.Project
import dev.aicli.core.filesystem.WorkspaceRoot

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (Project) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
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
            is UiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Offline, is UiState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Couldn't load projects", style = MaterialTheme.typography.bodyMedium)
            }
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    EmptyProjectsBody(padding, onCreate = { showCreateDialog = true }, onOpenExternal = { pickFolder.launch(null) })
                } else {
                    ProjectList(s.data, padding, onOpenProject, onDelete = viewModel::removeProject)
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

@Composable
private fun EmptyProjectsBody(padding: PaddingValues, onCreate: () -> Unit, onOpenExternal: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.padding(bottom = 12.dp))
        Text("No projects yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Create an app-managed workspace, or open a folder from your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProjectList(
    projects: List<Project>,
    padding: PaddingValues,
    onOpenProject: (Project) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(projects, key = { it.id }) { project ->
            Card(onClick = { onOpenProject(project) }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (project.root is WorkspaceRoot.AppWorkspace) Icons.Filled.Folder else Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Column {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (project.root is WorkspaceRoot.AppWorkspace) "App workspace" else "External project",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onDelete(project.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
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
                    modifier = Modifier.padding(top = 12.dp),
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
