package dev.aicli.runtime.bootstrap

import android.content.Context
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.core.networking.GitHubReleaseResolver
import dev.aicli.core.networking.downloadFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.zip.ZipInputStream
import dev.aicli.runtime.archive.ArchivePaths
import dev.aicli.terminal.runPtyCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.aicli.runtime.pkg.PackageManager
import dev.aicli.runtime.pkg.PackageInstallEvent
import dev.aicli.core.filesystem.SafeFiles
import java.nio.file.Files

/**
 * Real, observed install state for the Linux userland — never a badge that just claims "Ready".
 * Every transition here corresponds to a step that either succeeded or reported precisely why
 * it didn't (see [BootstrapException]).
 */
sealed class BootstrapState {
    data object NotInstalled : BootstrapState()
    data class Resolving(val message: String) : BootstrapState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : BootstrapState()
    data class Extracting(val filesExtracted: Int, val totalFiles: Int) : BootstrapState()
    data object Ready : BootstrapState()
    data class Failed(val reason: String, val throwable: Throwable? = null) : BootstrapState()
}

class BootstrapException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Downloads and extracts Termux's official bootstrap archive into this app's private storage.
 * See ARCHITECTURE.md §2 for why this is the right approach instead of writing our own
 * cross-compiled userland, and §2a for why every spawned process must carry the LD_PRELOAD this
 * class's extraction step makes available.
 *
 * Verified against the real archive structure of the current bootstrap release
 * (`bootstrap-2026.08.30-r1+apt.android-7`): a flat zip rooted at what becomes `$PREFIX`
 * (entries like `bin/bash`, `lib/libc++_shared.so`, ...), plus a `SYMLINKS.txt` manifest at the
 * archive root — symlinks are *not* stored as zip symlink entries in this format, they're listed
 * as `<target>←<link path>` lines (separator is U+2190, LEFTWARDS ARROW) to be created after
 * regular files are extracted. `termux-exec`'s LD_PRELOAD shim ships inside the base archive
 * already (`lib/libtermux-exec-ld-preload.so`) — no separate package install is needed for it.
 */
class BootstrapManager(private val context: Context) {
    private val env = TermuxEnvironment(context)
    private val symlinkSeparator = "←"

    fun install(forceReinstall: Boolean = false): Flow<BootstrapState> = flow {
      installMutex.withLock {
        val staged = File(context.filesDir, "usr-install")
        val backup = File(context.filesDir, "usr-backup")
        var replaced = false
        var success = false
        try {
            // Recover a setup interrupted after the directory swap but before verification.
            if (backup.exists()) {
                if (env.isBootstrapInstalled) SafeFiles.deleteTree(backup)
                else {
                    check(SafeFiles.deleteTree(env.prefixDir))
                    Files.move(backup.toPath(), env.prefixDir.toPath())
                }
            }
            if (!forceReinstall && env.isBootstrapInstalled && env.hasTermuxExec) {
                emit(BootstrapState.Ready)
                return@flow
            }

            emit(BootstrapState.Resolving("Looking up the current Termux bootstrap release…"))
            env.ensureDirectoriesExist()

            val release = GitHubReleaseResolver
                .latestReleaseWithTagPrefix("termux", "termux-packages", "bootstrap-")
                .getOrElse { throw BootstrapException("Could not resolve the Termux bootstrap release", it) }

            val assetName = "bootstrap-${env.termuxAbi}.zip"
            val asset = release.assets.firstOrNull { it.name == assetName }
                ?: throw BootstrapException(
                    "Release ${release.tag_name} has no asset named '$assetName' for this device's " +
                        "architecture (${env.termuxAbi}). Available: ${release.assets.joinToString { it.name }}"
                )

            AppLog.i(LogCategory.RUNTIME, "Resolved bootstrap ${release.tag_name} / $assetName (${asset.size} bytes)")

            val downloadFile = File(context.cacheDir, assetName)
            downloadFile(asset.browser_download_url, downloadFile, asset.size, asset.digest) { downloaded, total ->
                emit(BootstrapState.Downloading(downloaded, total))
            }

            check(SafeFiles.deleteTree(staged)) { "Could not clear unfinished runtime setup" }
            check(staged.mkdirs())
            extractBootstrap(downloadFile, staged) { extracted, total ->
                emit(BootstrapState.Extracting(extracted, total))
            }

            downloadFile.delete()

            Files.move(env.prefixDir.toPath(), backup.toPath())
            try { Files.move(staged.toPath(), env.prefixDir.toPath()) }
            catch (e: Exception) { Files.move(backup.toPath(), env.prefixDir.toPath()); throw e }
            replaced = true
            env.ensureDirectoriesExist()

            if (!File(env.prefixDir, "bin/bash").exists()) {
                throw BootstrapException("Extraction completed but no shell binary was found at \$PREFIX/bin")
            }
            if (!env.hasTermuxExec) {
                throw BootstrapException(
                    "The bundled runtime support is missing for this device architecture. Reinstall the app."
                )
            }

            val probe = runPtyCommand(env.wrapForExec(listOf(File(env.prefixDir, "bin/bash").absolutePath,
                "-c", "ls --version && apt --version")), env.buildEnvironment(), env.homeDir.absolutePath)
            probe.requireSuccess()
            val packages = PackageManager(context)
            if (!packages.isInstalled("node") || !packages.isInstalled("npm") || !packages.isInstalled("git")) {
                emit(BootstrapState.Resolving("Installing Node.js, npm, Git and ripgrep…"))
                for (step in listOf(packages.update(), packages.install(listOf("nodejs-lts", "npm", "git", "ripgrep")))) {
                    val recent = ArrayDeque<String>()
                    step.collect { event -> when (event) {
                        is PackageInstallEvent.Output -> {
                            recent.addLast(event.line)
                            while (recent.size > 12) recent.removeFirst()
                            emit(BootstrapState.Resolving(event.line.takeLast(300)))
                        }
                        is PackageInstallEvent.Completed -> if (event.exitCode != 0) {
                            throw BootstrapException("Package setup failed (exit ${event.exitCode}): ${recent.joinToString("\n")}")
                        }
                    } }
                }
            }
            File(env.prefixDir, ".skullshell-ready").writeText(release.tag_name)
            success = true
            SafeFiles.deleteTree(backup)

            AppLog.i(LogCategory.RUNTIME, "Bootstrap ready at ${env.prefixDir}")
            emit(BootstrapState.Ready)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BootstrapException) {
            AppLog.e(LogCategory.RUNTIME, "Bootstrap install failed: ${e.message} — ${e.stackTraceToString()}")
            emit(BootstrapState.Failed(e.message ?: "Unknown bootstrap failure", e))
        } catch (e: Exception) {
            AppLog.e(LogCategory.RUNTIME, "Bootstrap install failed unexpectedly — ${e.stackTraceToString()}")
            emit(BootstrapState.Failed("Unexpected error: ${e.message}", e))
        } finally {
            if (replaced && !success) {
                check(SafeFiles.deleteTree(env.prefixDir)) { "Could not roll back failed runtime setup" }
                Files.move(backup.toPath(), env.prefixDir.toPath())
            }
            SafeFiles.deleteTree(staged)
        }
      }
    }.flowOn(Dispatchers.IO)

    private suspend inline fun extractBootstrap(zipFile: File, destination: File, crossinline onProgress: suspend (extracted: Int, total: Int) -> Unit) {
        val symlinkLines = mutableListOf<String>()
        // First pass: count entries so progress is meaningful (zip files don't expose a cheap
        // total-entry count while streaming, so we do a lightweight scan first).
        val totalEntries = java.util.zip.ZipFile(zipFile).use { it.size() }

        var extracted = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (entry.isDirectory) {
                    ArchivePaths.resolve(destination, name).mkdirs()
                } else if (name == "SYMLINKS.txt") {
                    symlinkLines += zis.bufferedReader(Charsets.UTF_8).readText().lines()
                } else {
                    val outFile = ArchivePaths.resolve(destination, name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                    val magic = outFile.inputStream().use { it.readNBytes(4) }
                    val executable = magic.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46)) ||
                        (magic.size >= 2 && magic[0] == '#'.code.toByte() && magic[1] == '!'.code.toByte())
                    if (executable || name.startsWith("bin/") || name.startsWith("libexec/") ||
                        name.startsWith("lib/") && (name.endsWith(".so") || name.contains(".so."))
                    ) {
                        outFile.setExecutable(true, false)
                    }
                    outFile.setReadable(true, false)
                }
                extracted++
                if (extracted % 50 == 0 || extracted == totalEntries) {
                    onProgress(extracted, totalEntries)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        var symlinksCreated = 0
        for (line in symlinkLines) {
            if (line.isBlank() || !line.contains(symlinkSeparator)) continue
            val (target, rawLinkPath) = line.split(symlinkSeparator, limit = 2)
            val linkPath = rawLinkPath.removePrefix("./")
            val linkFile = ArchivePaths.link(destination, linkPath)
            linkFile.parentFile?.mkdirs()
            try {
                if (linkFile.exists() || java.nio.file.Files.isSymbolicLink(linkFile.toPath())) {
                    linkFile.delete()
                }
                val relativeTarget = if (target.startsWith(TermuxEnvironment.GUEST_PREFIX + "/")) {
                    val actualTarget = ArchivePaths.link(destination, target.removePrefix(TermuxEnvironment.GUEST_PREFIX + "/"))
                    linkFile.parentFile.toPath().relativize(actualTarget.toPath())
                } else java.io.File(target).toPath()
                java.nio.file.Files.createSymbolicLink(linkFile.toPath(), relativeTarget)
                symlinksCreated++
            } catch (e: java.io.IOException) {
                AppLog.w(LogCategory.RUNTIME, "Failed to create symlink $linkPath -> $target: ${e.message}")
            }
        }
        AppLog.i(LogCategory.RUNTIME, "Extracted $extracted files, created $symlinksCreated symlinks")
    }

    fun uninstall(): Boolean {
        val result = SafeFiles.deleteTree(env.prefixDir) and SafeFiles.deleteTree(env.homeDir)
        AppLog.i(LogCategory.RUNTIME, "Bootstrap uninstalled: $result")
        return result
    }

    companion object { private val installMutex = Mutex() }
}
