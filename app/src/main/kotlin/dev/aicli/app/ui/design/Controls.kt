package dev.aicli.app.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Toggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(checked, onCheckedChange, modifier.heightIn(min = Metrics.touch), enabled = enabled)
}
@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) { RadioButton(selected, onClick = null, modifier = modifier) }
@Composable
fun Slider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier) {
    androidx.compose.material3.Slider(value, onValueChange, modifier.heightIn(min = Metrics.touch), valueRange = valueRange)
}
@Composable
fun InputField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, placeholder: String? = null) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), singleLine = true,
        label = label?.let { { androidx.compose.material3.Text(it) } },
        placeholder = placeholder?.let { { androidx.compose.material3.Text(it) } }, shape = MaterialTheme.shapes.medium)
}
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), singleLine = true,
        placeholder = { androidx.compose.material3.Text("Search projects") },
        leadingIcon = { Icon(Glyphs.Search, null) },
        trailingIcon = if (value.isNotEmpty()) { { IconAction(Glyphs.Close, "Clear search", { onValueChange("") }) } } else null,
        shape = Shapes.pill, colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = scheme.surfaceContainerHigh, focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedBorderColor = Color.Transparent, focusedBorderColor = scheme.primary))
}
