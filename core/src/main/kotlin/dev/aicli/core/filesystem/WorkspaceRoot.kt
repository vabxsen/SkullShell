package dev.aicli.core.filesystem

import android.net.Uri
import java.io.File

/**
 * The two distinct kinds of project root this app supports, kept as distinct types (rather than
 * both being "a File" or "a String path") specifically so the UI and the process layer can never
 * silently blur the two: an [External] project only ever offers a real POSIX path once its
 * SAF-selected content has been staged (see filesystem manager); before that, only its
 * display name and the [Uri] itself are usable, and no spawned process ever receives a
 * `content://` URI as an argv/cwd value.
 */
sealed class WorkspaceRoot {
    abstract val id: String
    abstract val displayName: String

    /** Fully app-managed, app-private storage. No SAF, no user permission prompt needed. */
    data class AppWorkspace(
        override val id: String,
        override val displayName: String,
        val directory: File,
    ) : WorkspaceRoot()

    /**
     * A folder the user picked via `ACTION_OPEN_DOCUMENT_TREE`. [stagingDirectory] is where its
     * contents are mirrored to for CLI access (CLIs need real paths, not `content://`); changes
     * are synced back to [treeUri] explicitly by the filesystem manager, never implicitly.
     */
    data class ExternalProject(
        override val id: String,
        override val displayName: String,
        val treeUri: Uri,
        val stagingDirectory: File,
    ) : WorkspaceRoot()

    val rootDirectory: File
        get() = when (this) {
            is AppWorkspace -> directory
            is ExternalProject -> stagingDirectory
        }
}

/** A project the user has created or opened — persisted via `core.db.ProjectEntity`. */
data class Project(
    val id: String,
    val name: String,
    val root: WorkspaceRoot,
    val createdAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long?,
)
