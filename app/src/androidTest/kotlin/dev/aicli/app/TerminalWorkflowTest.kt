package dev.aicli.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aicli.app.data.SessionRunState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun typingEditingRecreationAndExitUseTheSameRealSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = (context.applicationContext as AiCliApplication).container.sessionManager
        compose.onNodeWithText("New terminal").performClick()
        compose.waitUntil(15_000) { manager.runningCount.value == 1 }
        val session = manager.sessions.value.single()
        val controller = manager.controllerFor(session.id)!!
        fun screen(): String = (0 until controller.buffer.rows).joinToString("\n") { row ->
            controller.buffer.snapshotRow(row).joinToString("") { String(Character.toChars(it.codepoint)) }
        }
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("terminal-input").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("terminal-input").performTextInput("printf 'INPUT_%s\\n' OK\n")
            compose.waitUntil(10_000) { screen().contains("INPUT_OK") }
            compose.onNodeWithTag("terminal-input").performTextInput("printf BACKSPACE_OX")
            // Exercise a physical DEL event through the app's input connection.
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DEL)
            try { compose.waitUntil(10_000) { screen().contains("printf BACKSPACE_O") && !screen().contains("BACKSPACE_OX") } }
            catch (e: Throwable) { throw AssertionError("Terminal after DEL: ${screen()}\nRaw: ${controller.outputTail.value}", e) }
            compose.onNodeWithTag("terminal-input").performTextInput("K\n")
            try { compose.waitUntil(10_000) { screen().contains("BACKSPACE_OK") } }
            catch (e: Throwable) { throw AssertionError("Terminal after edited command: ${screen()}", e) }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("terminal-input").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, manager.runningCount.value)
            assertSame(controller, manager.controllerFor(session.id))
            compose.onNodeWithTag("terminal-input").performTextInput("exit 3\n")
            compose.waitUntil(10_000) { controller.runState.value == SessionRunState.EXITED && manager.runningCount.value == 0 }
            assertEquals(3, controller.exitCode.value)
        } finally {
            compose.runOnIdle { runBlocking { manager.closeSession(session.id) } }
        }
    }
}
