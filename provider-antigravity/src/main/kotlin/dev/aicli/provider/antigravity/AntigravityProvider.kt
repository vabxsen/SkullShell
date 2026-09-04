package dev.aicli.provider.antigravity

import android.content.Context
import dev.aicli.provider.api.*
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.runtime.foreignlibc.*
import dev.aicli.terminal.PtyProcess
import dev.aicli.terminal.runPtyCommand

class AntigravityProvider(context: Context) : AIProvider {
    override val id = "antigravity_cli"
    override val displayName = "Antigravity CLI"
    private val env = TermuxEnvironment(context)
    private val libc = ForeignLibcRuntime(context)
    override val installer = AntigravityInstaller(context)
    override val auth = AntigravityAuth(context, installer.binaryPath)
    override suspend fun checkCompatibility() = ProviderCompatibilityReport(
        compatible = env.termuxAbi in listOf("aarch64", "x86_64"),
        summary = "Antigravity runs in an Ubuntu Base compatibility environment. Device kernel restrictions can still prevent startup.",
    )
    override suspend fun detectState(): ProviderState {
        if (!installer.binaryPath.exists()) return ProviderState.NotInstalled
        if (!libc.isInstalled(LibcFlavor.GLIBC)) return ProviderState.Error("The Antigravity compatibility layer is missing")
        val result = runPtyCommand(libc.wrapCommand(LibcFlavor.GLIBC, listOf(installer.binaryPath.absolutePath,"--version"),env.homeDir.absolutePath),
            env.buildEnvironment(),env.homeDir.absolutePath)
        if (result.exitCode == 159) return ProviderState.Incompatible("This device's kernel blocked a syscall required by Antigravity")
        if (result.exitCode != 0) return ProviderState.Error("Antigravity exited with ${result.exitCode}: ${result.output.take(500)}")
        val version = Regex("\\d+\\.\\d+\\.\\d+").find(result.output)?.value ?: return ProviderState.Error("Antigravity did not report its version")
        return installer.checkForUpdate() ?: ProviderState.Installed(version)
    }
    override suspend fun launch(request: ProviderLaunchRequest): PtyProcess = PtyProcess.spawn(
        libc.wrapCommand(LibcFlavor.GLIBC,listOf(installer.binaryPath.absolutePath)+request.extraArgs,request.workingDirectory),
        env.buildEnvironment(),request.workingDirectory,request.initialCols,request.initialRows,
    )
}
