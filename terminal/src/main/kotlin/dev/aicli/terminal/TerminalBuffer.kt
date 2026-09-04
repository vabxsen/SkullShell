package dev.aicli.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

typealias Row = Array<TerminalCell>

private fun blankRow(cols: Int): Row = Array(cols) { TerminalCell.BLANK }

/**
 * The terminal grid + cursor + scrollback state. Framework-free (no Android/Compose imports) so
 * it's plain-JVM unit testable; [TerminalView] renders it.
 *
 * Scrollback is a bounded ring buffer (default 5000 lines, see [maxScrollback]) — oldest lines
 * are evicted, never grown without bound, per the project's explicit memory requirement.
 *
 * Resize does not attempt reflow (rewrapping existing lines to the new width); it truncates or
 * pads rows and clamps the cursor. Reflow is a real terminal-emulator feature but a materially
 * larger undertaking (line-continuation tracking across the whole scrollback) that wasn't
 * justified for a mobile client where resizes are infrequent (rotation, split-screen) rather
 * than continuous — documented here rather than silently absent.
 */
class TerminalBuffer(
    initialCols: Int,
    initialRows: Int,
    private val maxScrollback: Int = 5000,
) {
    init { require(initialCols > 0 && initialRows > 0 && maxScrollback >= 0) }

    var cols: Int = initialCols
        private set
    var rows: Int = initialRows
        private set

    private var primary: MutableList<Row> = MutableList(initialRows) { blankRow(initialCols) }
    private var alternate: MutableList<Row> = MutableList(initialRows) { blankRow(initialCols) }
    private val scrollback = ArrayDeque<Row>()

    private val active: MutableList<Row> get() = if (alternateActive) alternate else primary

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var savedPen = PenState()
    private var pendingWrap = false

    var pen: PenState = PenState()
        private set

    var alternateActive: Boolean = false
        private set
    var cursorVisible: Boolean = true
        private set
    var autoWrap: Boolean = true
        private set
    var applicationCursorMode: Boolean = false
        private set
    var bracketedPaste: Boolean = false
        private set

    private var scrollTop: Int = 0
    private var scrollBottom: Int = initialRows - 1

    /** Bumped on every mutating call; the renderer throttles its own recomposition off this. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision
    private fun touch() {
        _revision.value = _revision.value + 1
    }

    @Synchronized
    fun snapshotRow(index: Int): Row = active.getOrNull(index)?.copyOf() ?: blankRow(cols)

    @Synchronized
    fun scrollbackSize(): Int = if (alternateActive) 0 else scrollback.size
    @Synchronized
    fun scrollbackLine(fromTop: Int): Row = scrollback.elementAtOrNull(fromTop)?.copyOf() ?: blankRow(cols)

    // ---- writing ----

    @Synchronized
    fun putCodepoint(rawCodepoint: Int) {
        val codepoint = if (Character.isValidCodePoint(rawCodepoint) && rawCodepoint !in 0xD800..0xDFFF) rawCodepoint else 0xFFFD
        if (pendingWrap) {
            // Standard DECAWM autowrap: the wrap consumes both a line feed AND a carriage
            // return — without resetting the column here, wrapped output would keep writing at
            // the old column on the new line instead of starting from the left margin (caught by
            // TerminalBufferTest's wrap test, which failed before this fix).
            newlineInternal(wrapped = true)
            cursorCol = 0
            pendingWrap = false
        }
        val wide = isWide(codepoint)
        active[cursorRow][cursorCol] = pen.toCell(codepoint)
        if (wide && cursorCol + 1 < cols) {
            active[cursorRow][cursorCol + 1] = pen.toCell(codepoint, continuation = true)
        }
        val advance = if (wide) 2 else 1
        if (cursorCol + advance >= cols) {
            cursorCol = cols - 1
            if (autoWrap) pendingWrap = true
        } else {
            cursorCol += advance
        }
        touch()
    }

    private fun isWide(codepoint: Int): Boolean {
        // A pragmatic subset of East-Asian-Wide/Fullwidth ranges; not the complete Unicode
        // East Asian Width table, but covers CJK + common emoji blocks.
        return codepoint in 0x1100..0x115F ||
            codepoint in 0x2E80..0xA4CF ||
            codepoint in 0xAC00..0xD7A3 ||
            codepoint in 0xF900..0xFAFF ||
            codepoint in 0xFF00..0xFF60 ||
            codepoint in 0x1F300..0x1FAFF
    }

    @Synchronized
    fun carriageReturn() {
        cursorCol = 0
        pendingWrap = false
        touch()
    }

    @Synchronized
    fun lineFeed() {
        pendingWrap = false
        newlineInternal(wrapped = false)
        touch()
    }

    private fun newlineInternal(wrapped: Boolean) {
        if (!wrapped) cursorCol = cursorCol.coerceAtMost(cols - 1)
        if (cursorRow == scrollBottom) {
            scrollUpInRegion(1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    @Synchronized
    fun backspace() {
        if (cursorCol > 0) cursorCol--
        pendingWrap = false
        touch()
    }

    @Synchronized
    fun tab(tabStop: Int = 8) {
        pendingWrap = false
        val next = ((cursorCol / tabStop) + 1) * tabStop
        cursorCol = next.coerceAtMost(cols - 1)
        touch()
    }

    // ---- cursor movement ----

    @Synchronized
    fun moveCursor(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, cols - 1)
        pendingWrap = false
        touch()
    }

    @Synchronized
    fun moveCursorRelative(dRow: Int, dCol: Int) = moveCursor(cursorRow + dRow, cursorCol + dCol)

    @Synchronized
    fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
        savedPen = pen
    }

    @Synchronized
    fun restoreCursor() {
        pendingWrap = false
        cursorRow = savedCursorRow.coerceIn(0, rows - 1)
        cursorCol = savedCursorCol.coerceIn(0, cols - 1)
        pen = savedPen
        touch()
    }

    // ---- erasing ----

    @Synchronized
    fun eraseInLine(mode: Int) {
        val row = active[cursorRow]
        when (mode) {
            0 -> for (c in cursorCol until cols) row[c] = TerminalCell.BLANK
            1 -> for (c in 0..cursorCol) row[c] = TerminalCell.BLANK
            2 -> for (c in 0 until cols) row[c] = TerminalCell.BLANK
        }
        touch()
    }

    @Synchronized
    fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseInLine(0)
                for (r in cursorRow + 1 until rows) active[r] = blankRow(cols)
            }
            1 -> {
                eraseInLine(1)
                for (r in 0 until cursorRow) active[r] = blankRow(cols)
            }
            2 -> for (r in 0 until rows) active[r] = blankRow(cols)
            3 -> scrollback.clear()
        }
        touch()
    }

    // ---- scrolling ----

    @Synchronized
    fun setScrollRegion(top: Int, bottom: Int) {
        scrollTop = top.coerceIn(0, rows - 1)
        scrollBottom = bottom.coerceIn(scrollTop, rows - 1)
        moveCursor(0, 0)
    }

    @Synchronized
    fun scrollUpInRegion(n: Int) {
        repeat(n.coerceIn(0, scrollBottom - scrollTop + 1)) {
            val removed = active.removeAt(scrollTop)
            if (!alternateActive && scrollTop == 0) {
                scrollback.addLast(removed)
                while (scrollback.size > maxScrollback) scrollback.removeFirst()
            }
            active.add(scrollBottom, blankRow(cols))
        }
        touch()
    }

    @Synchronized
    fun scrollDownInRegion(n: Int) {
        repeat(n.coerceIn(0, scrollBottom - scrollTop + 1)) {
            active.removeAt(scrollBottom)
            active.add(scrollTop, blankRow(cols))
        }
        touch()
    }

    @Synchronized
    fun insertLines(n: Int) {
        repeat(n.coerceIn(0, scrollBottom - scrollTop + 1)) {
            if (cursorRow in scrollTop..scrollBottom) {
                active.removeAt(scrollBottom)
                active.add(cursorRow, blankRow(cols))
            }
        }
        touch()
    }

    @Synchronized
    fun deleteLines(n: Int) {
        repeat(n.coerceIn(0, scrollBottom - scrollTop + 1)) {
            if (cursorRow in scrollTop..scrollBottom) {
                active.removeAt(cursorRow)
                active.add(scrollBottom, blankRow(cols))
            }
        }
        touch()
    }

    // ---- modes ----

    @Synchronized
    fun insertCharacters(count: Int) {
        val n = count.coerceIn(0, cols - cursorCol)
        val row = active[cursorRow]
        for (c in cols - 1 downTo cursorCol + n) row[c] = row[c - n]
        for (c in cursorCol until cursorCol + n) row[c] = pen.toCell(' '.code)
        touch()
    }

    @Synchronized
    fun deleteCharacters(count: Int) {
        val n = count.coerceIn(0, cols - cursorCol)
        val row = active[cursorRow]
        for (c in cursorCol until cols - n) row[c] = row[c + n]
        for (c in cols - n until cols) row[c] = pen.toCell(' '.code)
        touch()
    }

    @Synchronized
    fun eraseCharacters(count: Int) {
        val n = count.coerceIn(0, cols - cursorCol)
        for (c in cursorCol until cursorCol + n) active[cursorRow][c] = pen.toCell(' '.code)
        touch()
    }

    @Synchronized
    fun setPen(newPen: PenState) {
        pen = newPen
    }

    @Synchronized
    fun setAlternateScreen(enabled: Boolean) {
        if (enabled == alternateActive) return
        if (enabled) {
            saveCursor()
            alternate = MutableList(rows) { blankRow(cols) }
            cursorRow = 0
            cursorCol = 0
        } else {
            restoreCursor()
        }
        alternateActive = enabled
        pendingWrap = false
        touch()
    }

    @Synchronized
    fun setCursorVisible(visible: Boolean) {
        cursorVisible = visible
        touch()
    }

    @Synchronized
    fun setAutoWrap(enabled: Boolean) {
        autoWrap = enabled
    }

    @Synchronized
    fun setApplicationCursorMode(enabled: Boolean) {
        applicationCursorMode = enabled
    }

    @Synchronized
    fun setBracketedPaste(enabled: Boolean) {
        bracketedPaste = enabled
    }

    // ---- resize ----

    @Synchronized
    fun resize(newCols: Int, newRows: Int) {
        require(newCols > 0 && newRows > 0)
        pendingWrap = false
        if (newCols == cols && newRows == rows) return
        primary = resizeGrid(primary, newCols, newRows)
        alternate = resizeGrid(alternate, newCols, newRows)
        cols = newCols
        rows = newRows
        scrollTop = 0
        scrollBottom = newRows - 1
        cursorRow = cursorRow.coerceIn(0, newRows - 1)
        cursorCol = cursorCol.coerceIn(0, newCols - 1)
        touch()
    }

    private fun resizeGrid(grid: MutableList<Row>, newCols: Int, newRows: Int): MutableList<Row> {
        val resizedRows = grid.map { row ->
            Array(newCols) { c -> if (c < row.size) row[c] else TerminalCell.BLANK }
        }.toMutableList()
        while (resizedRows.size < newRows) resizedRows.add(blankRow(newCols))
        while (resizedRows.size > newRows) resizedRows.removeAt(resizedRows.size - 1)
        return resizedRows
    }
}
