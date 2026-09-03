package dev.aicli.terminal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TerminalBufferTest {

    @Test
    fun `putCodepoint advances the cursor and wraps at end of line`() {
        val buffer = TerminalBuffer(3, 3)
        buffer.putCodepoint('A'.code)
        buffer.putCodepoint('B'.code)
        buffer.putCodepoint('C'.code)
        // Cursor pins at the last column with a pending wrap rather than overflowing immediately.
        assertThat(buffer.cursorCol).isEqualTo(2)

        buffer.putCodepoint('D'.code)
        assertThat(buffer.cursorRow).isEqualTo(1)
        assertThat(buffer.snapshotRow(1)[0].codepoint).isEqualTo('D'.code)
    }

    @Test
    fun `lineFeed scrolls the region up once the bottom row is reached`() {
        val buffer = TerminalBuffer(5, 2)
        buffer.putCodepoint('1'.code)
        buffer.lineFeed()
        buffer.carriageReturn()
        buffer.putCodepoint('2'.code)
        buffer.lineFeed() // at the last row already — this scrolls
        buffer.carriageReturn()
        buffer.putCodepoint('3'.code)

        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo('2'.code)
        assertThat(buffer.snapshotRow(1)[0].codepoint).isEqualTo('3'.code)
        assertThat(buffer.scrollbackSize()).isEqualTo(1)
        assertThat(buffer.scrollbackLine(0)[0].codepoint).isEqualTo('1'.code)
    }

    @Test
    fun `scrollback is bounded and drops the oldest lines`() {
        val maxScrollback = 5
        val buffer = TerminalBuffer(5, 1, maxScrollback = maxScrollback)

        // Push far more lines through than the scrollback can hold.
        repeat(50) { i ->
            buffer.putCodepoint(('0' + (i % 10)).code)
            buffer.lineFeed()
            buffer.carriageReturn()
        }

        assertThat(buffer.scrollbackSize()).isEqualTo(maxScrollback)
        // The oldest surviving line should be the 45th written (50 total - 5 kept), i.e. digit '5'.
        assertThat(buffer.scrollbackLine(0)[0].codepoint).isEqualTo('5'.code)
    }

    @Test
    fun `eraseInLine mode 0 clears from the cursor to end of line only`() {
        val buffer = TerminalBuffer(5, 1)
        buffer.putCodepoint('A'.code)
        buffer.putCodepoint('B'.code)
        buffer.putCodepoint('C'.code)
        buffer.moveCursor(0, 1)
        buffer.eraseInLine(0)

        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo('A'.code)
        assertThat(buffer.snapshotRow(0)[1].codepoint).isEqualTo(' '.code)
        assertThat(buffer.snapshotRow(0)[2].codepoint).isEqualTo(' '.code)
    }

    @Test
    fun `alternate screen swap does not touch the primary grid or its scrollback`() {
        val buffer = TerminalBuffer(5, 2)
        buffer.putCodepoint('P'.code)

        buffer.setAlternateScreen(true)
        assertThat(buffer.alternateActive).isTrue()
        buffer.putCodepoint('Q'.code)
        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo('Q'.code)

        buffer.setAlternateScreen(false)
        assertThat(buffer.alternateActive).isFalse()
        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo('P'.code)
    }

    @Test
    fun `scrollback size is reported as zero while the alternate screen is active`() {
        val buffer = TerminalBuffer(5, 1)
        buffer.putCodepoint('A'.code)
        buffer.lineFeed() // pushes one line into scrollback
        assertThat(buffer.scrollbackSize()).isEqualTo(1)

        buffer.setAlternateScreen(true)
        assertThat(buffer.scrollbackSize()).isEqualTo(0)
    }

    @Test
    fun `resize preserves existing cell content up to the new bounds and clamps the cursor`() {
        val buffer = TerminalBuffer(5, 5)
        buffer.putCodepoint('A'.code)
        buffer.moveCursor(4, 4)

        buffer.resize(3, 3)

        assertThat(buffer.cols).isEqualTo(3)
        assertThat(buffer.rows).isEqualTo(3)
        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo('A'.code)
        assertThat(buffer.cursorRow).isEqualTo(2)
        assertThat(buffer.cursorCol).isEqualTo(2)
    }

    @Test
    fun `revision counter increases on every mutation so the renderer can throttle off it`() {
        val buffer = TerminalBuffer(5, 5)
        val before = buffer.revision.value
        buffer.putCodepoint('A'.code)
        assertThat(buffer.revision.value).isGreaterThan(before)
    }

    @Test
    fun `saveCursor and restoreCursor round-trip position and pen`() {
        val buffer = TerminalBuffer(10, 10)
        buffer.moveCursor(3, 4)
        buffer.setPen(PenState(fg = TerminalColor.Indexed(2)))
        buffer.saveCursor()

        buffer.moveCursor(0, 0)
        buffer.setPen(PenState())
        buffer.restoreCursor()

        assertThat(buffer.cursorRow).isEqualTo(3)
        assertThat(buffer.cursorCol).isEqualTo(4)
        assertThat(buffer.pen.fg).isEqualTo(TerminalColor.Indexed(2))
    }
}
