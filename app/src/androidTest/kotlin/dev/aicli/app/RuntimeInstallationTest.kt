package dev.aicli.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aicli.runtime.bootstrap.BootstrapManager
import dev.aicli.runtime.bootstrap.BootstrapState
import dev.aicli.runtime.health.RuntimeHealthChecker
import dev.aicli.runtime.health.CheckStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in: adb am instrument -e network true -e class ...RuntimeInstallationTest */
@RunWith(AndroidJUnit4::class)
class RuntimeInstallationTest {
    @Test fun installAndVerifyRuntimeAgainstLiveRepositories() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("network") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var last: BootstrapState? = null
        BootstrapManager(context).install().collect {
            last = it
            android.util.Log.i("SkullShell-Audit", it.toString())
        }
        assertTrue(last.toString(), last is BootstrapState.Ready)
        val health = RuntimeHealthChecker(context).runAll()
        assertTrue(health.joinToString("\n"), health.all { it.status == CheckStatus.PASS })
    }
}
