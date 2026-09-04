package dev.aicli.provider.claude

import android.content.Context
import dev.aicli.core.filesystem.SafeFiles
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
import dev.aicli.terminal.runPtyCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import dev.aicli.runtime.archive.installExecutable
import dev.aicli.core.networking.downloadFile
import dev.aicli.core.networking.ReleaseVersion

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
            is AuthState.Error -> ProviderState.Installed(version)
        }
    }

    override suspend fun launch(request: ProviderLaunchRequest): PtyProcess {
        val wrapped = foreignLibc.wrapCommand(LibcFlavor.MUSL, listOf(binary.absolutePath) + request.extraArgs, request.workingDirectory)
        return PtyProcess.spawn(wrapped, env.buildEnvironment(), request.workingDirectory, request.initialCols, request.initialRows)
    }

    private suspend fun runVersionCommand(): String? {
        return try {
            val result = runPtyCommand(foreignLibc.wrapCommand(LibcFlavor.MUSL, listOf(binary.absolutePath, "--version"), env.homeDir.absolutePath), env.buildEnvironment(), env.homeDir.absolutePath)
            if (result.exitCode != 0) {
                AppLog.w(LogCategory.PROVIDER, "Version check failed (exit ${result.exitCode}): ${result.output.take(500)}")
                null
            } else Regex("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?").find(result.output)?.value
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(LogCategory.PROVIDER, "Version check failed: ${e.message}")
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
                downloadFile(version.dist.tarball, tarball, expectedDigest = version.dist.shasum?.let { "sha1:$it" })

                emit(InstallEvent.Progress("Extracting", 0.85f))
                installDir.mkdirs()
                installExecutable(tarball, binary, setOf("claude")) { staged ->
                    emit(InstallEvent.Progress("Verifying installation", 0.97f))
                    val output = runPtyCommand(foreignLibc.wrapCommand(LibcFlavor.MUSL,
                        listOf(staged.absolutePath, "--version"), env.homeDir.absolutePath),
                        env.buildEnvironment(), env.homeDir.absolutePath).requireSuccess()
                    check(Regex("\\d+\\.\\d+\\.\\d+").containsMatchIn(output)) { "Claude did not report a version" }
                }
                AppLog.i(LogCategory.INSTALLER, "Claude Code installed: ${version.version}")
                emit(InstallEvent.Completed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(LogCategory.INSTALLER, "Claude Code install failed: ${e.message}")
                emit(InstallEvent.Failed("unexpected", e.message ?: "Unknown error", e))
            }
        }.flowOn(Dispatchers.IO)

        override fun uninstall(): Flow<InstallEvent> = flow {
            check(SafeFiles.deleteTree(installDir)) { "Could not remove agent files" }
            emit(InstallEvent.Completed)
        }.flowOn(Dispatchers.IO)

        override suspend fun checkForUpdate(): ProviderState.UpdateAvailable? = withContext(Dispatchers.IO) {
            val pkg = npmPlatformPackage() ?: return@withContext null
            val currentVersion = runVersionCommand() ?: return@withContext null
            val latest = NpmRegistryResolver.latestVersion(pkg).getOrNull() ?: return@withContext null
            if (ReleaseVersion.isNewer(latest.version, currentVersion)) {
                ProviderState.UpdateAvailable(currentVersion, latest.version)
            } else null
        }
    }

    /** Authentication status comes from the official CLI, not configuration-file existence. */
    private inner class ClaudeAuth : ProviderAuth {
        private suspend fun authCommand(action: String) = runPtyCommand(
            foreignLibc.wrapCommand(LibcFlavor.MUSL, listOf(binary.absolutePath, "auth", action), env.homeDir.absolutePath),
            env.buildEnvironment(), env.homeDir.absolutePath,
        )

        override suspend fun currentState(): AuthState {
            if (!binary.exists()) return AuthState.Unknown
            return when (val result = authCommand("status")) {
                else -> when (result.exitCode) {
                    0 -> AuthState.SignedIn
                    1 -> AuthState.SignedOut
                    else -> AuthState.Error("Could not check Claude authentication (exit ${result.exitCode})")
                }
            }
        }

        override suspend fun startLogin(): PtyProcess = PtyProcess.spawn(
            foreignLibc.wrapCommand(LibcFlavor.MUSL, listOf(binary.absolutePath, "auth", "login"), env.homeDir.absolutePath),
            env.buildEnvironment(), env.homeDir.absolutePath, 100, 30,
        )

        override suspend fun logout() { authCommand("logout").requireSuccess() }
    }
}
