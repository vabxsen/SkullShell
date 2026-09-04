@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.aicli.app.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Modal(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier,
          actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, modifier = modifier,
        title = { androidx.compose.material3.Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
        confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), content = actions) })
}

@Composable
fun Sheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, dismissOnScrimTap: Boolean = true,
          content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { dismissOnScrimTap || it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = { if (dismissOnScrimTap) onDismiss() }, modifier = modifier, sheetState = state) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = Space.x6, end = Space.x6, bottom = Space.x6), content = content)
    }
}

@Composable
fun Menu(expanded: Boolean, onDismiss: () -> Unit, items: List<MenuItem>) {
    DropdownMenu(expanded, onDismiss, modifier = Modifier.widthIn(min = 200.dp, max = 280.dp)) {
        items.forEach { item -> DropdownMenuItem(text = { androidx.compose.material3.Text(item.label) },
            onClick = { onDismiss(); item.onClick() }) }
    }
}
data class MenuItem(val label: String, val onClick: () -> Unit)
