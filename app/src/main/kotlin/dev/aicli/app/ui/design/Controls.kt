package dev.aicli.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * The switch: a fully round track with a round knob, and "on" is a complete inversion of the
 * control rather than a colour change - ink track, ground-coloured knob. Inversion is doing the
 * work a colour would do elsewhere, so the shape is free to be soft without the state becoming
 * ambiguous.
 */
@Composable
fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SkullTheme.colors
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 3.dp,
        animationSpec = tween(160),
        label = "toggleKnob",
    )
    val track by animateColorAsState(
        targetValue = when {
            !enabled -> colors.panelHi
            checked -> colors.ink
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "toggleTrack",
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(Shapes.pill)
            .background(track)
            .border(Metrics.hairline, if (enabled) colors.lineStrong else colors.line, Shapes.pill)
            .pressable(enabled = enabled, highlight = Color.Transparent) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .align(Alignment.CenterStart)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !enabled -> colors.inkFaint
                        checked -> colors.onInk
                        else -> colors.inkMuted
                    },
                ),
        )
    }
}

/** Single-choice mark: a ring with a filled core. */
@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = SkullTheme.colors
    Box(
        modifier = modifier
            .size(20.dp)
            .border(
                width = if (selected) 1.5.dp else Metrics.hairline,
                color = if (selected) colors.ink else colors.lineStrong,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(colors.ink))
        }
    }
}

/**
 * A round-ended track with a round thumb. The thumb carries a ground-coloured ring inside the
 * ink fill so it stays readable where it overlaps the filled part of its own track - the 1-bit
 * equivalent of the shadow a coloured slider would use to separate the two.
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = SkullTheme.colors
    val handleWidth = 20.dp
    val onChange by rememberUpdatedState(onValueChange)
    val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)

    BoxWithConstraints(modifier.fillMaxWidth().height(Metrics.touch)) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val handlePx = with(density) { handleWidth.toPx() }
        val travel = (widthPx - handlePx).coerceAtLeast(1f)
        val setFromX: (Float) -> Unit = { x ->
            val f = ((x - handlePx / 2f) / travel).coerceIn(0f, 1f)
            onChange(valueRange.start + f * range)
        }
        val travelDp = with(density) { (travel * fraction).toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(travel) { detectTapGestures { setFromX(it.x) } }
                .pointerInput(travel) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        setFromX(change.position.x)
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(Shapes.pill)
                    .background(colors.line),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(Shapes.pill)
                    .background(colors.ink),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = travelDp)
                    .size(handleWidth)
                    .clip(CircleShape)
                    .background(colors.ink),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.bg))
            }
        }
    }
}

/**
 * Text entry: a tracked label above, then the value in mono (this app's inputs are names, paths
 * and ids - machine values) inside a round-ended hairline capsule that brightens to
 * [SkullColors.ink] while focused. The capsule matches the buttons it sits above in a dialog,
 * so a form reads as one family of controls rather than as a rule with text on it.
 */
@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
) {
    val colors = SkullTheme.colors
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        targetValue = if (focused) colors.ink else colors.lineStrong,
        animationSpec = tween(140),
        label = "fieldBorder",
    )
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Label(label, color = colors.inkMuted, modifier = Modifier.padding(bottom = Space.x2))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            textStyle = SkullTheme.type.mono.merge(TextStyle(color = colors.ink)),
            cursorBrush = SolidColor(colors.ink),
            singleLine = true,
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Metrics.control)
                        .clip(Shapes.pill)
                        .background(colors.panel)
                        .border(Metrics.hairline, border, Shapes.pill)
                        .padding(horizontal = Space.x5),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = SkullTheme.type.mono, color = colors.inkFaint)
                    }
                    inner()
                }
            },
        )
    }
}

