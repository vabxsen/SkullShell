package dev.aicli.terminal

/**
 * Byte-level key encoding for the terminal: the escape sequences a VT-style program expects for
 * keys a soft keyboard cannot produce.
 *
 * This is deliberately separate from the on-screen key bar that calls it. The bar is app chrome
 * and is themed by the app's design system, so it lives in the app module; the encoding is
 * terminal behaviour and belongs here, next to the parser that has to understand the same
 * sequences coming back the other way.
 */

/** Maps a Ctrl+<letter> combination to its C0 control byte (the standard `char & 0x1f`). */
fun ctrlByte(letter: Char): Byte = (letter.uppercaseChar().code and 0x1f).toByte()

const val ESC: Byte = 0x1B

/** CSI - `ESC [` followed by the given bytes. */
fun csi(vararg extra: Byte): ByteArray = byteArrayOf(ESC, '['.code.toByte(), *extra)

/** SS3 - `ESC O <letter>`, the application-cursor-mode form of the arrow keys. */
fun ss3(letter: Char): ByteArray = byteArrayOf(ESC, 'O'.code.toByte(), letter.code.toByte())

/**
 * Arrow key bytes for the current cursor-key mode. Full-screen programs (vim, less) switch the
 * terminal into application cursor mode and expect SS3; a shell at a prompt expects CSI.
 */
fun arrowKey(letter: Char, applicationCursorMode: Boolean): ByteArray =
    if (applicationCursorMode) ss3(letter) else csi(letter.code.toByte())

/**
 * Combines a plain printable key press with any sticky CTRL/ALT modifiers currently held by the
 * on-screen key bar, so a letter typed right after tapping CTRL produces the control byte rather
 * than the letter.
 */
fun combineWithModifiers(char: Char, ctrlHeld: Boolean, altHeld: Boolean): ByteArray {
    val base = if (ctrlHeld && char.isLetter()) byteArrayOf(ctrlByte(char)) else char.toString().toByteArray(Charsets.UTF_8)
    return if (altHeld) byteArrayOf(ESC) + base else base
}
