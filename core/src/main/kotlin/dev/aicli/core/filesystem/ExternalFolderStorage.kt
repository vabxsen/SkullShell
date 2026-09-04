package dev.aicli.core.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.nio.file.Files

/** SAF folders are copied to private storage for CLI access. Export is explicit and conflict checked. */
class ExternalFolderStorage(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun importFolder(id: String, uri: Uri, destination: File): String = withContext(Dispatchers.IO) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val root = root(uri)
        check(root.canRead() && root.canWrite()) { "The selected folder must allow reading and writing" }
        check(destination.isDirectory || destination.mkdirs()) { "Could not create project directory" }
        val hashes = linkedMapOf<String, String>()
        fun copy(directory: DocumentFile, relative: String) {
            for (child in directory.listFiles()) {
                val name = child.name ?: error("A document has no file name")
                require(name != "." && name != ".." && '/' !in name && '\\' !in name) { "Invalid document name" }
                val path = if (relative.isEmpty()) name else "$relative/$name"
                val target = SafePath.resolve(destination, path)
                if (child.isDirectory) {
                    check(target.isDirectory || target.mkdirs())
                    copy(child, path)
                } else if (child.isFile) {
                    resolver.openInputStream(child.uri).use { input ->
                        checkNotNull(input) { "Could not read $path" }
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    hashes[path] = target.inputStream().use(::hash)
                }
            }
        }
        copy(root, "")
        baseline(id).apply { parentFile?.mkdirs() }.writeText(Json.encodeToString(hashes))
        root.name ?: "External project"
    }

    suspend fun exportChanges(project: WorkspaceRoot.ExternalProject): Int = withContext(Dispatchers.IO) {
        val root = root(project.treeUri)
        check(root.canWrite()) { "Folder access was revoked. Open the folder again to grant access." }
        val baselineFile = baseline(project.id)
        check(baselineFile.exists()) { "This folder has not been imported. Open it again from Projects." }
        val before = Json.decodeFromString<Map<String, String>>(baselineFile.readText())
        val remote = linkedMapOf<String, DocumentFile>()
        fun scan(directory: DocumentFile, parent: String) {
            for (child in directory.listFiles()) {
                val name = child.name ?: error("A document has no file name")
                require(name != "." && name != ".." && '/' !in name && '\\' !in name)
                val path = if (parent.isEmpty()) name else "$parent/$name"
                if (child.isDirectory) scan(child, path) else if (child.isFile) remote[path] = child
            }
        }
        scan(root, "")
        val remoteHashes = remote.mapValues { (path, file) ->
            resolver.openInputStream(file.uri).use { hash(checkNotNull(it) { "Could not read $path" }) }
        }
        val local = linkedMapOf<String, File>()
        val localDirectories = mutableListOf<String>()
        Files.walk(project.stagingDirectory.toPath()).use { paths ->
            paths.forEach { path ->
                check(!Files.isSymbolicLink(path)) { "Export does not follow symbolic links: ${path.fileName}" }
                if (Files.isRegularFile(path)) {
                    local[project.stagingDirectory.toPath().relativize(path).toString().replace(File.separatorChar, '/')] = path.toFile()
                } else if (Files.isDirectory(path) && path != project.stagingDirectory.toPath()) {
                    localDirectories += project.stagingDirectory.toPath().relativize(path).toString().replace(File.separatorChar, '/')
                }
            }
        }
        val localHashes = local.mapValues { it.value.inputStream().use(::hash) }
        val changed = (before.keys + local.keys).filter { before[it] != localHashes[it] }
        val conflicts = changed.filter { remoteHashes[it] != before[it] && remoteHashes[it] != localHashes[it] }
        check(conflicts.isEmpty()) { "Files changed outside SkullShell: ${conflicts.take(5).joinToString()}. No files were saved." }

        fun parentFor(path: String): DocumentFile {
            var parent = root
            for (part in path.split('/').dropLast(1)) {
                parent = parent.findFile(part)?.also { check(it.isDirectory) { "$part is not a directory" } }
                    ?: checkNotNull(parent.createDirectory(part)) { "Could not create $part" }
            }
            return parent
        }
        // Include empty folders; creating them must not overwrite an external file of the same name.
        for (directory in localDirectories) check(remote[directory] == null) { "A file outside SkullShell conflicts with folder $directory. No files were saved." }
        for (directory in localDirectories) parentFor("$directory/.directory-placeholder")
        for (path in changed) {
            val file = local[path]
            if (file == null) {
                remote[path]?.let { check(it.delete()) { "Could not delete $path" } }
            } else if (localHashes[path] != remoteHashes[path]) {
                val target = remote[path] ?: checkNotNull(parentFor(path).createFile("application/octet-stream", path.substringAfterLast('/'))) { "Could not create $path" }
                resolver.openOutputStream(target.uri, "wt").use { output ->
                    checkNotNull(output) { "Could not save $path" }
                    file.inputStream().use { it.copyTo(output) }
                }
            }
        }
        baselineFile.writeText(Json.encodeToString(localHashes))
        changed.size
    }

    private fun root(uri: Uri) = checkNotNull(DocumentFile.fromTreeUri(context, uri)) { "Folder is unavailable" }
    private fun baseline(id: String) = File(context.filesDir, "project-sync/$id.json")
    private fun hash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(bytes)
            if (count < 0) break
            digest.update(bytes, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
