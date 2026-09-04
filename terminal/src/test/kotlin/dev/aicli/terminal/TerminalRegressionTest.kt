package dev.aicli.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalRegressionTest {
    private val esc = "\u001b"
    private fun row(buffer: TerminalBuffer, index: Int = 0) = buffer.snapshotRow(index).joinToString("") { String(Character.toChars(it.codepoint)) }

    @Test fun carriageReturnAfterFullWidthDoesNotSkipALine() {
        val b = TerminalBuffer(4, 4)
        AnsiParser(b).feed("1234\r\nX".toByteArray())
        assertEquals('X'.code, b.snapshotRow(1)[0].codepoint)
    }
    @Test fun lineFeedClearsPendingWrap() {
        val b = TerminalBuffer(4, 4)
        AnsiParser(b).feed("1234\nX".toByteArray())
        assertEquals('X'.code, b.snapshotRow(1)[3].codepoint)
    }
    @Test fun alternateScreenRestoresCursorAndContent() {
        val b = TerminalBuffer(10, 4)
        val p = AnsiParser(b)
        p.feed("abc$esc[?1049hother$esc[?1049lZ".toByteArray())
        assertTrue(row(b).startsWith("abcZ"))
    }
    @Test fun repliesToCursorPositionAndDeviceQueries() {
        val b = TerminalBuffer(80, 24)
        val p = AnsiParser(b)
        val replies = mutableListOf<String>()
        p.onResponse = { replies += it.toString(Charsets.UTF_8) }
        p.feed("$esc[4;7H$esc[6n$esc[c".toByteArray())
        assertEquals(listOf("$esc[4;7R", "$esc[?1;2c"), replies)
    }
    @Test fun editingSequencesUsedByInteractiveClisWork() {
        val b = TerminalBuffer(8, 2)
        AnsiParser(b).feed("abcde$esc[3G$esc[P$esc[2@X".toByteArray())
        assertEquals("abX de  ", row(b))
    }
    @Test fun invalidUnicodeCannotCrashRenderer() {
        val b = TerminalBuffer(8, 2)
        AnsiParser(b).feed(byteArrayOf(0xf7.toByte(),0xbf.toByte(),0xbf.toByte(),0xbf.toByte()))
        assertEquals(0xfffd, b.snapshotRow(0)[0].codepoint)
    }
    @Test(timeout = 1000) fun excessiveScrollCountsAreBounded() {
        AnsiParser(TerminalBuffer(8, 2)).feed("$esc[2147483647S".toByteArray())
    }
    @Test fun clearScrollbackLeavesVisibleOutput() {
        val b = TerminalBuffer(8, 2)
        val p = AnsiParser(b)
        p.feed("a\r\nb\r\nc$esc[3J".toByteArray())
        assertEquals(0, b.scrollbackSize())
        assertTrue(row(b,1).startsWith("c"))
    }
}
