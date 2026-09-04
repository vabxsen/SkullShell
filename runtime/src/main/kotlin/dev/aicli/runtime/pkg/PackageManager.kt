package dev.aicli.runtime.pkg

import android.content.Context
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.terminal.PtyProcess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class PackageInstallEvent {
    data class Output(val line: String) : PackageInstallEvent()
    data class Completed(val exitCode: Int) : PackageInstallEvent()
}

/**
 * Thin wrapper around Termux's own `apt`/`dpkg` (present in the bootstrap) for installing
 * packages the providers need (`nodejs`, `git`, `ripgrep`, ...). We do not reimplement package
 * resolution — Termux's package repository and apt already solve dependency resolution for
 * Android/Bionic builds; we just drive it through a real PTY so output streams live to whatever
 * UI (installer screen, diagnostics) wants to show it, exactly like a user typing at a shell.
 */
class PackageManager(context: Context) {
    private val env = TermuxEnvironment(context)

    private fun aptExecutable(): String {
        val apt = java.io.File(env.prefixDir, "bin/apt")
        require(apt.exists()) { "apt not found at ${apt.absolutePath} — is the bootstrap installed?" }
        return apt.absolutePath
    }

    /** `apt update` — must be run at least once after a fresh bootstrap install before any `install`. */
    fun update(): Flow<PackageInstallEvent> = runApt(listOf("update", "-o", "APT::Update::Error-Mode=any"))

    fun install(packages: List<String>): Flow<PackageInstallEvent> =
        runApt(listOf("install", "-y") + packages)

    fun isInstalled(binaryName: String): Boolean =
        java.io.File(env.prefixDir, "bin/$binaryName").exists()

    private fun runApt(args: List<String>): Flow<PackageInstallEvent> = flow {
        val command = listOf(aptExecutable()) + args
        AppLog.i(LogCategory.INSTALLER, "Running: ${command.joinToString(" ")}")
        val process = PtyProcess.spawn(
            command = env.wrapForExec(command),
            environment = env.buildEnvironment(extra = mapOf("DEBIAN_FRONTEND" to "noninteractive")),
            workingDirectory = env.homeDir.absolutePath,
            initialCols = 120,
            initialRows = 40,
        )
        try {
        kotlinx.coroutines.withTimeout(15 * 60 * 1000L) {
        val lineBuffer = StringBuilder()
        process.outputFlow.collect { bytes ->
            val text = String(bytes, Charsets.UTF_8)
            lineBuffer.append(text)
            if (lineBuffer.length > 64 * 1024) lineBuffer.delete(0, lineBuffer.length - 64 * 1024)
            var newlineIndex = lineBuffer.indexOf("\n")
            while (newlineIndex >= 0) {
                val line = lineBuffer.substring(0, newlineIndex).trimEnd('\r')
                emit(PackageInstallEvent.Output(line))
                lineBuffer.delete(0, newlineIndex + 1)
                newlineIndex = lineBuffer.indexOf("\n")
            }
        }
        if (lineBuffer.isNotEmpty()) emit(PackageInstallEvent.Output(lineBuffer.toString()))
        val exitCode = process.waitForExit()
        AppLog.i(LogCategory.INSTALLER, "${command.joinToString(" ")} exited with $exitCode")
        emit(PackageInstallEvent.Completed(exitCode))
        }
        } finally { process.destroy() }
    }
}
