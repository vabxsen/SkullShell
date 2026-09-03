package dev.aicli.runtime.bootstrap

import android.content.Context
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.core.networking.GitHubReleaseResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

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
        try {
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
            downloadWithProgress(asset.browser_download_url, downloadFile, asset.size) { downloaded, total ->
                emit(BootstrapState.Downloading(downloaded, total))
            }

            extractBootstrap(downloadFile) { extracted, total ->
                emit(BootstrapState.Extracting(extracted, total))
            }

            downloadFile.delete()

            if (!env.isBootstrapInstalled) {
                throw BootstrapException("Extraction completed but no shell binary was found at \$PREFIX/bin")
            }
            if (!env.hasTermuxExec) {
                throw BootstrapException(
                    "Extraction completed but termux-exec's LD_PRELOAD shim is missing — process " +
                        "spawning inside the bootstrap will fail with EACCES on API 29+ (see ARCHITECTURE.md §2a)"
                )
            }

            AppLog.i(LogCategory.RUNTIME, "Bootstrap ready at ${env.prefixDir}")
            emit(BootstrapState.Ready)
        } catch (e: BootstrapException) {
            AppLog.e(LogCategory.RUNTIME, "Bootstrap install failed: ${e.message} — ${e.stackTraceToString()}")
            emit(BootstrapState.Failed(e.message ?: "Unknown bootstrap failure", e))
        } catch (e: Exception) {
            AppLog.e(LogCategory.RUNTIME, "Bootstrap install failed unexpectedly — ${e.stackTraceToString()}")
            emit(BootstrapState.Failed("Unexpected error: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    private suspend inline fun downloadWithProgress(
        url: String,
        destination: File,
        expectedSize: Long,
        crossinline onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw BootstrapException("Download failed with HTTP $code for $url")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize

            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastEmit = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        // Throttle progress emission so we don't flood the Flow for a 30MB download.
                        if (downloaded - lastEmit > 512 * 1024 || downloaded == total) {
                            onProgress(downloaded, total)
                            lastEmit = downloaded
                        }
                    }
                }
            }
            AppLog.d(LogCategory.RUNTIME, "Downloaded $url — sha256=${digest.digest().joinToString("") { "%02x".format(it) }}")
        } finally {
            connection.disconnect()
        }
    }

    private suspend inline fun extractBootstrap(zipFile: File, crossinline onProgress: suspend (extracted: Int, total: Int) -> Unit) {
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
                    File(env.prefixDir, name).mkdirs()
                } else if (name == "SYMLINKS.txt") {
                    symlinkLines += zis.bufferedReader(Charsets.UTF_8).readText().lines()
                } else {
                    val outFile = File(env.prefixDir, name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                    if (name.startsWith("bin/") || name.startsWith("libexec/") ||
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
            val linkFile = File(env.prefixDir, linkPath)
            linkFile.parentFile?.mkdirs()
            try {
                if (linkFile.exists() || java.nio.file.Files.isSymbolicLink(linkFile.toPath())) {
                    linkFile.delete()
                }
                java.nio.file.Files.createSymbolicLink(linkFile.toPath(), java.io.File(target).toPath())
                symlinksCreated++
            } catch (e: java.io.IOException) {
                AppLog.w(LogCategory.RUNTIME, "Failed to create symlink $linkPath -> $target: ${e.message}")
            }
        }
        AppLog.i(LogCategory.RUNTIME, "Extracted $extracted files, created $symlinksCreated symlinks")
    }

    fun uninstall(): Boolean {
        val result = env.prefixDir.deleteRecursively() and env.homeDir.deleteRecursively()
        AppLog.i(LogCategory.RUNTIME, "Bootstrap uninstalled: $result")
        return result
    }
}
