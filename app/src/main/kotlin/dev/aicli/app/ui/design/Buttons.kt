package dev.aicli.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Three button weights, and the hierarchy between them is ground-vs-ink rather than colour:
 *
 *  - [PrimaryButton] is an ink block with ground-coloured text. It is the only inverted element
 *    on a typical screen, which is exactly why it reads as the primary action - spend more than
 *    one per screen and the effect is gone.
 *  - [OutlineButton] is a hairline box, matching the panels around it.
 *  - [GhostButton] is a bare label, for the action you are allowed to ignore.
 *
 * All three set their label in tracked uppercase mono, so a row of buttons lines up with the
 * section headers and nav labels rather than looking like a separate widget family.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: ImageVector? = null,
) {
    val colors = SkullTheme.colors
    ButtonSurface(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        fill = { pressed ->
            when {
                !enabled -> colors.panelHi
                pressed -> colors.inkMuted
                else -> colors.ink
            }
        },
        border = if (enabled) Color.Transparent else colors.line,
        content = if (enabled) colors.onInk else colors.inkFaint,
        label = label,
        glyph = glyph,
    )
}

@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: ImageVector? = null,
) {
    val colors = SkullTheme.colors
    ButtonSurface(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        fill = { pressed -> if (pressed && enabled) colors.panelHi else Color.Transparent },
        border = if (enabled) colors.lineStrong else colors.line,
        content = if (enabled) colors.ink else colors.inkFaint,
        label = label,
        glyph = glyph,
    )
}

@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: ImageVector? = null,
) {
    val colors = SkullTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> colors.inkFaint
            pressed -> colors.ink
            else -> colors.inkMuted
        },
        animationSpec = tween(120),
        label = "ghost",
    )
    Row(
        modifier = modifier
            .height(Metrics.control)
            .clip(Shapes.pill)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            Glyph(glyph, null, size = Metrics.glyphSm, tint = content, modifier = Modifier.padding(end = Space.x2))
        }
        Label(label, color = content)
    }
}

@Composable
private fun ButtonSurface(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    fill: (pressed: Boolean) -> Color,
    border: Color,
    content: Color,
    label: String,
    glyph: ImageVector?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = Shapes.pill
    val background by animateColorAsState(
        targetValue = fill(pressed),
        animationSpec = tween(if (pressed) 40 else 160),
        label = "buttonFill",
    )

    Row(
        modifier = modifier
            .height(Metrics.control)
            .defaultMinSize(minWidth = Metrics.control)
            .clip(shape)
            .background(background)
            .border(Metrics.hairline, border, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.x5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            Glyph(glyph, null, size = Metrics.glyphSm, tint = content, modifier = Modifier.padding(end = Space.x2))
        }
        Label(label, color = content)
    }
}

/**
 * A bare glyph target - top-bar actions, row affordances, overflow. The drawn glyph stays small
 * while the touch area is padded out to [Metrics.touch], so the visual rhythm of a toolbar is
 * not dictated by accessibility minimums.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = SkullTheme.colors.inkMuted,
    size: Dp = Metrics.glyphMd,
) {
    Box(
        modifier = modifier
            .size(Metrics.touch)
            .clip(CircleShape)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(icon, contentDescription, size = size, tint = if (enabled) tint else SkullTheme.colors.inkFaint)
    }
}

/**
 * The terminal key-cap: a fixed-width hairline box whose *active* state is a full inversion.
 * Sticky modifiers (CTRL/ALT) have to be unmistakable at a glance while typing, and inversion is
 * the loudest signal a monochrome design has.
 */
@Composable
fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    width: Dp = 58.dp,
    onClick: () -> Unit,
) {
    val colors = SkullTheme.colors
    val shape = Shapes.small
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = when {
            active -> colors.ink
            pressed -> colors.panelHi
            else -> colors.panel
        },
        animationSpec = tween(90),
        label = "keycap",
    )
    Box(
        modifier = modifier
            .size(width = width, height = 42.dp)
            .clip(shape)
            .background(fill)
            .border(Metrics.hairline, if (active) colors.ink else colors.line, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = SkullTheme.type.label,
            color = if (active) colors.onInk else colors.ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}
