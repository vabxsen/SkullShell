package dev.aicli.provider.codex

import android.content.Context
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
import kotlinx.coroutines.CoroutineScope
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
 * OpenAI's Codex CLI. See ARCHITECTURE.md's "Codex CLI" section: it ships a real, statically
 * linked `*-unknown-linux-musl` Rust binary (Rust's musl target is static by default — no
 * dynamic interpreter at all), so unlike Claude Code/Antigravity it runs by direct exec inside
 * the Bionic bootstrap with no foreign-libc layer.
 *
 * The one deliberate compatibility override: Codex's own Linux sandbox (Landlock + a bubblewrap
 * fallback needing unprivileged user namespaces) does not work under Android's kernel/SELinux
 * restrictions for regular apps. Every launch therefore passes `--sandbox danger-full-access`
 * unless the caller already specified a `--sandbox` value — our own workspace/filesystem
 * boundary (SafePath/WorkspaceRoot) is the isolation layer instead. This is surfaced to the user
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
                    "This app's own workspace boundary is the isolation layer instead.",
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
            is AuthState.Error -> ProviderState.Ready(version) // auth check itself failing shouldn't block launch; codex will report auth errors on its own
        }
    }

    override suspend fun launch(request: ProviderLaunchRequest): PtyProcess {
        val hasSandboxFlag = request.extraArgs.any { it == "--sandbox" || it.startsWith("--sandbox=") }
        val args = if (hasSandboxFlag) request.extraArgs else request.extraArgs + listOf("--sandbox", "danger-full-access")
        return PtyProcess.spawn(
            command = env.wrapForExec(listOf(binary.absolutePath) + args),
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

    private suspend fun runVersionCommand(): String? = withContext(Dispatchers.IO) {
        try {
            val process = PtyProcess.spawn(
                command = env.wrapForExec(listOf(binary.absolutePath, "--version")),
                environment = env.buildEnvironment(),
                workingDirectory = env.homeDir.absolutePath,
                initialCols = 80,
                initialRows = 24,
            )
            val output = StringBuilder()
            val job = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                process.outputFlow.collect { output.append(String(it, Charsets.UTF_8)) }
            }
            process.waitForExit()
            job.cancel()
            output.toString().trim().lineSequence().firstOrNull()
        } catch (e: Exception) {
            AppLog.w(LogCategory.PROVIDER, "codex --version failed: ${e.message}")
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
                downloadTo(asset.browser_download_url, tarball)

                emit(InstallEvent.Progress("Extracting", 0.7f))
                val destDir = File(env.prefixDir, "opt/codex")
                destDir.mkdirs()
                extractTarGz(tarball, destDir)
                tarball.delete()

                val extractedBinary = destDir.walkTopDown().firstOrNull { it.name == "codex" && it.isFile }
                    ?: run {
                        emit(InstallEvent.Failed("extract", "No 'codex' binary found inside $assetName after extraction"))
                        return@flow
                    }
                extractedBinary.setExecutable(true, false)
                env.prefixDir.resolve("bin").mkdirs()
                if (binary.exists() || java.nio.file.Files.isSymbolicLink(binary.toPath())) binary.delete()
                java.nio.file.Files.createSymbolicLink(binary.toPath(), extractedBinary.toPath())

                emit(InstallEvent.Progress("Verifying installation", 0.95f))
                if (runVersionCommand() == null) {
                    emit(InstallEvent.Failed("verify", "codex was installed but --version did not succeed"))
                    return@flow
                }
                AppLog.i(LogCategory.INSTALLER, "Codex CLI installed: ${release.tag_name}")
                emit(InstallEvent.Completed)
            } catch (e: Exception) {
                AppLog.e(LogCategory.INSTALLER, "Codex install failed: ${e.message}")
                emit(InstallEvent.Failed("unexpected", e.message ?: "Unknown error", e))
            }
        }.flowOn(Dispatchers.IO)

        override fun uninstall(): Flow<InstallEvent> = flow {
            binary.delete()
            File(env.prefixDir, "opt/codex").deleteRecursively()
            emit(InstallEvent.Completed)
        }.flowOn(Dispatchers.IO)

        override suspend fun checkForUpdate(): ProviderState.UpdateAvailable? = withContext(Dispatchers.IO) {
            val currentVersion = runVersionCommand() ?: return@withContext null
            val latest = GitHubReleaseResolver.latestReleaseWithTagPrefix("openai", "codex", "rust-v").getOrNull()
                ?: GitHubReleaseResolver.latestRelease("openai", "codex").getOrNull()
                ?: return@withContext null
            val latestVersion = latest.tag_name.removePrefix("rust-v")
            if (latestVersion.isNotBlank() && !currentVersion.contains(latestVersion)) {
                ProviderState.UpdateAvailable(currentVersion, latestVersion)
            } else null
        }
    }

    private inner class CodexAuth : ProviderAuth {
        private val credentialsFile = File(env.homeDir, ".codex/auth.json")

        override suspend fun currentState(): AuthState = withContext(Dispatchers.IO) {
            when {
                credentialsFile.exists() -> AuthState.SignedIn
                System.getenv("OPENAI_API_KEY") != null -> AuthState.SignedIn
                else -> AuthState.SignedOut
            }
        }

        /**
         * Runs Codex's own `codex login` inside a real PTY. If that flow needs a browser (OAuth),
         * Codex itself prints/opens the URL — we never build our own OAuth or WebView handling.
         */
        override suspend fun startLogin(): PtyProcess = PtyProcess.spawn(
            command = env.wrapForExec(listOf(binary.absolutePath, "login")),
            environment = env.buildEnvironment(),
            workingDirectory = env.homeDir.absolutePath,
            initialCols = 100,
            initialRows = 30,
        )

        override suspend fun logout() = withContext(Dispatchers.IO) {
            credentialsFile.delete()
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

/** Minimal ustar extractor — Codex's release tarballs are plain `tar.gz` with no exotic entries. */
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
