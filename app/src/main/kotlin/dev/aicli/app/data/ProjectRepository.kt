package dev.aicli.app.data

import android.content.Context
import dev.aicli.core.db.AppDatabase
import dev.aicli.core.db.ProjectEntity
import dev.aicli.core.filesystem.Project
import dev.aicli.core.filesystem.WorkspaceRoot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import dev.aicli.core.filesystem.ExternalFolderStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bridges `core.db`'s persisted rows and `core.filesystem`'s domain model. */
class ProjectRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).projectDao()

    val projects: Flow<List<Project>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun createAppWorkspace(name: String): Project = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Project name cannot be empty" }
        val id = UUID.randomUUID().toString()
        val dir = File(context.filesDir, "workspaces/$id").apply { check(mkdirs()) { "Could not create project directory" } }
        val project = Project(
            id = id,
            name = name,
            root = WorkspaceRoot.AppWorkspace(id = id, displayName = name, directory = dir),
            createdAtEpochMillis = System.currentTimeMillis(),
            lastOpenedAtEpochMillis = null,
        )
        dao.upsert(project.toEntity())
        project
    }

    suspend fun registerExternalProject(name: String, treeUri: android.net.Uri): Project = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val staging = File(context.filesDir, "staging/$id").apply { mkdirs() }
        val displayName = ExternalFolderStorage(context).importFolder(id, treeUri, staging)
        val project = Project(
            id = id,
            name = displayName,
            root = WorkspaceRoot.ExternalProject(id = id, displayName = displayName, treeUri = treeUri, stagingDirectory = staging),
            createdAtEpochMillis = System.currentTimeMillis(),
            lastOpenedAtEpochMillis = null,
        )
        dao.upsert(project.toEntity())
        project
    }

    suspend fun saveToFolder(id: String): Int {
        val project = checkNotNull(get(id)) { "Project not found" }
        return ExternalFolderStorage(context).exportChanges(project.root as WorkspaceRoot.ExternalProject)
    }

    suspend fun markOpened(projectId: String) {
        val row = dao.get(projectId) ?: return
        dao.upsert(row.copy(lastOpenedAtEpochMillis = System.currentTimeMillis()))
    }

    suspend fun remove(projectId: String) = dao.delete(projectId)

    suspend fun get(projectId: String): Project? = dao.get(projectId)?.toDomain()

    private fun Project.toEntity() = ProjectEntity(
        id = id,
        name = name,
        rootKind = if (root is WorkspaceRoot.AppWorkspace) "app_workspace" else "external",
        rootLocator = when (val r = root) {
            is WorkspaceRoot.AppWorkspace -> r.directory.absolutePath
            is WorkspaceRoot.ExternalProject -> r.treeUri.toString()
        },
        createdAtEpochMillis = createdAtEpochMillis,
        lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
    )

    private fun ProjectEntity.toDomain() = Project(
        id = id,
        name = name,
        root = if (rootKind == "app_workspace") {
            WorkspaceRoot.AppWorkspace(id = id, displayName = name, directory = File(rootLocator))
        } else {
            WorkspaceRoot.ExternalProject(
                id = id,
                displayName = name,
                treeUri = android.net.Uri.parse(rootLocator),
                stagingDirectory = File(context.filesDir, "staging/$id"),
            )
        },
        createdAtEpochMillis = createdAtEpochMillis,
        lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
    )
}
