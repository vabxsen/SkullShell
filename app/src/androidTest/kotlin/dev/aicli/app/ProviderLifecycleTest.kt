package dev.aicli.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aicli.app.update.UpdateCheckResult
import dev.aicli.provider.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = (context.applicationContext as AiCliApplication).container

    @Test fun agentUninstallCanBeReversedByReinstalling() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("network") == "true")
        val provider = container.providersById.getValue("codex_cli")
        provider.installer.uninstall().collect { assertFalse(it is InstallEvent.Failed) }
        assertEquals(ProviderState.NotInstalled, provider.detectState())
        var last: InstallEvent? = null
        provider.installer.install().collect { last = it }
        assertEquals(last.toString(), InstallEvent.Completed, last)
    }

    @Test fun installedAgentsCanOpenTheirLoginPromptsAndCancel() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("network") == "true")
        for (provider in container.providers) {
            val status = provider.auth.currentState()
            assertFalse("${provider.id}: $status", status is AuthState.Error)
            // Never sign out an account if this test is reused on an already authenticated device.
            if (status == AuthState.SignedIn) continue
            val process = provider.auth.startLogin()
            var bytesReceived = 0
            val output = StringBuilder()
            val reader = launch { process.outputFlow.collect { bytesReceived += it.size; output.append(it.toString(Charsets.UTF_8)) } }
            try {
                withTimeout(20_000) { while (bytesReceived == 0) delay(100) }
                // Give startup failures time to surface instead of mistaking loader warnings for a prompt.
                delay(3000)
                val exit = withTimeoutOrNull(100) { process.waitForExit() }
                assertTrue("${provider.id} login exited with $exit: ${dev.aicli.core.logging.AppLog.redact(output.toString().takeLast(2000))}", exit == null || exit == 0)
            } finally {
                process.destroy()
                reader.cancelAndJoin()
            }
        }
    }

    @Test fun appUpdateCheckReturnsARealResultAndRejectsInvalidApks() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("network") == "true")
        val result = container.appUpdateManager.checkForUpdate()
        assertFalse(result.toString(), result is UpdateCheckResult.Failed)
        val invalid = context.cacheDir.resolve("audit-invalid.apk").apply { writeText("not an APK") }
        try {
            try { container.appUpdateManager.validateUpdate(invalid); fail("Invalid APK accepted") }
            catch (_: IllegalStateException) { }
        } finally { invalid.delete() }
        Unit
    }
}
