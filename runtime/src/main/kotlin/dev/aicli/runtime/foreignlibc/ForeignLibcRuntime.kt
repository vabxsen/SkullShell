package dev.aicli.runtime.foreignlibc

import android.content.Context
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.runtime.pkg.PackageInstallEvent
import dev.aicli.runtime.pkg.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Some upstream CLI binaries are dynamically linked against a libc our Termux-Bionic bootstrap
 * (§2) doesn't provide — Claude Code's official Linux ARM64 build links against **musl**
 * (verified 2026-09-03 by downloading `@anthropic-ai/claude-code-linux-arm64-musl` and running
 * `file` on the binary: `interpreter /lib/ld-musl-aarch64.so.1`), and Antigravity CLI links
 * against **glibc** (see `AntigravityCompatibility.kt`). Neither loader exists in a Bionic
 * userland. [ForeignLibcRuntime] gets one by extracting a small, real Linux rootfs of the right
 * flavor and running the target binary inside it via `proot` (ptrace-based — no root, no kernel
 * namespaces, works the same way `proot-distro` does for Termux users today).
 *
 * MUSL is fully wired: Alpine Linux's official `alpine-minirootfs` tarball (~3MB, MIT-adjacent —
 * Alpine itself is a mix of BSD/MIT-licensed base tools) is resolved dynamically from Alpine's
 * own CDN (`latest-stable` symlink, verified live 2026-09-03).
 *
 * GLIBC is **not** wired yet. Every source investigated this session for a small, reliably
 * downloadable official glibc rootfs failed: Ubuntu's `cloud-images.ubuntu.com/base/` tarball
 * path structure has changed and no longer serves what earlier documentation described; the
 * `debuerreotype/docker-debian-artifacts` repo's OCI layer blobs return a 16-byte placeholder
 * over both raw GitHub content and the Git LFS batch API (object not found) rather than the real
 * ~48MB layer. Antigravity CLI (the only provider that needs GLIBC) is reported
 * `ProviderState.Incompatible` with this exact reason rather than silently failing or pretending
 * to work — see `AntigravityCompatibility.kt`. A maintainer picking this back up should verify a
 * real download URL by hand (`curl -IL` a candidate, confirm `Content-Length` matches the real
 * archive size, not a redirect/placeholder page) before wiring [LibcFlavor.GLIBC] here.
 */
enum class LibcFlavor { MUSL, GLIBC }

sealed class ForeignLibcState {
    data object NotInstalled : ForeignLibcState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ForeignLibcState()
    data class Extracting(val filesExtracted: Int) : ForeignLibcState()
    data object Ready : ForeignLibcState()
    data class Failed(val reason: String) : ForeignLibcState()
}

class ForeignLibcRuntime(private val context: Context) {
    private val env = TermuxEnvironment(context)
    private val packageManager = PackageManager(context)

    private fun rootfsDir(flavor: LibcFlavor): File = File(context.filesDir, "foreign/${flavor.name.lowercase()}")

    fun isInstalled(flavor: LibcFlavor): Boolean = when (flavor) {
        LibcFlavor.MUSL -> File(rootfsDir(flavor), "lib/ld-musl-${muslArchName()}.so.1").exists()
        LibcFlavor.GLIBC -> false // never installed — see class doc; no working source yet
    }

    fun install(flavor: LibcFlavor): Flow<ForeignLibcState> = flow {
        val rootfsDir = rootfsDir(flavor)
        if (flavor == LibcFlavor.GLIBC) {
            emit(ForeignLibcState.Failed(
                "No verified glibc rootfs source is wired up yet (see ForeignLibcRuntime.kt doc " +
                    "comment for exactly what was tried and why it failed) — this is a genuine " +
                    "unimplemented gap, not a transient error."
            ))
            return@flow
        }
        if (isInstalled(flavor)) {
            emit(ForeignLibcState.Ready)
            return@flow
        }

        // proot itself comes from Termux's own package repo, same as every other bootstrap tool.
        if (!packageManager.isInstalled("proot")) {
            val result = packageManager.install(listOf("proot")).last()
            if (result is PackageInstallEvent.Completed && result.exitCode != 0) {
                emit(ForeignLibcState.Failed("Failed to install proot (exit ${result.exitCode})"))
                return@flow
            }
        }

        val arch = muslArchName()
        val indexUrl = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/$arch/"
        val indexHtml = try {
            httpGetText(indexUrl)
        } catch (e: Exception) {
            emit(ForeignLibcState.Failed("Could not reach Alpine's release index: ${e.message}"))
            return@flow
        }
        val versionRegex = Regex("alpine-minirootfs-([0-9.]+)-$arch\\.tar\\.gz")
        val versionPartsComparator = Comparator<List<Int>> { a, b ->
            for (i in 0 until maxOf(a.size, b.size)) {
                val cmp = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
                if (cmp != 0) return@Comparator cmp
            }
            0
        }
        val match = versionRegex.findAll(indexHtml).map { it.groupValues[1] }.maxWithOrNull(
            compareBy(versionPartsComparator) { version -> version.split(".").map { part -> part.toIntOrNull() ?: 0 } }
        )
        if (match == null) {
            emit(ForeignLibcState.Failed("Alpine's release index at $indexUrl listed no minirootfs for $arch"))
            return@flow
        }
        val tarballName = "alpine-minirootfs-$match-$arch.tar.gz"
        val tarballUrl = "$indexUrl$tarballName"

        AppLog.i(LogCategory.RUNTIME, "Resolved Alpine minirootfs $tarballName")
        rootfsDir.mkdirs()
        val downloadFile = File(context.cacheDir, tarballName)
        try {
            downloadWithProgress(tarballUrl, downloadFile) { downloaded, total ->
                emit(ForeignLibcState.Downloading(downloaded, total))
            }
            var extracted = 0
            extractTarGz(downloadFile, rootfsDir) { extracted++ }
            emit(ForeignLibcState.Extracting(extracted))
            downloadFile.delete()
        } catch (e: Exception) {
            emit(ForeignLibcState.Failed("Alpine rootfs setup failed: ${e.message}"))
            return@flow
        }

        if (!isInstalled(flavor)) {
            emit(ForeignLibcState.Failed("Extraction completed but ${LibcFlavor.MUSL} loader is still missing"))
            return@flow
        }
        AppLog.i(LogCategory.RUNTIME, "Foreign libc runtime ($flavor) ready at $rootfsDir")
        emit(ForeignLibcState.Ready)
    }.flowOn(Dispatchers.IO)

    /**
     * Wraps [command] to run inside [flavor]'s foreign-libc rootfs via `proot`. Always binds this
     * app's own bootstrap prefix, home, tmp, and [workingDirectory] into the guest view at
     * identical paths — without this, a CLI binary living under `env.prefixDir` (as every
     * provider's installed CLI does) would be invisible inside the proot'd rootfs, and the CLI
     * couldn't see `$HOME` or the project directory either. [extraBindMounts] adds any further
     * host-path-to-guest-path bindings a caller needs beyond those defaults.
     */
    fun wrapCommand(
        flavor: LibcFlavor,
        command: List<String>,
        workingDirectory: String,
        extraBindMounts: Map<String, String> = emptyMap(),
    ): List<String> {
        check(isInstalled(flavor)) { "Foreign libc runtime ($flavor) is not installed" }
        val prootBinary = File(env.prefixDir, "bin/proot").absolutePath
        val args = mutableListOf(prootBinary, "-r", rootfsDir(flavor).absolutePath, "--kill-on-exit")
        val essentialPaths = linkedSetOf(env.prefixDir.absolutePath, env.homeDir.absolutePath, env.tmpDir.absolutePath, workingDirectory)
        for (path in essentialPaths) {
            args += listOf("-b", "$path:$path")
        }
        for ((hostPath, guestPath) in extraBindMounts) {
            args += listOf("-b", "$hostPath:$guestPath")
        }
        args += listOf("-w", workingDirectory)
        args += command
        // proot itself is an app_data_file binary — its own first exec needs the same
        // exec-via-system-linker treatment as everything else. See TermuxEnvironment.wrapForExec.
        return env.wrapForExec(args)
    }

    private fun muslArchName(): String = when (env.termuxAbi) {
        "aarch64" -> "aarch64"
        "x86_64" -> "x86_64"
        "arm" -> "armhf"
        "i686" -> "x86"
        else -> error("Unsupported ABI for foreign libc runtime: ${env.termuxAbi}")
    }

    private fun httpGetText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        return try {
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private suspend inline fun downloadWithProgress(url: String, destination: File, crossinline onProgress: suspend (Long, Long) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Minimal tar (POSIX ustar) extractor over a GZIP stream — Alpine's minirootfs is small
     * (~3MB compressed) so this doesn't need to be a streaming marvel, just correct. We don't
     * pull in Apache Commons Compress for one archive format; this is ~50 lines of ustar header
     * parsing, which is a stable, decades-old format.
     */
    private fun extractTarGz(tarGzFile: File, destinationDir: File, onEntry: () -> Unit) {
        GZIPInputStream(tarGzFile.inputStream().buffered()).use { gzip ->
            val header = ByteArray(512)
            while (true) {
                val read = readFully(gzip, header)
                if (read < 512 || header.all { it == 0.toByte() }) break

                val name = header.decodeString(0, 100)
                if (name.isBlank()) break
                val sizeOctal = header.decodeString(124, 12).trim().trim(' ')
                val size = if (sizeOctal.isBlank()) 0L else sizeOctal.toLong(8)
                val typeFlag = header[156].toInt().toChar()
                val linkName = header.decodeString(157, 100)

                val outFile = File(destinationDir, name)
                when (typeFlag) {
                    '5' -> outFile.mkdirs() // directory
                    '2' -> { // symlink
                        outFile.parentFile?.mkdirs()
                        try {
                            if (outFile.exists()) outFile.delete()
                            java.nio.file.Files.createSymbolicLink(outFile.toPath(), File(linkName).toPath())
                        } catch (_: java.io.IOException) { /* best-effort, non-fatal for a minirootfs */ }
                    }
                    else -> { // regular file (includes '0' and ' ')
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> copyExactly(gzip, out, size) }
                        if (name.startsWith("bin/") || name.startsWith("lib/") || name.startsWith("usr/bin/") || name.startsWith("sbin/")) {
                            outFile.setExecutable(true, false)
                        }
                    }
                }
                onEntry()

                // Tar pads each entry to a 512-byte boundary.
                val padding = (512 - (size % 512)) % 512
                if (typeFlag != '5' && typeFlag != '2' && padding > 0) skipFully(gzip, padding)
            }
        }
    }

    private fun ByteArray.decodeString(offset: Int, length: Int): String =
        String(this, offset, length, Charsets.US_ASCII).substringBefore(' ')

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    private fun copyExactly(input: java.io.InputStream, output: java.io.OutputStream, size: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipFully(input: java.io.InputStream, bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(4096)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) break
            remaining -= read
        }
    }
}
