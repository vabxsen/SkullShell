package dev.aicli.provider.claude

import android.content.Context
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.core.networking.NpmRegistryResolver
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
import dev.aicli.runtime.foreignlibc.ForeignLibcState
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
 * Claude Code. Verified directly (not assumed) against the real npm tarball for
 * `@anthropic-ai/claude-code-linux-arm64-musl`: `file` reports
 * `ELF 64-bit ... dynamically linked, interpreter /lib/ld-musl-aarch64.so.1` — a hardcoded
 * absolute path, so it needs the same [ForeignLibcRuntime] (MUSL/Alpine, via proot) layer as
 * OpenCode. See ARCHITECTURE.md's Claude Code section for the full trail, including why we
 * fetch the npm platform package directly instead of running the official install.sh or `npm
 * install -g` (both rely on platform auto-detection that breaks under Termux's Node, which
 * reports `process.platform === "android"`).
 */
class ClaudeProvider(private val context: Context) : AIProvider {
    override val id = "claude_code"
    override val displayName = "Claude Code"

    private val env = TermuxEnvironment(context)
    private val foreignLibc = ForeignLibcRuntime(context)
    private val installDir = File(env.prefixDir, "opt/claude-code")
    private val binary = File(installDir, "claude")

    override val installer: ProviderInstaller = ClaudeInstaller()
    override val auth: ProviderAuth = ClaudeAuth()

    private fun npmPlatformPackage(): String? = when (env.termuxAbi) {
        "aarch64" -> "@anthropic-ai/claude-code-linux-arm64-musl"
        "x86_64" -> "@anthropic-ai/claude-code-linux-x64-musl"
        else -> null
    }

    override suspend fun checkCompatibility(): ProviderCompatibilityReport {
        val pkg = npmPlatformPackage()
            ?: return ProviderCompatibilityReport(false, "No Claude Code build exists for this device's architecture (${env.termuxAbi}).")
        return ProviderCompatibilityReport(
            compatible = true,
            summary = "Claude Code's $pkg build is musl-linked and runs under a small Alpine-based " +
                "compatibility layer (proot), not direct Bionic execution.",
            caveats = listOf(
                "Runs under proot with a downloaded Alpine musl root filesystem (shared with OpenCode " +
                    "if both are installed — the layer is installed once) — unverified on real ARM64 " +
                    "hardware in this build (only an x86_64 emulator was available; see ARCHITECTURE.md).",
                "Requires Node.js 22+ per Claude Code's own package.json engines field, but the " +
                    "installed binary itself does not depend on Node at runtime — only the resolution " +
                    "step above uses the npm registry's metadata API, not npm/Node itself.",
            ),
        )
    }

    override suspend fun detectState(): ProviderState {
        if (!binary.exists()) return ProviderState.NotInstalled
        if (!foreignLibc.isInstalled(LibcFlavor.MUSL)) {
            return ProviderState.Error("Claude Code binary present but its musl compatibility layer is not installed")
        }
        val version = runVersionCommand()
            ?: return ProviderState.Error("claude --version failed — see Diagnostics for the exact failure (likely proot or a CPU-feature mismatch)")
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
        return PtyProcess.spawn(wrapped, env.buildEnvironment(), request.workingDirectory, request.initialCols, request.initialRows)
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
            AppLog.w(LogCategory.PROVIDER, "claude --version failed: ${e.message}")
            null
        }
    }

    private inner class ClaudeInstaller : ProviderInstaller {
        override fun install(): Flow<InstallEvent> = flow {
            try {
                val pkg = npmPlatformPackage() ?: run {
                    emit(InstallEvent.Failed("compatibility", "No Claude Code build for this device's architecture (${env.termuxAbi})"))
                    return@flow
                }

                if (!foreignLibc.isInstalled(LibcFlavor.MUSL)) {
                    emit(InstallEvent.Progress("Installing musl compatibility layer (Alpine, one-time)", 0.05f))
                    var libcFailureReason: String? = null
                    foreignLibc.install(LibcFlavor.MUSL).collect { state ->
                        if (state is ForeignLibcState.Failed) {
                            // Don't let the real failure reason get swallowed into a generic
                            // "compatibility layer failed" message below — surface it verbatim.
                            libcFailureReason = state.reason
                        } else {
                            emit(InstallEvent.Progress("Compatibility layer: $state", null))
                        }
                    }
                    val libcFailure = libcFailureReason
                    if (libcFailure != null) {
                        emit(InstallEvent.Failed("foreign_libc", libcFailure))
                        return@flow
                    }
                    if (!foreignLibc.isInstalled(LibcFlavor.MUSL)) {
                        emit(InstallEvent.Failed("foreign_libc", "Failed to install the musl compatibility layer"))
                        return@flow
                    }
                }

                emit(InstallEvent.Progress("Resolving latest Claude Code release ($pkg)", 0.4f))
                val version = NpmRegistryResolver.latestVersion(pkg).getOrElse {
                    emit(InstallEvent.Failed("resolve_release", "Could not resolve $pkg from the npm registry: ${it.message}", it))
                    return@flow
                }

                emit(InstallEvent.Progress("Downloading claude-code ${version.version}", 0.55f))
                val tarball = File(context.cacheDir, "claude-code-${version.version}.tgz")
                downloadTo(version.dist.tarball, tarball)

                emit(InstallEvent.Progress("Extracting", 0.85f))
                installDir.mkdirs()
                extractTarGz(tarball, installDir) // npm tarballs are rooted at "package/"
                tarball.delete()

                val extractedBinary = File(installDir, "package/claude")
                if (!extractedBinary.exists()) {
                    emit(InstallEvent.Failed("extract", "No 'claude' binary found inside the $pkg tarball after extraction"))
                    return@flow
                }
                extractedBinary.setExecutable(true, false)
                if (binary.exists() || java.nio.file.Files.isSymbolicLink(binary.toPath())) binary.delete()
                java.nio.file.Files.createSymbolicLink(binary.toPath(), extractedBinary.toPath())

                emit(InstallEvent.Progress("Verifying installation", 0.97f))
                if (runVersionCommand() == null) {
                    emit(InstallEvent.Failed("verify", "claude was installed but --version did not succeed"))
                    return@flow
                }
                AppLog.i(LogCategory.INSTALLER, "Claude Code installed: ${version.version}")
                emit(InstallEvent.Completed)
            } catch (e: Exception) {
                AppLog.e(LogCategory.INSTALLER, "Claude Code install failed: ${e.message}")
                emit(InstallEvent.Failed("unexpected", e.message ?: "Unknown error", e))
            }
        }.flowOn(Dispatchers.IO)

        override fun uninstall(): Flow<InstallEvent> = flow {
            installDir.deleteRecursively()
            emit(InstallEvent.Completed)
        }.flowOn(Dispatchers.IO)

        override suspend fun checkForUpdate(): ProviderState.UpdateAvailable? = withContext(Dispatchers.IO) {
            val pkg = npmPlatformPackage() ?: return@withContext null
            val currentVersion = runVersionCommand() ?: return@withContext null
            val latest = NpmRegistryResolver.latestVersion(pkg).getOrNull() ?: return@withContext null
            if (latest.version.isNotBlank() && !currentVersion.contains(latest.version)) {
                ProviderState.UpdateAvailable(currentVersion, latest.version)
            } else null
        }
    }

    /**
     * Claude Code manages its own auth state (Pro/Max/Console-key login) internally and doesn't
     * publish a documented "check if logged in" flag. Rather than guess at an undocumented
     * credential file format and risk a false SignedOut, this checks only for the *presence* of a
     * non-empty credentials file under `~/.claude` — if that layout ever changes upstream, this
     * degrades to [AuthState.Unknown], never to a false positive. The authoritative signal is
     * always the real CLI session: launching `claude` shows its own login prompt when needed,
     * exactly like on desktop — we don't intercept or fake that.
     */
    private inner class ClaudeAuth : ProviderAuth {
        private val candidateCredentialFiles: List<File>
            get() = listOf(File(env.homeDir, ".claude/.credentials.json"), File(env.homeDir, ".claude.json"))

        override suspend fun currentState(): AuthState = withContext(Dispatchers.IO) {
            when {
                !binary.exists() -> AuthState.Unknown
                System.getenv("ANTHROPIC_API_KEY") != null -> AuthState.SignedIn
                candidateCredentialFiles.any { it.exists() && it.length() > 0 } -> AuthState.SignedIn
                candidateCredentialFiles.any { it.parentFile?.exists() == true } -> AuthState.SignedOut
                else -> AuthState.Unknown
            }
        }

        /** Claude Code's own `/login` flow runs inside the CLI itself once launched, not a separate subcommand. */
        override suspend fun startLogin(): PtyProcess {
            val wrapped = foreignLibc.wrapCommand(LibcFlavor.MUSL, listOf(binary.absolutePath), env.homeDir.absolutePath)
            return PtyProcess.spawn(wrapped, env.buildEnvironment(), env.homeDir.absolutePath, 100, 30)
        }

        override suspend fun logout() = withContext(Dispatchers.IO) {
            candidateCredentialFiles.forEach { it.delete() }
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
