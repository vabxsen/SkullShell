package dev.aicli.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own icon set. Material's icon library is not on the classpath any more, and it
 * would not have fitted anyway.
 *
 * Drawn on a 24-unit grid at a constant [STROKE] weight with **round caps and round joins**, so
 * every corner and every stroke end carries the same curvature as the pills and discs around it.
 * That is what keeps a 1.8-unit line from reading as a hard edge at 18dp: at this weight the
 * join radius is most of what the eye sees of a corner.
 *
 * Status marks are discs rather than squares for the same reason, and small solid marks (menu
 * dots, slider knobs, the dot on an `i`) are circles - a square that small is all corner.
 *
 * Everything here is tinted at draw time by [Glyph], so the baked-in colour is irrelevant.
 */
private const val STROKE = 1.8f

private class Seg(val d: String, val solid: Boolean = false)

/** A filled circle, since SVG path data has no primitive for one. */
private fun dot(cx: Double, cy: Double, r: Double): Seg = Seg(
    "M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy Z",
    solid = true,
)

/** An open circle, as a stroked outline. */
private fun ring(cx: Double, cy: Double, r: Double): Seg = Seg(
    "M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy Z",
)

private fun glyph(name: String, vararg segments: Seg): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        segments.forEach { seg ->
            if (seg.solid) {
                addPath(pathData = addPathNodes(seg.d), fill = SolidColor(Color.Black))
            } else {
                addPath(
                    pathData = addPathNodes(seg.d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }.build()

object Glyphs {
    val Home: ImageVector by lazy {
        glyph("home", Seg("M3.6 10.6 L12 3.6 L20.4 10.6 V20.4 H3.6 Z"), Seg("M9.4 20.4 V14.2 H14.6 V20.4"))
    }
    val Folder: ImageVector by lazy {
        glyph("folder", Seg("M3.4 19.6 V5.4 H9.5 L11.6 8.1 H20.6 V19.6 Z"))
    }
    val FolderExternal: ImageVector by lazy {
        glyph(
            "folderExternal",
            Seg("M3.4 19.6 V5.4 H9.5 L11.6 8.1 H20.6 V19.6 Z"),
            Seg("M9.8 16.2 L16 10.6 M11.8 10.6 H16.2 V14.9"),
        )
    }
    val Terminal: ImageVector by lazy {
        glyph("terminal", Seg("M4.4 6.8 L9.2 12 L4.4 17.2"), Seg("M11.8 17.2 H19.6"))
    }

    /** Four solid dots. An "apps" mark made of squares would be four hard corners at 18dp. */
    val Grid: ImageVector by lazy {
        glyph(
            "grid",
            dot(7.6, 7.6, 3.1),
            dot(16.4, 7.6, 3.1),
            dot(7.6, 16.4, 3.1),
            dot(16.4, 16.4, 3.1),
        )
    }
    val Sliders: ImageVector by lazy {
        glyph(
            "sliders",
            Seg("M3.4 6.4 H8 M13.6 6.4 H20.6 M3.4 12 H14.2 M19.4 12 H20.6 M3.4 17.6 H6.2 M11.6 17.6 H20.6"),
            dot(10.8, 6.4, 2.6),
            dot(16.8, 12.0, 2.6),
            dot(8.8, 17.6, 2.6),
        )
    }
    val Plus: ImageVector by lazy { glyph("plus", Seg("M12 4.8 V19.2 M4.8 12 H19.2")) }
    val Close: ImageVector by lazy { glyph("close", Seg("M6 6 L18 18 M18 6 L6 18")) }
    val Trash: ImageVector by lazy {
        glyph(
            "trash",
            Seg("M3.6 6.6 H20.4"),
            Seg("M9.2 6.6 V3.8 H14.8 V6.6"),
            Seg("M5.8 6.6 V20.2 H18.2 V6.6"),
            Seg("M10 10.8 V16.4 M14 10.8 V16.4"),
        )
    }
    val Refresh: ImageVector by lazy {
        glyph(
            "refresh",
            Seg("M4.6 9.6 A8 8 0 0 1 19 8.4"),
            Seg("M19 3.6 V8.4 H14.2"),
            Seg("M19.4 14.4 A8 8 0 0 1 5 15.6"),
            Seg("M5 20.4 V15.6 H9.8"),
        )
    }
    val ChevronDown: ImageVector by lazy { glyph("chevronDown", Seg("M5.8 9.6 L12 15.8 L18.2 9.6")) }
    val ChevronUp: ImageVector by lazy { glyph("chevronUp", Seg("M5.8 14.4 L12 8.2 L18.2 14.4")) }
    val ChevronLeft: ImageVector by lazy { glyph("chevronLeft", Seg("M14.4 4.2 L6.6 12 L14.4 19.8")) }
    val ArrowRight: ImageVector by lazy { glyph("arrowRight", Seg("M4.4 12 H19"), Seg("M13.4 6.4 L19 12 L13.4 17.6")) }
    val Check: ImageVector by lazy { glyph("check", Seg("M4.6 12.4 L9.4 17.4 L19.4 7")) }
    val CheckCircle: ImageVector by lazy {
        glyph("checkCircle", ring(12.0, 12.0, 8.5), Seg("M7.8 12.2 L10.8 15.2 L16.2 9"))
    }
    val Alert: ImageVector by lazy {
        glyph(
            "alert",
            Seg("M12 3.4 L21.4 20.3 H2.6 Z"),
            Seg("M12 9.6 V14.6"),
            dot(12.0, 17.4, 1.1),
        )
    }
    val ErrorCircle: ImageVector by lazy {
        glyph("errorCircle", ring(12.0, 12.0, 8.5), Seg("M9.2 9.2 L14.8 14.8 M14.8 9.2 L9.2 14.8"))
    }
    val Info: ImageVector by lazy {
        glyph("info", ring(12.0, 12.0, 8.5), Seg("M12 11 V16.4"), dot(12.0, 7.8, 1.1))
    }
    val Clock: ImageVector by lazy {
        glyph("clock", ring(12.0, 12.0, 8.5), Seg("M12 6.8 V12.2 L15.9 14.5"))
    }
    val NoSignal: ImageVector by lazy {
        glyph(
            "noSignal",
            Seg("M4.2 20.2 V16.8 M9.4 20.2 V13 M14.6 20.2 V9.2 M19.8 20.2 V5.6"),
            Seg("M3.4 3.6 L20.6 20.6"),
        )
    }
    val Dots: ImageVector by lazy {
        glyph("dots", dot(12.0, 5.0, 1.5), dot(12.0, 12.0, 1.5), dot(12.0, 19.0, 1.5))
    }
    val Copy: ImageVector by lazy {
        glyph("copy", Seg("M8.8 3.6 H20.4 V15.2 H8.8 Z"), Seg("M15.2 20.4 H3.6 V8.8 H8.8"))
    }
}
