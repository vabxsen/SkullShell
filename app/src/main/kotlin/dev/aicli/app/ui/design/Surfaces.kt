package dev.aicli.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** Tinted icon. The only way glyphs are drawn — nothing in this app renders an untinted asset. */
@Composable
fun Glyph(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = Metrics.glyphMd,
    tint: Color = SkullTheme.colors.ink,
) {
    Image(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/**
 * Click handling plus this design's press feedback, which is a fast fade to [SkullColors.panelHi]
 * rather than a ripple. Material's ripple is a coloured, organically-expanding shape; both of
 * those are wrong here, and the ripple implementation lives in the Material artifacts that this
 * app no longer depends on. Every interactive surface passes `indication = null` and draws its
 * own state, which is also why press feedback stays consistent across custom shapes.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    highlight: Color = SkullTheme.colors.panelHi,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val overlay by animateColorAsState(
        targetValue = if (pressed && enabled) highlight else Color.Transparent,
        animationSpec = tween(durationMillis = if (pressed) 40 else 160),
        label = "press",
    )
    return this
        .background(overlay)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** A 1dp horizontal rule. The structural workhorse: this design separates with lines, not gaps. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = SkullTheme.colors.line) {
    Box(modifier.fillMaxWidth().height(Metrics.hairline).background(color))
}

/** A 1dp vertical rule, for splitting a row into fields. */
@Composable
fun VRule(modifier: Modifier = Modifier, color: Color = SkullTheme.colors.line) {
    Box(modifier.width(Metrics.hairline).background(color))
}

/**
 * A bordered container. There is no elevation anywhere in this app — no shadows, no tonal
 * lift — so a panel is defined entirely by its hairline and a single step of ground colour.
 * [onClick] makes the whole panel a target and adds the standard press wash.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    fill: Color = SkullTheme.colors.panel,
    border: Color = SkullTheme.colors.line,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = Shapes.panel
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .let { if (onClick != null) it.pressable(onClick = onClick) else it }
            .border(Metrics.hairline, border, shape),
        content = content,
    )
}

/**
 * A section heading: tracked uppercase label, then a hairline that runs to the end of the row,
 * then an optional action. The rule is what makes a plain label read as structure — it is this
 * design's substitute for the weight and colour a Material section header would use.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Label(title, color = SkullTheme.colors.inkMuted)
        Rule(Modifier.padding(horizontal = Space.x3).weight(1f))
        action?.invoke(this)
    }
}

/**
 * The large screen title, set in the content rather than in the top bar (the bar carries only a
 * small breadcrumb). Keeping the title in the scroll area gives every screen the same editorial
 * opening — big sans title, hairline, then the content — and leaves the bar itself quiet.
 */
@Composable
fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = SkullTheme.type.display, color = SkullTheme.colors.ink)
        if (subtitle != null) {
            Text(
                subtitle,
                style = SkullTheme.type.body,
                color = SkullTheme.colors.inkMuted,
                modifier = Modifier.padding(top = Space.x2),
            )
        }
        Rule(Modifier.padding(top = Space.x4))
    }
}
