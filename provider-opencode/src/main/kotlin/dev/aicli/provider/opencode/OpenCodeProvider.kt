package dev.aicli.provider.opencode

import android.content.Context
import dev.aicli.core.filesystem.SafeFiles
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
import kotlinx.serialization.json.*

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
            is AuthState.Error -> ProviderState.Installed(version)
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
    private inner class OpenCodeInstaller : ProviderInstaller {
        override fun install(): Flow<InstallEvent> = flow {
            try {
                val arch = releaseAssetArch() ?: run {
                    emit(InstallEvent.Failed("compatibility", "No OpenCode build for this device's architecture (${env.termuxAbi})"))
                    return@flow
                }

                if (!foreignLibc.isInstalled(LibcFlavor.MUSL)) {
                    emit(InstallEvent.Progress("Installing musl compatibility layer (Alpine, one-time)", 0.05f))
                    var libcFailureReason: String? = null
                    foreignLibc.install(LibcFlavor.MUSL).collect { state ->
                        if (state is ForeignLibcState.Failed) {
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
                downloadFile(asset.browser_download_url, tarball, asset.size, asset.digest)

                emit(InstallEvent.Progress("Extracting", 0.85f))
                installDir.mkdirs()
                installExecutable(tarball, binary, setOf("opencode")) { staged ->
                    emit(InstallEvent.Progress("Verifying installation", 0.97f))
                    val output = runPtyCommand(foreignLibc.wrapCommand(LibcFlavor.MUSL,
                        listOf(staged.absolutePath, "--version"), env.homeDir.absolutePath),
                        env.buildEnvironment(), env.homeDir.absolutePath).requireSuccess()
                    check(Regex("\\d+\\.\\d+\\.\\d+").containsMatchIn(output)) { "OpenCode did not report a version" }
                }
                AppLog.i(LogCategory.INSTALLER, "OpenCode installed: ${release.tag_name}")
                emit(InstallEvent.Completed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(LogCategory.INSTALLER, "OpenCode install failed: ${e.message}")
                emit(InstallEvent.Failed("unexpected", e.message ?: "Unknown error", e))
            }
        }.flowOn(Dispatchers.IO)

        override fun uninstall(): Flow<InstallEvent> = flow {
            check(SafeFiles.deleteTree(installDir)) { "Could not remove agent files" }
            emit(InstallEvent.Completed)
        }.flowOn(Dispatchers.IO)

        override suspend fun checkForUpdate(): ProviderState.UpdateAvailable? = withContext(Dispatchers.IO) {
            val currentVersion = runVersionCommand() ?: return@withContext null
            val latest = GitHubReleaseResolver.latestRelease("anomalyco", "opencode").getOrNull() ?: return@withContext null
            val latestVersion = latest.tag_name.removePrefix("v")
            if (ReleaseVersion.isNewer(latestVersion, currentVersion)) {
                ProviderState.UpdateAvailable(currentVersion, latestVersion)
            } else null
        }
    }

    private inner class OpenCodeAuth : ProviderAuth {
        private val authFile = File(env.homeDir, ".local/share/opencode/auth.json")

        override suspend fun currentState(): AuthState = withContext(Dispatchers.IO) {
            if (!authFile.exists()) return@withContext AuthState.SignedOut
            try {
                val credentials = Json.parseToJsonElement(authFile.readText()).jsonObject
                if (credentials.values.any { entry ->
                    val value = entry as? JsonObject
                    listOf("key", "access", "refresh").any { field ->
                        (value?.get(field) as? JsonPrimitive)?.content?.isNotBlank() == true
                    }
                }) AuthState.SignedIn else AuthState.SignedOut
            } catch (_: Exception) { AuthState.Error("OpenCode credentials could not be read. Sign in again.") }
        }

        override suspend fun startLogin(): PtyProcess {
            val wrapped = foreignLibc.wrapCommand(LibcFlavor.MUSL, listOf(binary.absolutePath, "auth", "login"), env.homeDir.absolutePath)
            return PtyProcess.spawn(wrapped, env.buildEnvironment(), env.homeDir.absolutePath, 100, 30)
        }

        override suspend fun logout() = withContext(Dispatchers.IO) {
            check(!authFile.exists() || authFile.delete()) { "Could not remove OpenCode credentials" }
            Unit
        }
    }
}
