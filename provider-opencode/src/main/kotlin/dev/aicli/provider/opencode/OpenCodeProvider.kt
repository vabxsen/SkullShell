package dev.aicli.provider.opencode

import android.content.Context
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.core.networking.GitHubReleaseResolver
import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.AuthState
import dev.aicli.provider.api.InstallEvent
import dev.aicli.provider.api.ProviderAuth
import dev.aicli.provider.api.ProviderCompatibilityReport
import dev.aicli.provider.api.ProviderInstaller
import dev.aicli.provider.api.ProviderLaunchRequest
import dev.aicli.provider.api.ProviderState
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.runtime.foreignlibc.ForeignLibcRuntime
import dev.aicli.runtime.foreignlibc.LibcFlavor
import dev.aicli.terminal.PtyProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * OpenCode (`anomalyco/opencode`, formerly under the `sst` org — `sst/opencode` now 301-redirects
 * there, verified directly against the GitHub API). Two real, verified compatibility facts:
 *
 * 1. Its npm package (`opencode-ai`) has an open, real upstream bug (anomalyco/opencode#12515):
 *    the postinstall script keys off `process.platform`, which Termux's own Node build reports
 *    as `"android"` (not `"linux"`), so it looks for a package that was never published
 *    (`opencode-android-arm64`) and fails. We never go through npm for this reason — we fetch
 *    the real release binary directly from GitHub Releases.
 * 2. The Linux release ships two variants per arch: a glibc one (`opencode-linux-arm64.tar.gz`)
 *    and a musl one (`opencode-linux-arm64-musl.tar.gz`). Downloaded and inspected the musl
 *    build directly with `file`: `ELF 64-bit ... dynamically linked, interpreter
 *    /lib/ld-musl-aarch64.so.1` — the exact same hardcoded-absolute-musl-interpreter situation
 *    as Claude Code (see ARCHITECTURE.md). So OpenCode needs the same [ForeignLibcRuntime]
 *    (MUSL/Alpine) proot layer Claude Code does — this was not assumed, it was verified.
 */
class OpenCodeProvider(private val context: Context) : AIProvider {
    override val id = "opencode"
    override val displayName = "OpenCode"

    private val env = TermuxEnvironment(context)
    private val foreignLibc = ForeignLibcRuntime(context)
    private val installDir = File(env.prefixDir, "opt/opencode")
    private val binary = File(installDir, "opencode")

    override val installer: ProviderInstaller = OpenCodeInstaller()
    override val auth: ProviderAuth = OpenCodeAuth()

    private fun releaseAssetArch(): String? = when (env.termuxAbi) {
        "aarch64" -> "arm64"
        "x86_64" -> "x64"
        else -> null
    }

    override suspend fun checkCompatibility(): ProviderCompatibilityReport {
        val arch = releaseAssetArch()
            ?: return ProviderCompatibilityReport(false, "No OpenCode build exists for this device's architecture (${env.termuxAbi}).")
        return ProviderCompatibilityReport(
            compatible = true,
            summary = "OpenCode's Linux build for $arch is musl-linked and requires a small Alpine-based " +
                "compatibility layer (proot), not direct Bionic execution.",
            caveats = listOf(
                "Runs under proot with a downloaded Alpine musl root filesystem (~a few MB one-time " +
                    "download) — slightly slower startup than a native binary, and unverified on real " +
                    "ARM64 hardware in this build (only an x86_64 emulator was available).",
            ),
        )
    }

    override suspend fun detectState(): ProviderState {
        if (!binary.exists()) return ProviderState.NotInstalled
        if (!foreignLibc.isInstalled(LibcFlavor.MUSL)) {
            return ProviderState.Error("OpenCode binary present but its musl compatibility layer is not installed")
        }
        val version = runVersionCommand() ?: return ProviderState.Error(
            "opencode --version failed — likely a missing 'proot' package or an incompatible device CPU " +
                "feature (see ARCHITECTURE.md's Antigravity/Claude compatibility notes for the class of issue)"
        )
        val update = installer.checkForUpdate()
        if (update != null) return update
        return when (auth.currentState()) {
            AuthState.SignedIn -> ProviderState.Ready(version)
            AuthState.SignedOut, AuthState.Unknown -> ProviderState.AuthRequired
            is AuthState.Error -> ProviderState.Ready(version)
        }
    }

    override suspend fun launch(request: ProviderLaunchRequest): PtyProcess {
        val wrapped = foreignLibc.wrapCommand(LibcFlavor.MUSL, listOf(binary.absolutePath) + request.extraArgs, request.workingDirectory)
        return PtyProcess.spawn(
            command = wrapped,
            environment = env.buildEnvironment(),
            workingDirectory = request.workingDirectory,
            initialCols = request.initialCols,
            initialRows = request.initialRows,
        )
    }

    private suspend fun runVersionCommand(): String? = withContext(Dispatchers.IO) {
        try {
            val wrapped = foreignLibc.wrapCommand(LibcFlavor.MUSL, listOf(binary.absolutePath, "--version"), env.homeDir.absolutePath)
            val process = PtyProcess.spawn(wrapped, env.buildEnvironment(), env.homeDir.absolutePath, 80, 24)
            val output = StringBuilder()
            val job = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                process.outputFlow.collect { output.append(String(it, Charsets.UTF_8)) }
            }
            process.waitForExit()
            job.cancel()
            output.toString().trim().lineSequence().firstOrNull { it.isNotBlank() }
        } catch (e: Exception) {
            AppLog.w(LogCategory.PROVIDER, "opencode --version failed: ${e.message}")
            null
        }
    }

    private inner class OpenCodeInstaller : ProviderInstaller {
        override fun install(): Flow<InstallEvent> = flow {
            try {
                val arch = releaseAssetArch() ?: run {
                    emit(InstallEvent.Failed("compatibility", "No OpenCode build for this device's architecture (${env.termuxAbi})"))
                    return@flow
                }

                if (!foreignLibc.isInstalled(LibcFlavor.MUSL)) {
                    emit(InstallEvent.Progress("Installing musl compatibility layer (Alpine, one-time)", 0.05f))
                    foreignLibc.install(LibcFlavor.MUSL).collect { state ->
                        emit(InstallEvent.Progress("Compatibility layer: $state", null))
                    }
                    if (!foreignLibc.isInstalled(LibcFlavor.MUSL)) {
                        emit(InstallEvent.Failed("foreign_libc", "Failed to install the musl compatibility layer"))
                        return@flow
                    }
                }

                emit(InstallEvent.Progress("Resolving latest OpenCode release", 0.4f))
                val release = GitHubReleaseResolver.latestRelease("anomalyco", "opencode").getOrElse {
                    emit(InstallEvent.Failed("resolve_release", "Could not resolve OpenCode release: ${it.message}", it))
                    return@flow
                }

                val assetName = "opencode-linux-$arch-musl.tar.gz"
                val asset = release.assets.firstOrNull { it.name == assetName } ?: run {
                    emit(InstallEvent.Failed(
                        "resolve_asset",
                        "Release ${release.tag_name} has no asset '$assetName'. Available: ${release.assets.joinToString { it.name }}",
                    ))
                    return@flow
                }

                emit(InstallEvent.Progress("Downloading $assetName", 0.55f))
                val tarball = File(context.cacheDir, assetName)
                downloadTo(asset.browser_download_url, tarball)

                emit(InstallEvent.Progress("Extracting", 0.85f))
                installDir.mkdirs()
                extractTarGz(tarball, installDir)
                tarball.delete()
                if (!binary.exists()) {
                    emit(InstallEvent.Failed("extract", "No 'opencode' binary found after extracting $assetName"))
                    return@flow
                }
                binary.setExecutable(true, false)

                emit(InstallEvent.Progress("Verifying installation", 0.97f))
                if (runVersionCommand() == null) {
                    emit(InstallEvent.Failed("verify", "opencode was installed but --version did not succeed"))
                    return@flow
                }
                AppLog.i(LogCategory.INSTALLER, "OpenCode installed: ${release.tag_name}")
                emit(InstallEvent.Completed)
            } catch (e: Exception) {
                AppLog.e(LogCategory.INSTALLER, "OpenCode install failed: ${e.message}")
                emit(InstallEvent.Failed("unexpected", e.message ?: "Unknown error", e))
            }
        }.flowOn(Dispatchers.IO)

        override fun uninstall(): Flow<InstallEvent> = flow {
            installDir.deleteRecursively()
            emit(InstallEvent.Completed)
        }.flowOn(Dispatchers.IO)

        override suspend fun checkForUpdate(): ProviderState.UpdateAvailable? = withContext(Dispatchers.IO) {
            val currentVersion = runVersionCommand() ?: return@withContext null
            val latest = GitHubReleaseResolver.latestRelease("anomalyco", "opencode").getOrNull() ?: return@withContext null
            val latestVersion = latest.tag_name.removePrefix("v")
            if (latestVersion.isNotBlank() && !currentVersion.contains(latestVersion)) {
                ProviderState.UpdateAvailable(currentVersion, latestVersion)
            } else null
        }
    }

    private inner class OpenCodeAuth : ProviderAuth {
        private val authFile = File(env.homeDir, ".local/share/opencode/auth.json")

        override suspend fun currentState(): AuthState = withContext(Dispatchers.IO) {
            if (authFile.exists() && authFile.length() > 0) AuthState.SignedIn else AuthState.SignedOut
        }

        override suspend fun startLogin(): PtyProcess {
            val wrapped = foreignLibc.wrapCommand(LibcFlavor.MUSL, listOf(binary.absolutePath, "auth", "login"), env.homeDir.absolutePath)
            return PtyProcess.spawn(wrapped, env.buildEnvironment(), env.homeDir.absolutePath, 100, 30)
        }

        override suspend fun logout() = withContext(Dispatchers.IO) {
            authFile.delete()
            Unit
        }
    }
}

private fun downloadTo(url: String, destination: File) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 30_000
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) error("Download failed with HTTP $code for $url")
        connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
    } finally {
        connection.disconnect()
    }
}

private fun extractTarGz(tarGz: File, destDir: File) {
    GZIPInputStream(tarGz.inputStream().buffered()).use { gzip ->
        val header = ByteArray(512)
        while (true) {
            var total = 0
            while (total < 512) {
                val n = gzip.read(header, total, 512 - total)
                if (n == -1) break
                total += n
            }
            if (total < 512 || header.all { it == 0.toByte() }) break

            val name = cString(header, 0, 100)
            if (name.isBlank()) break
            val sizeOctal = cString(header, 124, 12).trim()
            val size = if (sizeOctal.isBlank()) 0L else sizeOctal.toLong(8)
            val typeFlag = header[156].toInt().toChar()

            val target = File(destDir, name)
            if (typeFlag == '5') {
                target.mkdirs()
            } else if (size > 0) {
                target.parentFile?.mkdirs()
                val content = ByteArray(size.toInt())
                var read = 0
                while (read < content.size) {
                    val n = gzip.read(content, read, content.size - read)
                    if (n == -1) break
                    read += n
                }
                target.outputStream().use { it.write(content) }
                val padding = (512 - (size % 512)) % 512
                if (padding > 0) gzip.skip(padding)
            }
        }
    }
}

private fun cString(bytes: ByteArray, offset: Int, length: Int): String {
    val end = (offset until offset + length).firstOrNull { bytes[it] == 0.toByte() } ?: (offset + length)
    return String(bytes, offset, end - offset, Charsets.US_ASCII)
}
