package dev.aicli.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aicli.runtime.bootstrap.BootstrapManager
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.terminal.runPtyCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RuntimeRecoveryTest {
    private class StopRepair : CancellationException("Audit: cancel after the runtime directory swap")

    @Test fun cancelledRepairRestoresTheWorkingRuntimeAndPreservesHome() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("network") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val env = TermuxEnvironment(context)
        assertTrue(env.isBootstrapInstalled)
        val sentinel = env.homeDir.resolve("audit-recovery-${UUID.randomUUID()}").apply { writeText("keep") }
        try {
            try {
                BootstrapManager(context).install(forceReinstall = true).collect {
                    if (context.filesDir.resolve("usr-backup").isDirectory) throw StopRepair()
                }
                fail("Expected to cancel the replacement before it completed")
            } catch (_: StopRepair) { }
            assertTrue(env.isBootstrapInstalled)
            assertEquals("keep", sentinel.readText())
            assertFalse(context.filesDir.resolve("usr-backup").exists())
            val result = runPtyCommand(env.wrapForExec(listOf(env.prefixDir.resolve("bin/node").absolutePath, "--version")),
                env.buildEnvironment(), env.homeDir.absolutePath)
            assertEquals(result.output, 0, result.exitCode)
        } finally { sentinel.delete() }
        Unit
    }
}
