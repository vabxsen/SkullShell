package dev.aicli.provider.antigravity

import android.content.Context
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.provider.api.InstallEvent
import dev.aicli.provider.api.ProviderInstaller
import dev.aicli.provider.api.ProviderState
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.runtime.pkg.PackageInstallEvent
import dev.aicli.runtime.pkg.PackageManager
import dev.aicli.terminal.PtyProcess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Runs Google's own installer (`curl -fsSL https://antigravity.google/cli/install.sh | bash`),
 * the same shape as [dev.aicli.provider.claude.ClaudeInstaller]. This step reliably places the
 * `agy` binary — the failure modes documented in [AntigravityCompatibility] happen at *launch*
 * time (missing glibc, seccomp, VA-width), not install time, so a successful install here is not
 * a promise the binary will run; [AntigravityProvider.detectState] is the real signal.
 */
class AntigravityInstaller(private val context: Context) : ProviderInstaller {
    private val env = TermuxEnvironment(context)
    private val pkg = PackageManager(context)

    val binaryPath: File get() = File(env.homeDir, ".local/bin/agy")

    override fun install(): Flow<InstallEvent> = flow {
        if (!env.isBootstrapInstalled) {
            emit(InstallEvent.Failed("bootstrap", "The Linux runtime isn't set up yet — install it from Home first."))
            return@flow
        }

        if (!pkg.isInstalled("curl")) {
            emit(InstallEvent.Progress("prerequisites", 0.1f, "Installing curl…"))
            var curlFailed = false
            pkg.install(listOf("curl")).collect { event ->
                when (event) {
                    is PackageInstallEvent.Output -> emit(InstallEvent.Progress("prerequisites", null, event.line))
                    is PackageInstallEvent.Completed -> if (event.exitCode != 0) curlFailed = true
                }
            }
            if (curlFailed) {
                emit(InstallEvent.Failed("prerequisites", "Failed to install curl via apt — check network and try again."))
                return@flow
            }
        }

        emit(InstallEvent.Progress("download", 0.3f, "Running Google's Antigravity CLI installer…"))
        val shell = File(env.prefixDir, "bin/bash").absolutePath
        val process = PtyProcess.spawn(
            command = env.wrapForExec(listOf(shell, "-lc", "curl -fsSL https://antigravity.google/cli/install.sh | bash")),
            environment = env.buildEnvironment(),
            workingDirectory = env.homeDir.absolutePath,
            initialCols = 120,
            initialRows = 40,
        )
        val lineBuffer = StringBuilder()
        process.outputFlow.collect { bytes ->
            lineBuffer.append(String(bytes, Charsets.UTF_8))
            var idx = lineBuffer.indexOf("\n")
            while (idx >= 0) {
                val line = lineBuffer.substring(0, idx).trimEnd('\r')
                if (line.isNotBlank()) emit(InstallEvent.Progress("install", 0.7f, line))
                lineBuffer.delete(0, idx + 1)
                idx = lineBuffer.indexOf("\n")
            }
        }
        val exitCode = process.waitForExit()

        if (exitCode != 0) {
            emit(InstallEvent.Failed("install", "install.sh exited with code $exitCode"))
            return@flow
        }
        if (!binaryPath.exists()) {
            emit(InstallEvent.Failed("verify", "install.sh reported success but no binary was found at ${binaryPath.absolutePath}"))
            return@flow
        }
        binaryPath.setExecutable(true, false)
        AppLog.i(LogCategory.INSTALLER, "Antigravity CLI installed at ${binaryPath.absolutePath} (launch-time compatibility not yet verified — see AntigravityCompatibility)")
        emit(InstallEvent.Completed)
    }

    override fun uninstall(): Flow<InstallEvent> = flow {
        val deleted = binaryPath.delete()
        emit(if (deleted || !binaryPath.exists()) InstallEvent.Completed else InstallEvent.Failed("uninstall", "Could not remove ${binaryPath.absolutePath}"))
    }

    override suspend fun checkForUpdate(): ProviderState.UpdateAvailable? = null
}
