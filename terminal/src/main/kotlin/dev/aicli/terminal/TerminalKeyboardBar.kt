package dev.aicli.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Maps a Ctrl+<letter> combination to its C0 control byte (the standard `char & 0x1f`). */
fun ctrlByte(letter: Char): Byte = (letter.uppercaseChar().code and 0x1f).toByte()

private const val ESC = 0x1B.toByte()

private fun csi(vararg extra: Byte): ByteArray = byteArrayOf(ESC, '['.code.toByte(), *extra)
private fun ss3(letter: Char): ByteArray = byteArrayOf(ESC, 'O'.code.toByte(), letter.code.toByte())

/**
 * On-screen shortcut bar for keys/combinations mobile keyboards don't offer directly. [ctrlHeld]
 * is exposed by the caller so a follow-up physical-keyboard or on-screen key press can be
 * combined with Ctrl (sticky-modifier pattern): tap CTRL, then tap a letter, and this composable
 * sends the combined control byte instead of two separate keystrokes.
 */
@Composable
fun TerminalKeyboardBar(
    modifier: Modifier = Modifier,
    applicationCursorMode: Boolean = false,
    onSend: (ByteArray) -> Unit,
) {
    var ctrlHeld by remember { mutableStateOf(false) }
    var altHeld by remember { mutableStateOf(false) }

    fun sendKey(bytes: ByteArray) {
        onSend(bytes)
    }

    fun arrow(letter: Char) {
        sendKey(if (applicationCursorMode) ss3(letter) else csi(letter.code.toByte()))
    }

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyButton("ESC") { sendKey(byteArrayOf(ESC)) }
        KeyButton("TAB") { sendKey(byteArrayOf(0x09)) }
        KeyButton("CTRL", active = ctrlHeld) { ctrlHeld = !ctrlHeld }
        KeyButton("ALT", active = altHeld) { altHeld = !altHeld }
        KeyButton("↑") { arrow('A') }
        KeyButton("↓") { arrow('B') }
        KeyButton("→") { arrow('C') }
        KeyButton("←") { arrow('D') }
        KeyButton("HOME") { sendKey(csi('H'.code.toByte())) }
        KeyButton("END") { sendKey(csi('F'.code.toByte())) }
        KeyButton("PGUP") { sendKey(csi('5'.code.toByte(), '~'.code.toByte())) }
        KeyButton("PGDN") { sendKey(csi('6'.code.toByte(), '~'.code.toByte())) }
        KeyButton("^C") { sendKey(byteArrayOf(ctrlByte('C'))) }
        KeyButton("^D") { sendKey(byteArrayOf(ctrlByte('D'))) }
        KeyButton("^L") { sendKey(byteArrayOf(ctrlByte('L'))) }
        KeyButton("^Z") { sendKey(byteArrayOf(ctrlByte('Z'))) }
    }
}

/**
 * Combines a plain printable/text-key press with any currently sticky CTRL/ALT modifiers from
 * [TerminalKeyboardBar]. Callers (the terminal screen's text-input handling) route ordinary IME
 * key events through this when the on-screen CTRL toggle is active, so a physical- or soft-
 * keyboard letter typed right after tapping CTRL produces the control byte instead of the letter.
 */
fun combineWithModifiers(char: Char, ctrlHeld: Boolean, altHeld: Boolean): ByteArray {
    val base = if (ctrlHeld && char.isLetter()) byteArrayOf(ctrlByte(char)) else char.toString().toByteArray(Charsets.UTF_8)
    return if (altHeld) byteArrayOf(ESC) + base else base
}

@Composable
private fun KeyButton(label: String, active: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(width = 56.dp, height = 48.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        shape = MaterialTheme.shapes.small,
        colors = if (active) {
            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        )
    }
}
