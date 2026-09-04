package dev.aicli.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aicli.terminal.TerminalBuffer
import dev.aicli.terminal.TerminalView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun committedTextAndHardwareBackspaceAreSentExactlyOnce() {
        val received = StringBuilder()
        compose.setContent {
            TerminalView(TerminalBuffer(80, 24), modifier = Modifier.fillMaxSize(),
                onInput = { received.append(it.toString(Charsets.UTF_8)) })
        }
        val input = compose.onNodeWithTag("terminal-input")
        input.performTextInput("first\n")
        input.performTextInput("secondX")
        compose.runOnIdle { assertEquals("first\rsecondX", received.toString()) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DEL)
        compose.waitUntil(5_000) { received.endsWith("\u007f") }
        input.performTextInput("K\n")
        input.performTextInput("third\n")
        compose.runOnIdle { assertEquals("first\rsecondX\u007fK\rthird\r", received.toString()) }
    }

    @Test fun softKeyboardCanDeleteBeyondItsLocalInputHistory() {
        val received = StringBuilder()
        lateinit var inputView: android.view.View
        compose.setContent {
            inputView = LocalView.current
            TerminalView(TerminalBuffer(80, 24), modifier = Modifier.fillMaxSize(),
                onInput = { received.append(it.toString(Charsets.UTF_8)) })
        }
        val input = compose.onNodeWithTag("terminal-input")
        input.performTextInput("abc")
        compose.runOnIdle {
            val connection = checkNotNull(inputView.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
            repeat(5) { connection.deleteSurroundingText(1, 0) }
        }
        compose.waitUntil(5_000) { received.length == 8 }
        input.performTextInput("next\n")
        compose.runOnIdle { assertEquals("abc" + "\u007f".repeat(5) + "next\r", received.toString()) }
    }

    @Test fun longPastedTextDoesNotReplayAfterHistoryIsTrimmed() {
        val received = StringBuilder()
        compose.setContent {
            TerminalView(TerminalBuffer(80, 24), modifier = Modifier.fillMaxSize(),
                onInput = { received.append(it.toString(Charsets.UTF_8)) })
        }
        val input = compose.onNodeWithTag("terminal-input")
        val pasted = "abc123".repeat(1000)
        input.performTextInput(pasted)
        input.performTextInput("done\n")
        compose.runOnIdle { assertEquals(pasted + "done\r", received.toString()) }
    }
}
