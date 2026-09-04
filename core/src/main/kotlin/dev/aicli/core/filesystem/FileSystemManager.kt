package dev.aicli.core.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
)

sealed class FileOpResult<out T> {
    data class Success<T>(val value: T) : FileOpResult<T>()
    data class Failure(val message: String, val cause: Throwable? = null) : FileOpResult<Nothing>()
}

/**
 * Safe filesystem operations scoped to a single [WorkspaceRoot]. Every method resolves its
 * relative-path argument through [SafePath] first — there is no method here that accepts an
 * absolute path or a `..`-escaping relative path and just does it anyway.
 */
class FileSystemManager(private val root: WorkspaceRoot) {

    private fun resolve(relativePath: String): File = SafePath.resolve(root.rootDirectory, relativePath)

    suspend fun list(relativePath: String = ""): FileOpResult<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = resolve(relativePath)
            if (!dir.isDirectory) error("Not a directory: $relativePath")
            dir.listFiles()?.map {
                FileEntry(it.name, it.isDirectory, if (it.isFile) it.length() else 0L, it.lastModified())
            }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        }.fold(
            onSuccess = { FileOpResult.Success(it) },
            onFailure = { FileOpResult.Failure(it.message ?: "list failed", it) },
        )
    }

    suspend fun readText(relativePath: String): FileOpResult<String> = withContext(Dispatchers.IO) {
        runCatching { resolve(relativePath).readText() }.fold(
            onSuccess = { FileOpResult.Success(it) },
            onFailure = { FileOpResult.Failure(it.message ?: "read failed", it) },
        )
    }

    suspend fun writeText(relativePath: String, content: String): FileOpResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val f = resolve(relativePath)
            f.parentFile?.mkdirs()
            f.writeText(content)
        }.fold(
            onSuccess = { FileOpResult.Success(Unit) },
            onFailure = { FileOpResult.Failure(it.message ?: "write failed", it) },
        )
    }

    suspend fun createDirectory(relativePath: String): FileOpResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!resolve(relativePath).mkdirs()) error("mkdirs returned false for $relativePath")
        }.fold(
            onSuccess = { FileOpResult.Success(Unit) },
            onFailure = { FileOpResult.Failure(it.message ?: "mkdir failed", it) },
        )
    }

    suspend fun delete(relativePath: String): FileOpResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = SafePath.entry(root.rootDirectory, relativePath)
            if (target == root.rootDirectory.canonicalFile) error("refusing to delete the workspace root itself")
            if (!SafeFiles.deleteTree(target)) error("delete failed for $relativePath")
        }.fold(
            onSuccess = { FileOpResult.Success(Unit) },
            onFailure = { FileOpResult.Failure(it.message ?: "delete failed", it) },
        )
    }

    suspend fun rename(fromRelativePath: String, toRelativePath: String): FileOpResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val from = SafePath.entry(root.rootDirectory, fromRelativePath)
            val to = SafePath.entry(root.rootDirectory, toRelativePath)
            check(from != root.rootDirectory.canonicalFile && to != root.rootDirectory.canonicalFile) { "Cannot rename the workspace root" }
            check(!java.nio.file.Files.exists(to.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) { "Destination already exists: $toRelativePath" }
            to.parentFile?.mkdirs()
            if (!from.renameTo(to)) error("rename failed: $fromRelativePath -> $toRelativePath")
        }.fold(
            onSuccess = { FileOpResult.Success(Unit) },
            onFailure = { FileOpResult.Failure(it.message ?: "rename failed", it) },
        )
    }

    suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolve(relativePath).exists() }.getOrDefault(false)
    }

    suspend fun metadata(relativePath: String): FileOpResult<FileEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val f = resolve(relativePath)
            if (!f.exists()) error("does not exist: $relativePath")
            FileEntry(f.name, f.isDirectory, if (f.isFile) f.length() else 0L, f.lastModified())
        }.fold(
            onSuccess = { FileOpResult.Success(it) },
            onFailure = { FileOpResult.Failure(it.message ?: "metadata failed", it) },
        )
    }

    /** Absolute POSIX path safe to hand to a spawned process's argv/cwd, or null if [relativePath] escapes the root. */
    fun absolutePathOrNull(relativePath: String): String? = try {
        resolve(relativePath).path
    } catch (e: PathTraversalException) {
        null
    }
}
