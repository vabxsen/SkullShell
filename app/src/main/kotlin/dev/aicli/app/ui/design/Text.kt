package dev.aicli.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun Text(text: String, modifier: Modifier = Modifier, style: TextStyle = SkullTheme.type.body,
         color: Color = SkullTheme.colors.ink, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip,
         align: TextAlign? = null, softWrap: Boolean = true, minLines: Int = 1) {
    androidx.compose.material3.Text(text, modifier, color = color, style = style, textAlign = align,
        maxLines = maxLines, minLines = minLines, overflow = overflow, softWrap = softWrap)
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = SkullTheme.colors.inkMuted,
          style: TextStyle = SkullTheme.type.label, maxLines: Int = 1, align: TextAlign? = null) {
    Text(text, modifier, style, color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, align = align, softWrap = false)
}
