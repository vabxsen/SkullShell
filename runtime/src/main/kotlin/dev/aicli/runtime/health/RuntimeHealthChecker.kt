package dev.aicli.runtime.health

import android.content.Context
import android.os.Build
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.core.networking.NetworkMonitor
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.terminal.PtyProcess
import dev.aicli.terminal.runPtyCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

enum class CheckStatus { PASS, FAIL, NOT_CHECKED }

data class HealthCheckResult(
    val id: String,
    val label: String,
    val status: CheckStatus,
    val detail: String,
)

/**
 * Every check here actually runs something — spawns a real process, opens a real file, makes a
 * real socket connection — and reports what really happened. No check is allowed to report PASS
 * without having been executed this call; see the project brief's explicit "Do not show green
 * checks unless the check was actually performed."
 */
class RuntimeHealthChecker(private val context: Context) {
    private val env = TermuxEnvironment(context)
    private val networkMonitor = NetworkMonitor(context)

    suspend fun runAll(): List<HealthCheckResult> = listOf(
        checkAbi(),
        checkBootstrapInstalled(),
        checkTermuxExec(),
        checkCommand("node", "--version", "Node.js"),
        checkCommand("npm", "--version", "npm"),
        checkCommand("git", "--version", "Git"),
        checkPty(),
        checkFilesystemWritable(),
        checkNetwork(),
    )

    private fun checkAbi(): HealthCheckResult {
        val supported = Build.SUPPORTED_ABIS.toList()
        val is64 = supported.any { it == "arm64-v8a" || it == "x86_64" }
        return HealthCheckResult(
            id = "abi",
            label = "Device architecture",
            status = if (is64) CheckStatus.PASS else CheckStatus.FAIL,
            detail = if (is64) "Supported ABIs: ${supported.joinToString()}, using Termux ABI '${env.termuxAbi}'"
                     else "No 64-bit ABI found (${supported.joinToString()}) — CLI binaries require arm64-v8a or x86_64",
        )
    }

    private fun checkBootstrapInstalled(): HealthCheckResult {
        val installed = env.isBootstrapInstalled
        return HealthCheckResult(
            id = "bootstrap",
            label = "Linux userland",
            status = if (installed) CheckStatus.PASS else CheckStatus.FAIL,
            detail = if (installed) "Bootstrap present at ${env.prefixDir}" else "Not installed — open Settings > Environment to set up the runtime",
        )
    }

    private fun checkTermuxExec(): HealthCheckResult {
        val present = env.hasTermuxExec
        return HealthCheckResult(
            id = "termux_exec",
            label = "Runtime execution support",
            status = if (present) CheckStatus.PASS else CheckStatus.FAIL,
            detail = if (present) "Bundled PRoot and loader are present; see the PTY check for execution results"
                     else "Bundled runtime support is missing for this architecture",
        )
    }

    private suspend fun checkCommand(binary: String, versionArg: String, label: String): HealthCheckResult {
        val executable = File(env.prefixDir, "bin/$binary")
        if (!executable.exists()) {
            return HealthCheckResult("cmd_$binary", label, CheckStatus.FAIL, "$binary not installed in the bootstrap")
        }
        return try {
            val result = withTimeoutOrNull(8_000) { runCommand(listOf(executable.absolutePath, versionArg)) }
            if (result == null) {
                HealthCheckResult("cmd_$binary", label, CheckStatus.FAIL, "Timed out running $binary $versionArg")
            } else {
                HealthCheckResult("cmd_$binary", label, CheckStatus.PASS, result.trim().lineSequence().firstOrNull() ?: "OK")
            }
        } catch (e: Exception) {
            HealthCheckResult("cmd_$binary", label, CheckStatus.FAIL, e.message ?: "Failed to run $binary")
        }
    }

    private suspend fun checkPty(): HealthCheckResult = try {
        val shellPath = File(env.prefixDir, "bin/sh").takeIf { it.exists() }?.absolutePath ?: "/system/bin/sh"
        val process = withTimeoutOrNull(5_000) {
            PtyProcess.spawn(
                command = env.wrapForExec(listOf(shellPath, "-c", "exit 0")),
                environment = env.buildEnvironment(),
                workingDirectory = env.homeDir.takeIf { it.exists() }?.absolutePath ?: context.filesDir.absolutePath,
                initialCols = 80,
                initialRows = 24,
            )
        }
        if (process == null) {
            HealthCheckResult("pty", "PTY", CheckStatus.FAIL, "PTY spawn timed out")
        } else {
            val exitCode = withTimeoutOrNull(5_000) { process.waitForExit() }
            process.destroy()
            // Exit code 127 conventionally means "exec failed" (missing binary, or the target
            // couldn't actually be loaded — e.g. a real SELinux exec denial) — the PTY mechanism
            // itself can still "work" in the sense that fork/pty-alloc succeeded, but that's not
            // a fact worth a green check if the one command we ran couldn't actually run.
            when (exitCode) {
                0 -> HealthCheckResult("pty", "PTY", CheckStatus.PASS, "PTY opened, shell exited cleanly")
                127 -> HealthCheckResult("pty", "PTY", CheckStatus.FAIL, "PTY opened but exec failed (exit 127) — $shellPath could not actually run")
                else -> HealthCheckResult("pty", "PTY", CheckStatus.FAIL, "PTY opened, but the shell exited with unexpected code $exitCode")
            }
        }
    } catch (e: Exception) {
        HealthCheckResult("pty", "PTY", CheckStatus.FAIL, e.message ?: "PTY open failed")
    }

    private fun checkFilesystemWritable(): HealthCheckResult = try {
        val probe = File(context.filesDir, ".health_probe")
        probe.writeText("ok")
        val ok = probe.readText() == "ok"
        probe.delete()
        HealthCheckResult("fs", "Workspace filesystem", if (ok) CheckStatus.PASS else CheckStatus.FAIL,
            if (ok) "Read/write verified at ${context.filesDir}" else "Read-back mismatch")
    } catch (e: Exception) {
        HealthCheckResult("fs", "Workspace filesystem", CheckStatus.FAIL, e.message ?: "Not writable")
    }

    private suspend fun checkNetwork(): HealthCheckResult {
        val reachable = networkMonitor.canReach("registry.npmjs.org", 443)
        return HealthCheckResult(
            id = "network",
            label = "Network",
            status = if (reachable) CheckStatus.PASS else CheckStatus.FAIL,
            detail = if (reachable) "Reached registry.npmjs.org:443" else "Could not reach registry.npmjs.org:443",
        )
    }

    private suspend fun runCommand(command: List<String>): String {
        return runPtyCommand(env.wrapForExec(command), env.buildEnvironment(), env.homeDir.absolutePath).requireSuccess()
    }
}
