package dev.aicli.core.filesystem

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Comparator

object SafeFiles {
    /** Files.walk does not follow links: deleting an installation never visits linked system directories. */
    fun deleteTree(directory: File): Boolean {
        val path = directory.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
        return runCatching {
            Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }.isSuccess
    }
}
