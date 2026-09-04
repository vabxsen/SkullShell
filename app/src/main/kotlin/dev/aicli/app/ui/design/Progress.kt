package dev.aicli.app.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Indeterminate work. A 90-degree arc sweeping a hairline ring - the smallest shape that still
 * reads as motion at 16dp. Round caps, matching the glyphs and the progress tracks.
 */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = SkullTheme.colors.ink,
    track: Color = SkullTheme.colors.line,
    strokeWidth: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 850, easing = LinearEasing)),
        label = "spinnerAngle",
    )
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * A progress track. Determinate when [fraction] is non-null, otherwise a single round-ended
 * block that traverses the track - one block, not Material's tapering two-part sweep.
 */
@Composable
fun LinearProgress(
    modifier: Modifier = Modifier,
    fraction: Float? = null,
    color: Color = SkullTheme.colors.ink,
    track: Color = SkullTheme.colors.line,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(5.dp).clip(Shapes.pill)) {
        val full = maxWidth
        Box(Modifier.fillMaxWidth().fillMaxHeight().background(track))
        if (fraction != null) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(Shapes.pill)
                    .background(color),
            )
        } else {
            val transition = rememberInfiniteTransition(label = "linear")
            val position by transition.animateFloat(
                initialValue = -0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
                label = "linearPos",
            )
            Box(
                Modifier
                    .offset(x = full * position)
                    .fillMaxWidth(0.35f)
                    .fillMaxHeight()
                    .clip(Shapes.pill)
                    .background(color),
            )
        }
    }
}

/** Centred spinner plus an optional caption - the standard "working on it" body for a screen. */
@Composable
fun LoadingBody(modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner()
            if (label != null) {
                Label(
                    label,
                    color = SkullTheme.colors.inkMuted,
                    modifier = Modifier.padding(top = Space.x4),
                )
            }
        }
    }
}

