package dev.aicli.runtime.archive

import java.io.File
import java.io.IOException

object ArchivePaths {
    fun link(root: File, name: String): File {
        if (name.isBlank() || name.startsWith('/') || name.contains('\\') || Regex("^[A-Za-z]:").containsMatchIn(name)) {
            throw IOException("Invalid archive path: $name")
        }
        val base = root.canonicalFile.toPath()
        val target = base.resolve(name).normalize()
        if (!target.startsWith(base)) throw IOException("Archive link escapes destination: $name")
        if (target != base && !target.parent.toFile().canonicalFile.toPath().startsWith(base)) {
            throw IOException("Archive link parent escapes destination: $name")
        }
        return target.toFile()
    }
    fun resolve(root: File, name: String): File {
        if (name.isBlank() || name.startsWith('/') || name.contains('\\') || Regex("^[A-Za-z]:").containsMatchIn(name)) {
            throw IOException("Invalid archive path: $name")
        }
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, name).canonicalFile
        if (target != canonicalRoot && !target.path.startsWith(canonicalRoot.path + File.separator)) {
            throw IOException("Archive path escapes destination: $name")
        }
        return target
    }
}
