package dev.aicli.provider.codex

import android.content.Context
import dev.aicli.core.filesystem.SafeFiles
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.core.networking.GitHubReleaseResolver
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.AuthState
import dev.aicli.provider.api.InstallEvent
import dev.aicli.provider.api.ProviderAuth
import dev.aicli.provider.api.ProviderCompatibilityReport
import dev.aicli.provider.api.ProviderInstaller
import dev.aicli.provider.api.ProviderLaunchRequest
import dev.aicli.provider.api.ProviderState
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
 * OpenAI's Codex CLI. See ARCHITECTURE.md's "Codex CLI" section: it ships a real, statically
 * linked `*-unknown-linux-musl` Rust binary (Rust's musl target is static by default — no
 * dynamic interpreter at all), so unlike Claude Code/Antigravity it runs by direct exec inside
 * the Bionic bootstrap with no foreign-libc layer.
 *
 * The one deliberate compatibility override: Codex's own Linux sandbox (Landlock + a bubblewrap
 * fallback needing unprivileged user namespaces) does not work under Android's kernel/SELinux
 * restrictions for regular apps. Every launch therefore passes `--sandbox danger-full-access`
 * unless the caller already specified a `--sandbox` value — the Android application
 * sandbox is the isolation layer; individual projects are not isolated from each other. This is surfaced to the user
 * via [checkCompatibility]'s caveats, not hidden.
 */
class CodexProvider(private val context: Context) : AIProvider {
    override val id = "codex_cli"
    override val displayName = "Codex CLI"

    private val env = TermuxEnvironment(context)
    private val binary = File(env.prefixDir, "bin/codex")

    override val installer: ProviderInstaller = CodexInstaller()
    override val auth: ProviderAuth = CodexAuth()

    override suspend fun checkCompatibility(): ProviderCompatibilityReport {
        val abi = env.termuxAbi
        val targetTriple = rustTargetTriple(abi)
        if (targetTriple == null) {
            return ProviderCompatibilityReport(
                compatible = false,
                summary = "No known Codex CLI build targets this device's architecture ($abi).",
            )
        }
        return ProviderCompatibilityReport(
            compatible = true,
            summary = "Codex CLI ships a statically-linked musl build for $targetTriple; no foreign-libc layer needed.",
            caveats = listOf(
                "Codex's built-in Linux sandbox (Landlock/bubblewrap) cannot run under Android's " +
                    "kernel restrictions, so every launch runs with --sandbox danger-full-access. " +
                    "Commands share this app's Android sandbox and can access its other projects and files.",
            ),
        )
    }

    override suspend fun detectState(): ProviderState {
        if (!binary.exists()) return ProviderState.NotInstalled
        val version = runVersionCommand() ?: return ProviderState.Error("codex --version produced no output")
        val update = installer.checkForUpdate()
        if (update != null) return update
        return when (auth.currentState()) {
            AuthState.SignedIn -> ProviderState.Ready(version)
            AuthState.SignedOut, AuthState.Unknown -> ProviderState.AuthRequired
            is AuthState.Error -> ProviderState.Installed(version)
        }
    }

    override suspend fun launch(request: ProviderLaunchRequest): PtyProcess {
        val hasSandboxFlag = request.extraArgs.any { it == "--sandbox" || it.startsWith("--sandbox=") }
        val args = if (hasSandboxFlag) request.extraArgs else request.extraArgs + listOf("--sandbox", "danger-full-access")
        return PtyProcess.spawn(
            command = env.wrapForExec(listOf(binary.absolutePath) + args, request.workingDirectory),
            environment = env.buildEnvironment(),
            workingDirectory = request.workingDirectory,
            initialCols = request.initialCols,
            initialRows = request.initialRows,
        )
    }

    private fun rustTargetTriple(termuxAbi: String): String? = when (termuxAbi) {
        "aarch64" -> "aarch64-unknown-linux-musl"
        "x86_64" -> "x86_64-unknown-linux-musl"
        else -> null // no known 32-bit (arm/i686) musl build published
    }

    private suspend fun runVersionCommand(): String? {
        return try {
            val result = runPtyCommand(env.wrapForExec(listOf(binary.absolutePath, "--version")), env.buildEnvironment(), env.homeDir.absolutePath)
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
    private inner class CodexInstaller : ProviderInstaller {
        override fun install(): Flow<InstallEvent> = flow {
            try {
                emit(InstallEvent.Progress("Resolving latest Codex release", 0.05f))
                val release = GitHubReleaseResolver.latestReleaseWithTagPrefix("openai", "codex", "rust-v")
                    .recoverCatching { GitHubReleaseResolver.latestRelease("openai", "codex").getOrThrow() }
                    .getOrElse {
                        emit(InstallEvent.Failed("resolve_release", "Could not resolve a Codex release: ${it.message}", it))
                        return@flow
                    }

                val triple = rustTargetTriple(env.termuxAbi)
                    ?: run {
                        emit(InstallEvent.Failed("compatibility", "No Codex build exists for this device's architecture (${env.termuxAbi})"))
                        return@flow
                    }
                val assetName = "codex-$triple.tar.gz"
                val asset = release.assets.firstOrNull { it.name == assetName }
                    ?: run {
                        emit(InstallEvent.Failed(
                            "resolve_asset",
                            "Release ${release.tag_name} has no asset '$assetName'. Available: ${release.assets.joinToString { a -> a.name }}",
                        ))
                        return@flow
                    }

                emit(InstallEvent.Progress("Downloading $assetName", 0.2f))
                val tarball = File(context.cacheDir, assetName)
                downloadFile(asset.browser_download_url, tarball, asset.size, asset.digest)

                emit(InstallEvent.Progress("Extracting", 0.7f))
                env.prefixDir.resolve("bin").mkdirs()
                installExecutable(tarball, binary, setOf("codex", "codex-$triple")) { staged ->
                    emit(InstallEvent.Progress("Verifying installation", 0.95f))
                    val output = runPtyCommand(env.wrapForExec(listOf(staged.absolutePath, "--version")),
                        env.buildEnvironment(), env.homeDir.absolutePath).requireSuccess()
                    check(Regex("\\d+\\.\\d+\\.\\d+").containsMatchIn(output)) { "Codex did not report a version" }
                }
                AppLog.i(LogCategory.INSTALLER, "Codex CLI installed: ${release.tag_name}")
                emit(InstallEvent.Completed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(LogCategory.INSTALLER, "Codex install failed: ${e.message}")
                emit(InstallEvent.Failed("unexpected", e.message ?: "Unknown error", e))
            }
        }.flowOn(Dispatchers.IO)

        override fun uninstall(): Flow<InstallEvent> = flow {
            check(!binary.exists() || binary.delete()) { "Could not remove Codex executable" }
            check(SafeFiles.deleteTree(File(env.prefixDir, "opt/codex"))) { "Could not remove Codex files" }
            emit(InstallEvent.Completed)
        }.flowOn(Dispatchers.IO)

        override suspend fun checkForUpdate(): ProviderState.UpdateAvailable? = withContext(Dispatchers.IO) {
            val currentVersion = runVersionCommand() ?: return@withContext null
            val latest = GitHubReleaseResolver.latestReleaseWithTagPrefix("openai", "codex", "rust-v").getOrNull()
                ?: GitHubReleaseResolver.latestRelease("openai", "codex").getOrNull()
                ?: return@withContext null
            val latestVersion = latest.tag_name.removePrefix("rust-v")
            if (ReleaseVersion.isNewer(latestVersion, currentVersion)) {
                ProviderState.UpdateAvailable(currentVersion, latestVersion)
            } else null
        }
    }

    private inner class CodexAuth : ProviderAuth {
        private val credentialsFile = File(env.homeDir, ".codex/auth.json")

        override suspend fun currentState(): AuthState = withContext(Dispatchers.IO) {
            val result = runPtyCommand(env.wrapForExec(listOf(binary.absolutePath, "login", "status")), env.buildEnvironment(), env.homeDir.absolutePath)
            when (result.exitCode) {
                0 -> AuthState.SignedIn
                1 -> AuthState.SignedOut
                else -> AuthState.Error("Could not check Codex authentication (exit ${result.exitCode})")
            }
        }

        /**
         * Runs Codex's own `codex login` inside a real PTY. If that flow needs a browser (OAuth),
         * Codex itself prints/opens the URL — we never build our own OAuth or WebView handling.
         */
        override suspend fun startLogin(): PtyProcess = PtyProcess.spawn(
            command = env.wrapForExec(listOf(binary.absolutePath, "login", "--device-auth")),
            environment = env.buildEnvironment(),
            workingDirectory = env.homeDir.absolutePath,
            initialCols = 100,
            initialRows = 30,
        )

        override suspend fun logout() = withContext(Dispatchers.IO) {
            runPtyCommand(env.wrapForExec(listOf(binary.absolutePath, "logout")),
                env.buildEnvironment(), env.homeDir.absolutePath).requireSuccess()
            Unit
        }
    }
}
