package dev.aicli.terminal

/** Text attribute flags, packed as a bitset so a row of cells stays cheap to allocate/copy. */
object CellFlags {
    const val BOLD = 1 shl 0
    const val ITALIC = 1 shl 1
    const val UNDERLINE = 1 shl 2
    const val STRIKETHROUGH = 1 shl 3
    const val INVERSE = 1 shl 4
    const val DIM = 1 shl 5
    const val BLINK = 1 shl 6
    const val INVISIBLE = 1 shl 7
}

/**
 * One terminal grid cell. [codepoint] is a full Unicode code point (not a UTF-16 Char), so
 * astral-plane characters (emoji, etc.) are represented correctly in a single cell.
 * [continuation] marks the trailing cell of a wide (CJK/emoji) glyph so the renderer skips
 * drawing it directly but still reserves the column.
 */
data class TerminalCell(
    val codepoint: Int = ' '.code,
    val fg: TerminalColor = TerminalColor.Default,
    val bg: TerminalColor = TerminalColor.Default,
    val flags: Int = 0,
    val continuation: Boolean = false,
) {
    fun hasFlag(flag: Int) = (flags and flag) != 0

    companion object {
        val BLANK = TerminalCell()
    }
}

/** The "current pen": SGR state applied to newly-written characters. */
data class PenState(
    val fg: TerminalColor = TerminalColor.Default,
    val bg: TerminalColor = TerminalColor.Default,
    val flags: Int = 0,
) {
    fun toCell(codepoint: Int, continuation: Boolean = false) =
        TerminalCell(codepoint, fg, bg, flags, continuation)
}
