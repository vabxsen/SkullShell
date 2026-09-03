package dev.aicli.provider.antigravity

import android.content.Context
import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.ProviderCompatibilityReport
import dev.aicli.provider.api.ProviderLaunchRequest
import dev.aicli.provider.api.ProviderState
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.terminal.PtyProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * See ARCHITECTURE.md §5 and [AntigravityCompatibility] — this is the one provider where
 * `detectState()` may legitimately return [ProviderState.Incompatible] with a precise reason
 * instead of [ProviderState.Ready], because some of its failure modes are real kernel/CPU
 * properties this app cannot change. We detect them by their actual, documented signatures rather
 * than guessing.
 */
class AntigravityProvider(context: Context) : AIProvider {
    override val id: String = "antigravity_cli"
    override val displayName: String = "Antigravity CLI"

    private val env = TermuxEnvironment(context)
    override val installer = AntigravityInstaller(context)
    override val auth = AntigravityAuth(context, installer.binaryPath)

    override suspend fun checkCompatibility(): ProviderCompatibilityReport = AntigravityCompatibility.check()

    override suspend fun detectState(): ProviderState {
        val binary = installer.binaryPath
        if (!binary.exists()) return ProviderState.NotInstalled

        val probe = withTimeoutOrNull(10_000) { probeVersion(binary.absolutePath) }
            ?: return ProviderState.Error("Timed out running 'agy --version'")

        probe.knownIncompatibilityReason?.let { return ProviderState.Incompatible(it) }

        if (probe.exitCode != 0 || probe.version == null) {
            return ProviderState.Error(
                "agy exited with code ${probe.exitCode} and did not report a version. " +
                    "Output: ${probe.rawOutput.take(500)}",
            )
        }
        return ProviderState.Ready(probe.version)
    }

    override suspend fun launch(request: ProviderLaunchRequest): PtyProcess {
        val binary = installer.binaryPath
        check(binary.exists()) { "Antigravity CLI is not installed" }
        return PtyProcess.spawn(
            command = env.wrapForExec(listOf(binary.absolutePath) + request.extraArgs),
            environment = AntigravityEnvironment.build(env),
            workingDirectory = request.workingDirectory,
            initialCols = request.initialCols,
            initialRows = request.initialRows,
        )
    }

    private data class Probe(val exitCode: Int, val rawOutput: String, val version: String?, val knownIncompatibilityReason: String?)

    private suspend fun probeVersion(binaryPath: String): Probe {
        val process = PtyProcess.spawn(
            command = env.wrapForExec(listOf(binaryPath, "--version")),
            environment = AntigravityEnvironment.build(env),
            workingDirectory = env.homeDir.absolutePath,
            initialCols = 80,
            initialRows = 24,
        )
        val output = StringBuilder()
        val collectJob = CoroutineScope(Dispatchers.IO).launch {
            process.outputFlow.collect { output.append(String(it, Charsets.UTF_8)) }
        }
        val exitCode = process.waitForExit()
        collectJob.cancel()
        val raw = output.toString()

        // SIGSYS is signal 31 on Linux; PtyProcess's exit-code convention is 128+signal (see
        // pty_native.c waitFor()). This is the faccessat2-blocked-by-seccomp failure documented
        // in AntigravityCompatibility.
        val reason = when {
            exitCode == 128 + 31 || raw.contains("SIGSYS") || raw.contains("bad system call", ignoreCase = true) ->
                "This device's kernel blocks a syscall (faccessat2) that Antigravity CLI's Go " +
                    "runtime requires. This is a kernel-level seccomp policy — no setting in this " +
                    "app can work around it."
            raw.contains("TCMalloc", ignoreCase = true) && raw.contains("48-bit", ignoreCase = true) ->
                "This device uses a virtual-address layout (39-bit) that Antigravity CLI's memory " +
                    "allocator doesn't support. This is a CPU/kernel property, not something this " +
                    "app can configure around."
            raw.contains("not a dynamic executable", ignoreCase = true) || raw.contains("No such file or directory", ignoreCase = true) && raw.contains("libc.so", ignoreCase = true) ->
                "Antigravity CLI needs a glibc runtime, which isn't set up. See Settings → " +
                    "Providers → Antigravity for manual glibc setup instructions."
            else -> null
        }

        val version = Regex("""\d+\.\d+\.\d+""").find(raw)?.value
        return Probe(exitCode, raw, version, reason)
    }
}
