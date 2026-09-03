package dev.aicli.terminal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private fun String.bytes() = toByteArray(Charsets.UTF_8)
private const val ESC = ""

class AnsiParserTest {

    @Test
    fun `plain text is placed starting at the cursor`() {
        val buffer = TerminalBuffer(10, 5)
        AnsiParser(buffer).feed("Hi".bytes())

        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo('H'.code)
        assertThat(buffer.snapshotRow(0)[1].codepoint).isEqualTo('i'.code)
        assertThat(buffer.cursorCol).isEqualTo(2)
    }

    @Test
    fun `line feed and carriage return move the cursor to the next line start`() {
        val buffer = TerminalBuffer(10, 5)
        AnsiParser(buffer).feed("AB\r\nC".bytes())

        assertThat(buffer.cursorRow).isEqualTo(1)
        assertThat(buffer.cursorCol).isEqualTo(1)
        assertThat(buffer.snapshotRow(1)[0].codepoint).isEqualTo('C'.code)
    }

    @Test
    fun `cursor movement CSI sequences move the cursor relatively`() {
        val buffer = TerminalBuffer(20, 10)
        // Move to row 5 (CUD x5), then right 3 (CUF), then up 2 (CUU).
        AnsiParser(buffer).feed("$ESC[5B$ESC[3C$ESC[2A".bytes())

        assertThat(buffer.cursorRow).isEqualTo(3)
        assertThat(buffer.cursorCol).isEqualTo(3)
    }

    @Test
    fun `CUP moves the cursor to an absolute 1-indexed position`() {
        val buffer = TerminalBuffer(20, 10)
        AnsiParser(buffer).feed("$ESC[4;7H".bytes())

        assertThat(buffer.cursorRow).isEqualTo(3)
        assertThat(buffer.cursorCol).isEqualTo(6)
    }

    @Test
    fun `SGR bold and standard color set the pen for subsequently written cells`() {
        val buffer = TerminalBuffer(10, 5)
        AnsiParser(buffer).feed("$ESC[1;31mX".bytes())

        val cell = buffer.snapshotRow(0)[0]
        assertThat(cell.hasFlag(CellFlags.BOLD)).isTrue()
        assertThat(cell.fg).isEqualTo(TerminalColor.Indexed(1))
    }

    @Test
    fun `SGR reset clears prior attributes`() {
        val buffer = TerminalBuffer(10, 5)
        AnsiParser(buffer).feed("$ESC[1;31mA$ESC[0mB".bytes())

        assertThat(buffer.snapshotRow(0)[0].hasFlag(CellFlags.BOLD)).isTrue()
        val second = buffer.snapshotRow(0)[1]
        assertThat(second.hasFlag(CellFlags.BOLD)).isFalse()
        assertThat(second.fg).isEqualTo(TerminalColor.Default)
    }

    @Test
    fun `256-color extended SGR sets an Indexed color`() {
        val buffer = TerminalBuffer(10, 5)
        AnsiParser(buffer).feed("$ESC[38;5;201mX".bytes())

        assertThat(buffer.snapshotRow(0)[0].fg).isEqualTo(TerminalColor.Indexed(201))
    }

    @Test
    fun `truecolor extended SGR sets an Rgb color for both foreground and background`() {
        val buffer = TerminalBuffer(10, 5)
        AnsiParser(buffer).feed("$ESC[38;2;10;20;30;48;2;40;50;60mX".bytes())

        val cell = buffer.snapshotRow(0)[0]
        assertThat(cell.fg).isEqualTo(TerminalColor.Rgb(10, 20, 30))
        assertThat(cell.bg).isEqualTo(TerminalColor.Rgb(40, 50, 60))
    }

    @Test
    fun `erase in line mode 2 clears the whole line without moving the cursor`() {
        val buffer = TerminalBuffer(10, 5)
        val parser = AnsiParser(buffer)
        parser.feed("HELLO".bytes())
        parser.feed("$ESC[2K".bytes())

        assertThat(buffer.snapshotRow(0).all { it.codepoint == ' '.code }).isTrue()
        assertThat(buffer.cursorCol).isEqualTo(5)
    }

    @Test
    fun `erase in display mode 2 clears every row`() {
        val buffer = TerminalBuffer(5, 3)
        val parser = AnsiParser(buffer)
        parser.feed("AAAAA\r\nBBBBB\r\nCCCCC".bytes())
        parser.feed("$ESC[2J".bytes())

        for (row in 0 until 3) {
            assertThat(buffer.snapshotRow(row).all { it.codepoint == ' '.code }).isTrue()
        }
    }

    @Test
    fun `alternate screen toggles and preserves the primary buffer's content underneath`() {
        val buffer = TerminalBuffer(10, 5)
        val parser = AnsiParser(buffer)
        parser.feed("primary".bytes())
        assertThat(buffer.alternateActive).isFalse()

        parser.feed("$ESC[?1049h".bytes())
        assertThat(buffer.alternateActive).isTrue()
        // Entering the alt screen resets the cursor and starts from a blank grid.
        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo(' '.code)

        parser.feed("alt-screen".bytes())
        parser.feed("$ESC[?1049l".bytes())

        assertThat(buffer.alternateActive).isFalse()
        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo('p'.code)
    }

    @Test
    fun `cursor visibility DEC private mode is tracked`() {
        val buffer = TerminalBuffer(10, 5)
        val parser = AnsiParser(buffer)
        assertThat(buffer.cursorVisible).isTrue()

        parser.feed("$ESC[?25l".bytes())
        assertThat(buffer.cursorVisible).isFalse()

        parser.feed("$ESC[?25h".bytes())
        assertThat(buffer.cursorVisible).isTrue()
    }

    @Test
    fun `bracketed paste mode is tracked`() {
        val buffer = TerminalBuffer(10, 5)
        val parser = AnsiParser(buffer)

        parser.feed("$ESC[?2004h".bytes())
        assertThat(buffer.bracketedPaste).isTrue()

        parser.feed("$ESC[?2004l".bytes())
        assertThat(buffer.bracketedPaste).isFalse()
    }

    @Test
    fun `OSC window title sequence invokes the title callback`() {
        val buffer = TerminalBuffer(10, 5)
        val parser = AnsiParser(buffer)
        var title: String? = null
        parser.onTitleChange = { title = it }

        parser.feed("$ESC]0;my session".bytes())

        assertThat(title).isEqualTo("my session")
    }

    @Test
    fun `OSC title terminated by ST (ESC backslash) also invokes the callback`() {
        val buffer = TerminalBuffer(10, 5)
        val parser = AnsiParser(buffer)
        var title: String? = null
        parser.onTitleChange = { title = it }

        parser.feed("$ESC]2;other title$ESC\\".bytes())

        assertThat(title).isEqualTo("other title")
    }

    @Test
    fun `BEL invokes the bell callback and is not printed`() {
        val buffer = TerminalBuffer(10, 5)
        val parser = AnsiParser(buffer)
        var rang = false
        parser.onBell = { rang = true }

        parser.feed("AB".bytes())

        assertThat(rang).isTrue()
        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo('A'.code)
        assertThat(buffer.snapshotRow(0)[1].codepoint).isEqualTo('B'.code)
    }

    @Test
    fun `an escape sequence split across two feed calls still resumes correctly`() {
        val buffer = TerminalBuffer(10, 5)
        val parser = AnsiParser(buffer)

        parser.feed("$ESC[1".bytes()) // split mid-CSI-parameter
        parser.feed(";31mX".bytes())

        val cell = buffer.snapshotRow(0)[0]
        assertThat(cell.hasFlag(CellFlags.BOLD)).isTrue()
        assertThat(cell.fg).isEqualTo(TerminalColor.Indexed(1))
    }

    @Test
    fun `a multi-byte UTF-8 codepoint split across two feed calls decodes correctly`() {
        val buffer = TerminalBuffer(10, 5)
        val parser = AnsiParser(buffer)
        // U+00E9 'é' encodes as 0xC3 0xA9 in UTF-8; split the two bytes across feed() calls.
        val encoded = "é".bytes()
        assertThat(encoded).hasLength(2)

        parser.feed(byteArrayOf(encoded[0]))
        parser.feed(byteArrayOf(encoded[1]))

        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo('é'.code)
    }

    @Test
    fun `an unrecognized CSI final byte is consumed without corrupting subsequent output`() {
        val buffer = TerminalBuffer(10, 5)
        val parser = AnsiParser(buffer)

        // 'q' (e.g. DECSCUSR cursor style) isn't implemented — must not leak into the grid or
        // desync the parser for what follows.
        parser.feed("$ESC[2 qOK".bytes())

        assertThat(buffer.snapshotRow(0)[0].codepoint).isEqualTo('O'.code)
        assertThat(buffer.snapshotRow(0)[1].codepoint).isEqualTo('K'.code)
    }
}
