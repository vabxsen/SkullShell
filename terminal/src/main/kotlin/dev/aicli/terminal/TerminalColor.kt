package dev.aicli.terminal

/**
 * ANSI color model: 16 standard colors, the 256-color palette (6x6x6 cube + grayscale ramp),
 * and 24-bit truecolor. Kept free of any Android/Compose dependency so it's plain-JVM testable;
 * conversion to a UI color type happens in the renderer layer, not here.
 */
sealed class TerminalColor {
    data class Indexed(val index: Int) : TerminalColor()
    data class Rgb(val r: Int, val g: Int, val b: Int) : TerminalColor()
    object Default : TerminalColor()

    companion object {
        val STANDARD_16 = intArrayOf(
            0x000000, 0x800000, 0x008000, 0x808000, 0x000080, 0x800080, 0x008080, 0xc0c0c0,
            0x808080, 0xff0000, 0x00ff00, 0xffff00, 0x0000ff, 0xff00ff, 0x00ffff, 0xffffff,
        )

        /** Resolves any [TerminalColor] to a packed 0xRRGGBB int, per the xterm 256-color formula. */
        fun resolveRgb(color: TerminalColor, defaultRgb: Int): Int = when (color) {
            is Default -> defaultRgb
            is Rgb -> (color.r shl 16) or (color.g shl 8) or color.b
            is Indexed -> resolveIndexed(color.index)
        }

        private fun resolveIndexed(index: Int): Int = when {
            index < 16 -> STANDARD_16[index]
            index in 16..231 -> {
                val i = index - 16
                val r = i / 36
                val g = (i % 36) / 6
                val b = i % 6
                fun ramp(v: Int) = if (v == 0) 0 else 55 + v * 40
                (ramp(r) shl 16) or (ramp(g) shl 8) or ramp(b)
            }
            index in 232..255 -> {
                val level = 8 + (index - 232) * 10
                (level shl 16) or (level shl 8) or level
            }
            else -> 0xffffff
        }
    }
}
