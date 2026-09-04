package dev.aicli.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.terminal.runPtyCommand
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun nativePtyPreservesOutputAndExitCode() = runBlocking {
        repeat(20) {
            val result = runPtyCommand(listOf("/system/bin/sh", "-c", "printf 'hello-世界'; exit 7"),
                mapOf("PATH" to "/system/bin"), context.filesDir.absolutePath)
            assertEquals("hello-世界", result.output)
            assertEquals(7, result.exitCode)
        }
    }

    @Test fun missingWorkingDirectoryFailsInsteadOfWritingElsewhere() = runBlocking {
        val result = runPtyCommand(listOf("/system/bin/sh", "-c", "echo should-not-run"),
            emptyMap(), context.filesDir.resolve("missing-audit-directory").absolutePath)
        assertEquals(126, result.exitCode)
        assertFalse(result.output.contains("should-not-run"))
    }

    @Test fun installedRuntimeRunsSubprocesses() = runBlocking {
        val env = TermuxEnvironment(context)
        assertTrue("Install runtime before running this device test", env.isBootstrapInstalled)
        val command = listOf(env.prefixDir.resolve("bin/bash").absolutePath, "-c", "ls --version")
        val result = runPtyCommand(env.wrapForExec(command), env.buildEnvironment(), env.homeDir.absolutePath)
        assertEquals(result.output, 0, result.exitCode)
        assertTrue(result.output, result.output.contains("coreutils"))
    }

    @Test fun installedRuntimePackageManagerWorks() = runBlocking {
        val env = TermuxEnvironment(context)
        val result = runPtyCommand(env.wrapForExec(listOf(env.prefixDir.resolve("bin/apt").absolutePath, "--version")),
            env.buildEnvironment(), env.homeDir.absolutePath)
        assertEquals(result.output, 0, result.exitCode)
        assertTrue(result.output, result.output.contains("apt"))
    }

    @Test fun runtimePreservesRequestedWorkingDirectory() = runBlocking {
        val env = TermuxEnvironment(context)
        val directory = context.filesDir.resolve("workspaces/cwd-audit").apply { mkdirs() }
        val result = runPtyCommand(env.wrapForExec(listOf(env.prefixDir.resolve("bin/bash").absolutePath,"-c","printf audit-ok > result.txt"), directory.absolutePath),
            env.buildEnvironment(), directory.absolutePath)
        assertEquals(result.output, 0, result.exitCode)
        assertEquals("audit-ok", directory.resolve("result.txt").readText())
        directory.resolve("result.txt").delete()
        directory.delete()
        Unit
    }

    @Test fun timeoutClosesProcessAndDoesNotHang() = runBlocking {
        val started = System.currentTimeMillis()
        try {
            runPtyCommand(listOf("/system/bin/sh", "-c", "sleep 30"), mapOf("PATH" to "/system/bin"), context.filesDir.absolutePath, timeoutMillis=200)
            fail("Expected timeout")
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) { }
        assertTrue(System.currentTimeMillis() - started < 5000)
    }

    @Test fun compatibilityShellsCanRunDevelopmentTools() = runBlocking {
        val env = TermuxEnvironment(context)
        for (flavor in listOf("musl", "glibc")) {
            val root = context.filesDir.resolve("foreign/$flavor")
            assertTrue("Install compatibility layers first", root.resolve("bin/sh").exists())
            val directory = context.filesDir.resolve("workspaces/tools-audit-${java.util.UUID.randomUUID()}").apply { mkdirs() }
            try {
                val command = """node -e 'require("node:fs").writeFileSync("test.txt", "audit-ok")' && git init -q && git add test.txt && git -c user.name=Audit -c user.email=audit@example.invalid commit -qm test && rg audit-ok test.txt"""
                val result = runPtyCommand(env.prootCommand(listOf("/bin/sh", "-c", command), directory.absolutePath, root),
                    env.buildEnvironment(), directory.absolutePath)
                assertEquals("$flavor: ${result.output}", 0, result.exitCode)
                assertTrue(result.output.contains("audit-ok"))
            } finally { dev.aicli.core.filesystem.SafeFiles.deleteTree(directory) }
        }
    }
}
