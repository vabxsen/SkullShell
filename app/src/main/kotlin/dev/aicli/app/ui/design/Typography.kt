package dev.aicli.app.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Two families, used for two different jobs, and never interchangeably:
 *
 *  - **Sans** ([title], [heading], [body], [bodySm]) for anything a person wrote — screen
 *    titles, descriptions, error prose.
 *  - **Mono** ([label], [labelSm], [mono], [monoSm]) for anything a machine produced or that
 *    labels a machine: section headings, buttons, navigation, versions, paths, ids, log lines.
 *
 * That split is the reason this reads as an instrument rather than a form. [label] in
 * particular — uppercase, 600 weight, wide positive tracking — is the signature of the whole
 * design and does most of the work that colour would otherwise do; it is paired with a hairline
 * rule in [SectionHeader] and used for every button and nav item.
 *
 * Uppercasing is applied by the components, not baked in here, so a [label] style can still be
 * used verbatim where the source string is already cased correctly.
 */
@Immutable
data class SkullType(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodySm: TextStyle,
    val label: TextStyle,
    val labelSm: TextStyle,
    val mono: TextStyle,
    val monoSm: TextStyle,
)

// Compose adds font-specific padding above/below a line by default, which throws off the tight
// optical alignment this layout depends on (a label centred against a 1px rule, a glyph beside
// a caption). Turned off globally, with line height trimmed at both ends instead.
private val Tight = PlatformTextStyle(includeFontPadding = false)
private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun sans(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    tracking: Double,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    platformStyle = Tight,
    lineHeightStyle = TrimBoth,
)

private fun mono(
    size: Double,
    weight: FontWeight,
    lineHeight: Int,
    tracking: Double,
) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    platformStyle = Tight,
    lineHeightStyle = TrimBoth,
)

val SkullTypography = SkullType(
    // Negative tracking on the large sizes only: it tightens display text that would otherwise
    // look loose, and would smear anything small.
    display = sans(30, FontWeight.Medium, 34, -0.6),
    title = sans(20, FontWeight.Medium, 26, -0.2),
    heading = sans(15, FontWeight.SemiBold, 20, 0.0),
    body = sans(14, FontWeight.Normal, 21, 0.0),
    bodySm = sans(12, FontWeight.Normal, 18, 0.1),
    label = mono(11.0, FontWeight.SemiBold, 14, 1.5),
    labelSm = mono(9.5, FontWeight.SemiBold, 12, 1.2),
    mono = mono(12.5, FontWeight.Normal, 18, 0.0),
    monoSm = mono(11.0, FontWeight.Normal, 16, 0.0),
)
