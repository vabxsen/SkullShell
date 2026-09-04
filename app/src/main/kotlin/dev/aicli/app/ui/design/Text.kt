package dev.aicli.app.ui.design

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * The one text primitive. Wraps [BasicText] (Foundation) rather than a Material `Text`, and
 * resolves its colour from [SkullTheme] instead of from an ambient content-colour local — this
 * design has few enough ink levels that naming the level at the call site is clearer than
 * inheriting one, and it makes an inverted block (ink fill, [SkullColors.onInk] text) explicit
 * rather than magic.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = SkullTheme.type.body,
    color: Color = SkullTheme.colors.ink,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    align: TextAlign? = null,
    softWrap: Boolean = true,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = color, textAlign = align ?: TextAlign.Unspecified)),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
    )
}

/**
 * The tracked uppercase micro-label used for section headings, buttons, navigation, chips and
 * any key/value key. Uppercasing happens here so callers pass ordinary sentence-case strings
 * and the design stays consistent even when the source text isn't.
 */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SkullTheme.colors.inkMuted,
    style: TextStyle = SkullTheme.type.label,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        align = align,
        softWrap = false,
    )
}
