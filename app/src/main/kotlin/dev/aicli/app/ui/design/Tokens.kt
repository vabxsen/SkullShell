package dev.aicli.app.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The whole palette, and there is no other one. SkullShell is monochrome by design, not by
 * omission: hue carries no meaning anywhere in this UI, so every distinction that a coloured
 * design would encode as "blue = primary, red = danger" is encoded here as a position on a
 * single black-to-white ramp, plus inversion for the loudest states.
 *
 * The ramp is deliberately short. Four ink levels and four ground levels are enough to build a
 * complete hierarchy, and keeping it short is what stops a greyscale UI from turning into
 * indistinguishable mush — every step is a visible step.
 *
 * [ink] doubles as the "filled" surface (see [onInk]): a solid ink block with ground-coloured
 * text on it is this design's equivalent of a saturated accent, and it is spent sparingly —
 * one primary action per screen, error chips, and the selected state of a modifier key.
 */
@Immutable
data class SkullColors(
    /** The page itself. Pure black in dark, pure white in light — no "almost". */
    val bg: Color,
    /** A panel/card sitting on [bg]. One barely-perceptible step, read via its border. */
    val panel: Color,
    /** Nested or pressed surface — the press feedback that replaces the Material ripple. */
    val panelHi: Color,
    /** Hairline rules, panel borders, dividers. The main structural device in this design. */
    val line: Color,
    /** A hairline that needs to be noticed: focus, selection, an emphasised container. */
    val lineStrong: Color,
    /** Primary text and iconography. */
    val ink: Color,
    /** Secondary text: descriptions, metadata, unselected navigation. */
    val inkMuted: Color,
    /** Tertiary text: disabled controls, unlit dot-matrix cells, decorative marks. */
    val inkFaint: Color,
    /** Text/glyphs drawn on top of an [ink] fill. Always the ground colour — never grey. */
    val onInk: Color,
    /** Behind modals. Heavier than a Material scrim because there is no elevation shadow. */
    val scrim: Color,
    /** True when this is the dark scheme — drives system-bar icon appearance, nothing visual. */
    val isDark: Boolean,
)

val DarkInk = SkullColors(
    bg = Color(0xFF000000),
    panel = Color(0xFF0B0B0B),
    panelHi = Color(0xFF161616),
    line = Color(0xFF232323),
    lineStrong = Color(0xFF3D3D3D),
    ink = Color(0xFFFFFFFF),
    inkMuted = Color(0xFF9B9B9B),
    inkFaint = Color(0xFF5A5A5A),
    onInk = Color(0xFF000000),
    scrim = Color(0xCC000000),
    isDark = true,
)

val LightInk = SkullColors(
    bg = Color(0xFFFFFFFF),
    panel = Color(0xFFFAFAFA),
    panelHi = Color(0xFFF0F0F0),
    line = Color(0xFFE2E2E2),
    lineStrong = Color(0xFFBDBDBD),
    ink = Color(0xFF000000),
    inkMuted = Color(0xFF6A6A6A),
    inkFaint = Color(0xFFA6A6A6),
    onInk = Color(0xFFFFFFFF),
    scrim = Color(0x99000000),
    isDark = false,
)

/** 4dp-based spacing scale. Named by step, not by intent, so nothing drifts into "space16ish". */
object Space {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x10 = 40.dp
    val x12 = 48.dp
    val x16 = 64.dp
}

/**
 * The shape scale. Curvature here is a deliberate counterweight to the hairline grid: the rules
 * that structure a page are dead straight, so everything that *sits on* that grid is rounded,
 * and the contrast between the two is what stops the layout reading as a spreadsheet.
 *
 * Radius scales with the size of the thing, which is what keeps it from looking arbitrary — a
 * chip and a bottom sheet rounded by the same number look wrong together. Anything whose height
 * is fixed and small ([pill]: buttons, chips, toggles) goes fully round rather than picking a
 * value, so its curvature is defined by its own height and never has to be re-tuned.
 */
object Shapes {
    /** Fully round ends. Buttons, chips, toggles, progress tracks. */
    val pill = RoundedCornerShape(percent = 50)

    /** Small surfaces that still need corners: menu, key caps, glyph containers. */
    val small = RoundedCornerShape(12.dp)

    /** Panels and fields - the workhorse. */
    val panel = RoundedCornerShape(18.dp)

    /** Modals: larger surface, larger radius. */
    val modal = RoundedCornerShape(26.dp)

    /** A bottom sheet is only rounded where it leaves the screen edge. */
    val sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
}

/** Fixed measurements. Corner radii live in [Shapes], not here. */
object Metrics {
    val hairline = 1.dp

    val glyphSm = 14.dp
    val glyphMd = 18.dp
    val glyphLg = 22.dp
    val glyphXl = 34.dp

    /** Height of a button/field. Below the 48dp touch minimum on purpose — the components that
     *  use it add their own transparent touch padding rather than inflating the drawn box. */
    val control = 44.dp
    val touch = 48.dp

    val gutter = 20.dp
    val topBar = 52.dp
    val navBar = 58.dp
    val rail = 84.dp
}
