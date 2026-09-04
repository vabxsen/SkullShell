package dev.aicli.terminal

import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream

data class CommandResult(val exitCode: Int, val output: String) {
    fun requireSuccess(): String {
        check(exitCode == 0) { "Command exited with $exitCode: ${output.takeLast(2000).trim()}" }
        return output
    }
}

/** Drains output before reading the result. Always closes the PTY, including on timeout. */
suspend fun runPtyCommand(
    command: List<String>,
    environment: Map<String, String>,
    workingDirectory: String,
    timeoutMillis: Long = 15_000,
    maxOutputBytes: Int = 256 * 1024,
): CommandResult {
    val process = PtyProcess.spawn(command, environment, workingDirectory, 120, 40)
    return try {
        withTimeout(timeoutMillis) {
            val output = ByteArrayOutputStream()
            process.outputFlow.collect { bytes ->
                val remaining = maxOutputBytes - output.size()
                if (remaining > 0) output.write(bytes, 0, minOf(bytes.size, remaining))
            }
            CommandResult(process.waitForExit(), output.toString("UTF-8"))
        }
    } finally {
        process.destroy()
    }
}
