package dev.aicli.runtime.archive

import dev.aicli.core.filesystem.SafeFiles
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Keep the working binary intact until extraction and a real execution check both succeed. */
suspend fun installExecutable(
    archive: File,
    destination: File,
    binaryNames: Set<String>,
    verify: suspend (File) -> Unit,
) {
    val staging = File(destination.parentFile, ".${destination.name}-install")
    check(SafeFiles.deleteTree(staging)) { "Could not clear incomplete installation" }
    check(staging.mkdirs())
    try {
        val entries = TarGzExtractor.extract(archive, staging)
        val executable = entries.map { ArchivePaths.resolve(staging, it) }
            .firstOrNull { it.name in binaryNames && it.isFile }
            ?: error("The archive does not contain the expected executable")
        check(executable.setExecutable(true, true)) { "Could not make the agent executable" }
        verify(executable)
        Files.move(executable.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        SafeFiles.deleteTree(staging)
        archive.delete()
    }
}
