package dev.aicli.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aicli.app.ui.design.*
import dev.aicli.terminal.ESC
import dev.aicli.terminal.arrowKey
import dev.aicli.terminal.csi
import dev.aicli.terminal.ctrlByte

/** Direct shortcuts are explicit: each key sends exactly the sequence printed on its cap. */
@Composable
fun TerminalKeyboardBar(modifier: Modifier = Modifier, applicationCursorMode: Boolean = false,
                        withNavigationBarInset: Boolean = false, onSend: (ByteArray) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier.background(SkullTheme.colors.panel)
        .then(if (withNavigationBarInset) Modifier.navigationBarsPadding() else Modifier)) {
        Rule()
        Column(Modifier.padding(Space.x2), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyCap("esc", width = 48.dp) { onSend(byteArrayOf(ESC)) }
                KeyCap("tab", width = 48.dp) { onSend(byteArrayOf(0x09)) }
                KeyCap("^C", width = 48.dp) { onSend(byteArrayOf(ctrlByte('C'))) }
                KeyCap("←", width = 44.dp) { onSend(arrowKey('D', applicationCursorMode)) }
                KeyCap("↓", width = 44.dp) { onSend(arrowKey('B', applicationCursorMode)) }
                KeyCap("↑", width = 44.dp) { onSend(arrowKey('A', applicationCursorMode)) }
                KeyCap("→", width = 44.dp) { onSend(arrowKey('C', applicationCursorMode)) }
                KeyCap(if (expanded) "less" else "more", active = expanded) { expanded = !expanded }
            }
            if (expanded) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyCap("home") { onSend(csi('H'.code.toByte())) }
                KeyCap("end") { onSend(csi('F'.code.toByte())) }
                KeyCap("pg up") { onSend(csi('5'.code.toByte(), '~'.code.toByte())) }
                KeyCap("pg dn") { onSend(csi('6'.code.toByte(), '~'.code.toByte())) }
                KeyCap("^D", width = 48.dp) { onSend(byteArrayOf(ctrlByte('D'))) }
                KeyCap("^L", width = 48.dp) { onSend(byteArrayOf(ctrlByte('L'))) }
                KeyCap("^Z", width = 48.dp) { onSend(byteArrayOf(ctrlByte('Z'))) }
            }
        }
    }
}
