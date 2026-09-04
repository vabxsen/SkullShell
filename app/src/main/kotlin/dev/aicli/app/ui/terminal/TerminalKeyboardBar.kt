package dev.aicli.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aicli.app.ui.design.KeyCap
import dev.aicli.app.ui.design.Rule
import dev.aicli.app.ui.design.SkullTheme
import dev.aicli.app.ui.design.Space
import dev.aicli.terminal.ESC
import dev.aicli.terminal.arrowKey
import dev.aicli.terminal.csi
import dev.aicli.terminal.ctrlByte

/**
 * On-screen keys a mobile keyboard does not offer. CTRL and ALT are sticky modifiers: tap CTRL,
 * then tap a letter, and the combined control byte is sent rather than two keystrokes - which is
 * why their pressed state has to be visible at a glance, and why [KeyCap] shows it by inverting
 * completely rather than by tinting.
 *
 * This is app chrome rather than terminal behaviour, so it lives in the app module and is themed
 * by the design system; the byte encodings it sends come from
 * [dev.aicli.terminal.TerminalKeys][dev.aicli.terminal.ctrlByte].
 */
@Composable
fun TerminalKeyboardBar(
    modifier: Modifier = Modifier,
    applicationCursorMode: Boolean = false,
    withNavigationBarInset: Boolean = false,
    onSend: (ByteArray) -> Unit,
) {
    var ctrlHeld by remember { mutableStateOf(false) }
    var altHeld by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(SkullTheme.colors.bg)
            .then(if (withNavigationBarInset) Modifier.navigationBarsPadding() else Modifier),
    ) {
        Rule()
        Column(
            modifier = Modifier.padding(horizontal = Space.x3, vertical = Space.x2),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                KeyCap("ESC") { onSend(byteArrayOf(ESC)) }
                KeyCap("TAB") { onSend(byteArrayOf(0x09)) }
                KeyCap("CTRL", active = ctrlHeld) { ctrlHeld = !ctrlHeld }
                KeyCap("ALT", active = altHeld) { altHeld = !altHeld }
                KeyCap("↑", width = 46.dp) { onSend(arrowKey('A', applicationCursorMode)) }
                KeyCap("↓", width = 46.dp) { onSend(arrowKey('B', applicationCursorMode)) }
                KeyCap("←", width = 46.dp) { onSend(arrowKey('D', applicationCursorMode)) }
                KeyCap("→", width = 46.dp) { onSend(arrowKey('C', applicationCursorMode)) }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                KeyCap("HOME") { onSend(csi('H'.code.toByte())) }
                KeyCap("END") { onSend(csi('F'.code.toByte())) }
                KeyCap("PGUP") { onSend(csi('5'.code.toByte(), '~'.code.toByte())) }
                KeyCap("PGDN") { onSend(csi('6'.code.toByte(), '~'.code.toByte())) }
                KeyCap("^C", width = 46.dp) { onSend(byteArrayOf(ctrlByte('C'))) }
                KeyCap("^D", width = 46.dp) { onSend(byteArrayOf(ctrlByte('D'))) }
                KeyCap("^L", width = 46.dp) { onSend(byteArrayOf(ctrlByte('L'))) }
                KeyCap("^Z", width = 46.dp) { onSend(byteArrayOf(ctrlByte('Z'))) }
            }
        }
    }
}
