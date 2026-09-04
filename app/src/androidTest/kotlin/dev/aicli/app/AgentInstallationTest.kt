package dev.aicli.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aicli.provider.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentInstallationTest {
    @Test fun claudeInstallsAndStarts() = verify("claude_code")
    @Test fun codexInstallsAndStarts() = verify("codex_cli")
    @Test fun openCodeInstallsAndStarts() = verify("opencode")
    @Test fun antigravityInstallsAndStarts() = verify("antigravity_cli")

    private fun verify(id: String) = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("network") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as AiCliApplication).container
        val provider = container.providersById.getValue(id)
        var last: InstallEvent? = null
        withTimeout(20 * 60 * 1000L) {
            provider.installer.install().collect {
                last = it
                android.util.Log.i("SkullShell-Agent-Audit", "$id: $it")
            }
        }
        assertTrue(last.toString(), last is InstallEvent.Completed)
        val state = provider.detectState()
        assertFalse(state.toString(), state is ProviderState.Error || state is ProviderState.NotInstalled || state is ProviderState.Incompatible)
        val directory = context.filesDir.resolve("workspaces/agent-audit").apply { mkdirs() }
        val process = provider.launch(ProviderLaunchRequest(directory.absolutePath, listOf("--help")))
        try {
            withTimeout(30_000) {
                val output = StringBuilder()
                process.outputFlow.collect { output.append(it.toString(Charsets.UTF_8)) }
                assertEquals(output.toString(), 0, process.waitForExit())
                assertTrue("No help output for $id", output.isNotBlank())
            }
        } finally { process.destroy() }
    }
}
