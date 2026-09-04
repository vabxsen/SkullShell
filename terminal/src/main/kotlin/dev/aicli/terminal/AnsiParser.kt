package dev.aicli.terminal

/**
 * Incremental VT100/ANSI/xterm parser: a small state machine (Ground / Escape / CSI / OSC /
 * DCS-skip) modeled after the classic VT500 parser, feeding a [TerminalBuffer]. It is fed raw
 * bytes as they arrive from the PTY, which means:
 *  - an escape sequence can be split across two [feed] calls (state lives in instance fields,
 *    not locals, so this resumes correctly)
 *  - a multi-byte UTF-8 codepoint can be split across two [feed] calls (same treatment, see
 *    [decodeUtf8Byte])
 *
 * Framework-free (no Android/Compose dependency) so it's plain-JVM unit testable.
 */
class AnsiParser(private val buffer: TerminalBuffer) {

    private enum class State { GROUND, ESCAPE, CSI, OSC, DCS_SKIP }

    private var state = State.GROUND
    private val paramBuilder = StringBuilder()
    private val intermediates = StringBuilder()
    private var csiPrivate = false

    private val oscBuilder = StringBuilder()
    private var oscEscPending = false
    private var dcsEscPending = false

    // Resumable UTF-8 decode state.
    private var utf8Remaining = 0
    private var utf8Value = 0

    var onTitleChange: ((String) -> Unit)? = null
    var onBell: (() -> Unit)? = null
    var onResponse: ((ByteArray) -> Unit)? = null

    fun feed(bytes: ByteArray, len: Int = bytes.size) {
        require(len in 0..bytes.size)
        synchronized(buffer) {
            for (i in 0 until len) feedByte(bytes[i].toInt() and 0xFF)
        }
    }

    private fun feedByte(b: Int) {
        when (state) {
            State.GROUND -> handleGround(b)
            State.ESCAPE -> handleEscape(b)
            State.CSI -> handleCsi(b)
            State.OSC -> handleOsc(b)
            State.DCS_SKIP -> handleDcsSkip(b)
        }
    }

    private fun handleGround(b: Int) {
        when (b) {
            0x1B -> state = State.ESCAPE
            0x0D -> buffer.carriageReturn()
            0x0A, 0x0B, 0x0C -> buffer.lineFeed()
            0x08 -> buffer.backspace()
            0x09 -> buffer.tab()
            0x07 -> onBell?.invoke()
            else -> if (b >= 0x20) decodeUtf8Byte(b) // other C0 controls are ignored
        }
    }

    private fun decodeUtf8Byte(b: Int) {
        if (utf8Remaining == 0) {
            when {
                b and 0x80 == 0x00 -> buffer.putCodepoint(b)
                b and 0xE0 == 0xC0 -> { utf8Value = b and 0x1F; utf8Remaining = 1 }
                b and 0xF0 == 0xE0 -> { utf8Value = b and 0x0F; utf8Remaining = 2 }
                b and 0xF8 == 0xF0 -> { utf8Value = b and 0x07; utf8Remaining = 3 }
                else -> { /* invalid leading byte: drop */ }
            }
        } else if (b and 0xC0 == 0x80) {
            utf8Value = (utf8Value shl 6) or (b and 0x3F)
            utf8Remaining--
            if (utf8Remaining == 0) buffer.putCodepoint(utf8Value)
        } else {
            // Invalid continuation byte: abandon the in-progress codepoint and reprocess this
            // byte as a fresh lead byte instead of desyncing the whole stream.
            utf8Remaining = 0
            decodeUtf8Byte(b)
        }
    }

    private fun handleEscape(b: Int) {
        val c = b.toChar()
        when (c) {
            '[' -> {
                state = State.CSI
                paramBuilder.clear()
                intermediates.clear()
                csiPrivate = false
                return
            }
            ']' -> {
                state = State.OSC
                oscBuilder.clear()
                oscEscPending = false
                return
            }
            'P', 'X', '^', '_' -> {
                state = State.DCS_SKIP
                dcsEscPending = false
                return
            }
            '7' -> buffer.saveCursor()
            '8' -> buffer.restoreCursor()
            'D' -> buffer.lineFeed()
            'E' -> { buffer.carriageReturn(); buffer.lineFeed() }
            'M' -> if (buffer.cursorRow > 0) buffer.moveCursorRelative(-1, 0) else buffer.scrollDownInRegion(1)
            else -> { /* unsupported single-char escape: ignore */ }
        }
        state = State.GROUND
    }

    private fun handleCsi(b: Int) {
        if (paramBuilder.length + intermediates.length >= 256) {
            state = State.GROUND
            return
        }
        when {
            b == '?'.code && paramBuilder.isEmpty() && intermediates.isEmpty() -> csiPrivate = true
            b in 0x30..0x3F -> paramBuilder.append(b.toChar())
            b in 0x20..0x2F -> intermediates.append(b.toChar())
            b in 0x40..0x7E -> {
                dispatchCsi(b.toChar())
                state = State.GROUND
            }
            else -> state = State.GROUND
        }
    }

    private fun params(): List<Int?> {
        if (paramBuilder.isEmpty()) return emptyList()
        return paramBuilder.split(';').map { it.toIntOrNull() }
    }

    private fun dispatchCsi(final: Char) {
        val p = params()
        fun p1(idx: Int = 0, default: Int = 1): Int {
            val v = p.getOrNull(idx)
            return if (v == null || v == 0) default else v
        }
        when (final) {
            'A' -> buffer.moveCursorRelative(-p1(), 0)
            'B' -> buffer.moveCursorRelative(p1(), 0)
            'C' -> buffer.moveCursorRelative(0, p1())
            'D' -> buffer.moveCursorRelative(0, -p1())
            'E' -> buffer.moveCursor(buffer.cursorRow + p1(), 0)
            'F' -> buffer.moveCursor(buffer.cursorRow - p1(), 0)
            'G', '`' -> buffer.moveCursor(buffer.cursorRow, p1() - 1)
            'd' -> buffer.moveCursor(p1() - 1, buffer.cursorCol)
            '@' -> buffer.insertCharacters(p1())
            'P' -> buffer.deleteCharacters(p1())
            'X' -> buffer.eraseCharacters(p1())
            'n' -> when (p.getOrNull(0)) {
                5 -> onResponse?.invoke("\u001b[0n".toByteArray())
                6 -> onResponse?.invoke("\u001b[${buffer.cursorRow + 1};${buffer.cursorCol + 1}R".toByteArray())
            }
            'c' -> onResponse?.invoke("\u001b[?1;2c".toByteArray())
            'H', 'f' -> {
                val row = (p.getOrNull(0) ?: 1).coerceAtLeast(1) - 1
                val col = (p.getOrNull(1) ?: 1).coerceAtLeast(1) - 1
                buffer.moveCursor(row, col)
            }
            's' -> buffer.saveCursor()
            'u' -> buffer.restoreCursor()
            'J' -> buffer.eraseInDisplay(p.getOrNull(0) ?: 0)
            'K' -> buffer.eraseInLine(p.getOrNull(0) ?: 0)
            'm' -> applySgr(p)
            'r' -> {
                val top = (p.getOrNull(0) ?: 1) - 1
                val bottom = (p.getOrNull(1) ?: buffer.rows) - 1
                buffer.setScrollRegion(top, bottom)
            }
            'S' -> buffer.scrollUpInRegion(p1())
            'T' -> buffer.scrollDownInRegion(p1())
            'L' -> buffer.insertLines(p1())
            'M' -> buffer.deleteLines(p1())
            'h' -> setMode(p, true)
            'l' -> setMode(p, false)
            else -> { /* unsupported final byte: ignore, don't desync */ }
        }
    }

    private fun setMode(codes: List<Int?>, enable: Boolean) {
        if (!csiPrivate) return // only DEC private modes (`ESC[?...h/l`) are implemented
        for (code in codes) {
            when (code) {
                1 -> buffer.setApplicationCursorMode(enable)
                7 -> buffer.setAutoWrap(enable)
                25 -> buffer.setCursorVisible(enable)
                47, 1047, 1049 -> buffer.setAlternateScreen(enable)
                2004 -> buffer.setBracketedPaste(enable)
                else -> { /* unimplemented DEC private mode: ignore */ }
            }
        }
    }

    private fun applySgr(paramsList: List<Int?>) {
        val p = paramsList.ifEmpty { listOf(0) }
        var pen = buffer.pen
        var i = 0
        while (i < p.size) {
            when (val code = p[i] ?: 0) {
                0 -> pen = PenState()
                1 -> pen = pen.copy(flags = pen.flags or CellFlags.BOLD)
                2 -> pen = pen.copy(flags = pen.flags or CellFlags.DIM)
                3 -> pen = pen.copy(flags = pen.flags or CellFlags.ITALIC)
                4 -> pen = pen.copy(flags = pen.flags or CellFlags.UNDERLINE)
                5, 6 -> pen = pen.copy(flags = pen.flags or CellFlags.BLINK)
                7 -> pen = pen.copy(flags = pen.flags or CellFlags.INVERSE)
                8 -> pen = pen.copy(flags = pen.flags or CellFlags.INVISIBLE)
                9 -> pen = pen.copy(flags = pen.flags or CellFlags.STRIKETHROUGH)
                22 -> pen = pen.copy(flags = pen.flags and (CellFlags.BOLD or CellFlags.DIM).inv())
                23 -> pen = pen.copy(flags = pen.flags and CellFlags.ITALIC.inv())
                24 -> pen = pen.copy(flags = pen.flags and CellFlags.UNDERLINE.inv())
                25 -> pen = pen.copy(flags = pen.flags and CellFlags.BLINK.inv())
                27 -> pen = pen.copy(flags = pen.flags and CellFlags.INVERSE.inv())
                28 -> pen = pen.copy(flags = pen.flags and CellFlags.INVISIBLE.inv())
                29 -> pen = pen.copy(flags = pen.flags and CellFlags.STRIKETHROUGH.inv())
                in 30..37 -> pen = pen.copy(fg = TerminalColor.Indexed(code - 30))
                38 -> {
                    val (color, consumed) = parseExtendedColor(p, i + 1)
                    pen = pen.copy(fg = color)
                    i += consumed
                }
                39 -> pen = pen.copy(fg = TerminalColor.Default)
                in 40..47 -> pen = pen.copy(bg = TerminalColor.Indexed(code - 40))
                48 -> {
                    val (color, consumed) = parseExtendedColor(p, i + 1)
                    pen = pen.copy(bg = color)
                    i += consumed
                }
                49 -> pen = pen.copy(bg = TerminalColor.Default)
                in 90..97 -> pen = pen.copy(fg = TerminalColor.Indexed(code - 90 + 8))
                in 100..107 -> pen = pen.copy(bg = TerminalColor.Indexed(code - 100 + 8))
                else -> { /* unsupported SGR code: ignore */ }
            }
            i++
        }
        buffer.setPen(pen)
    }

    /** Parses the `5;<idx>` or `2;r;g;b` tail of an extended (38/48) SGR color; returns (color, paramsConsumedAfterModeSelector+1). */
    private fun parseExtendedColor(p: List<Int?>, startIndex: Int): Pair<TerminalColor, Int> {
        return when (p.getOrNull(startIndex)) {
            5 -> TerminalColor.Indexed(p.getOrNull(startIndex + 1) ?: 0) to 2
            2 -> TerminalColor.Rgb(
                p.getOrNull(startIndex + 1) ?: 0,
                p.getOrNull(startIndex + 2) ?: 0,
                p.getOrNull(startIndex + 3) ?: 0,
            ) to 4
            else -> TerminalColor.Default to 1
        }
    }

    private fun handleOsc(b: Int) {
        when {
            b == 0x07 -> finishOsc()
            oscEscPending && b == '\\'.code -> finishOsc()
            oscEscPending -> {
                oscEscPending = false
                oscBuilder.append(0x1B.toChar()).append(b.toChar())
            }
            b == 0x1B -> oscEscPending = true
            else -> if (oscBuilder.length < 4096) oscBuilder.append(b.toChar())
        }
    }

    private fun finishOsc() {
        val parts = oscBuilder.toString().split(';', limit = 2)
        if (parts.size == 2 && (parts[0] == "0" || parts[0] == "2")) onTitleChange?.invoke(parts[1])
        oscBuilder.clear()
        oscEscPending = false
        state = State.GROUND
    }

    private fun handleDcsSkip(b: Int) {
        when {
            dcsEscPending && b == '\\'.code -> { dcsEscPending = false; state = State.GROUND }
            b == 0x1B -> dcsEscPending = true
            else -> dcsEscPending = false
        }
    }
}
