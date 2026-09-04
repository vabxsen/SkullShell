package dev.aicli.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders a [TerminalBuffer] to a [Canvas], viewport-only (never lays out the full scrollback
 * per frame) and throttled to ~60 fps regardless of how fast bytes are arriving from the PTY —
 * see the polling [LaunchedEffect] below. Scrolling pins to the live bottom unless the user has
 * manually scrolled up into history; new output while scrolled up does not yank the view back
 * down.
 */
@Composable
fun TerminalView(
    buffer: TerminalBuffer,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    cursorBlink: Boolean = true,
    copyOnSelect: Boolean = true,
    contentPadding: Dp = 0.dp,
    backgroundColor: Int = 0x00101014,
    defaultForeground: Int = 0xffe6e6e6.toInt(),
    focusRequester: FocusRequester = remember { FocusRequester() },
    onInput: (ByteArray) -> Unit = {},
    onSizeChanged: (cols: Int, rows: Int) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val textToolbar = LocalTextToolbar.current
    var canvasBounds by remember { mutableStateOf(Rect.Zero) }
    var selectionAnchor by remember { mutableStateOf(Rect.Zero) }
    DisposableEffect(textToolbar) { onDispose { textToolbar.hide() } }
    val padPx = with(density) { contentPadding.toPx() }

    val paint = remember(fontSize, density.density, density.fontScale) {
        Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = with(density) { fontSize.toPx() }
            isAntiAlias = true
        }
    }
    val charWidth = remember(paint) { paint.measureText("M") }
    val fontMetrics = remember(paint) { paint.fontMetrics }
    val charHeight = remember(fontMetrics) { fontMetrics.descent - fontMetrics.ascent }

    // Throttled recomposition trigger: poll the buffer's revision counter at ~60Hz and only
    // touch Compose state when it actually changed, coalescing bursts of PTY output into a
    // single recomposition per frame instead of one per byte/escape-sequence.
    var displayedRevision by remember { mutableStateOf(0) }
    LaunchedEffect(buffer) {
        var last = -1
        while (isActive) {
            val current = buffer.revision.value
            if (current != last) {
                displayedRevision = current
                last = current
            }
            delay(16)
        }
    }

    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(cursorBlink) {
        blink = true
        while (isActive && cursorBlink) {
            delay(530)
            blink = !blink
        }
    }

    var lastCols by remember { mutableStateOf(0) }
    var lastRows by remember { mutableStateOf(0) }

    var pinnedToBottom by remember { mutableStateOf(true) }
    var scrollFromBottom by remember { mutableStateOf(0) }
    var lastScrollbackSize by remember { mutableStateOf(0) }
    var dragRemainder by remember { mutableStateOf(0f) }

    var selectionStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectionEnd by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Standard "invisible text field" pattern for a custom-rendered terminal: a real IME/keyboard
    // connection has to attach to *some* Android text-input-capable element to receive typed text
    // (soft keyboard commits, autocorrect, physical-keyboard composition) — the Canvas below only
    // ever draws pixels, it has no Android input connection of its own. The field's own visible
    // text is irrelevant (this Canvas renders the terminal grid, not the field's buffer). Keep
    // a bounded input history and forward only the delta. Resetting a value-based field after
    // every commit can leave its input connection stale and resend previously committed text.
    // Transforming the state synchronously keeps Compose and the IME in agreement, forwarding
    // to [onInput] as raw bytes. This is the one place typed text actually reaches the PTY —
    // arrow/Ctrl/Esc/function keys are handled separately by [TerminalKeyboardBar].
    val keyboardController = LocalSoftwareKeyboardController.current
    val sentinel = "\u200B"
    val fieldState = remember(buffer) { TextFieldState(sentinel, TextRange(1)) }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        onInput(text.replace("\n", "\r").toByteArray(Charsets.UTF_8))
    }

    LaunchedEffect(displayedRevision) {
        val newSize = buffer.scrollbackSize()
        if (!pinnedToBottom) {
            val delta = newSize - lastScrollbackSize
            if (delta > 0) scrollFromBottom += delta
        }
        lastScrollbackSize = newSize
    }

    fun totalLines() = buffer.scrollbackSize() + buffer.rows
    fun getLine(absoluteIndex: Int): Row {
        val sbSize = buffer.scrollbackSize()
        return if (absoluteIndex < sbSize) buffer.scrollbackLine(absoluteIndex)
        else buffer.snapshotRow(absoluteIndex - sbSize)
    }

    Box(modifier = modifier) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { canvasBounds = it.boundsInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    textToolbar.hide()
                    selectionStart = null
                    selectionEnd = null
                    focusRequester.requestFocus()
                    keyboardController?.show()
                })
            }
            .pointerInput(charWidth, charHeight, copyOnSelect) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (scrollFromBottom <= 0) {
                            pinnedToBottom = true
                            scrollFromBottom = 0
                        }
                    },
                ) { change, dragAmount ->
                    if (selectionStart == null) {
                        change.consume()
                        dragRemainder += dragAmount / charHeight
                        val lineDelta = dragRemainder.toInt()
                        dragRemainder -= lineDelta
                        val maxOffset = buffer.scrollbackSize()
                        scrollFromBottom = (scrollFromBottom + lineDelta).coerceIn(0, max(0, maxOffset))
                        pinnedToBottom = scrollFromBottom == 0
                    }
                }
            }
            .pointerInput(charWidth, charHeight, copyOnSelect) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        textToolbar.hide()
                        val point = canvasBounds.topLeft + offset
                        selectionAnchor = Rect(point.x, point.y, point.x + 1, point.y + charHeight)
                        val topLine = currentTopLine(pinnedToBottom, totalLines(), lastRows, scrollFromBottom)
                        val cell = cellFromOffset(offset, padPx, charWidth, charHeight, topLine, lastCols)
                        selectionStart = cell
                        selectionEnd = cell
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val topLine = currentTopLine(pinnedToBottom, totalLines(), lastRows, scrollFromBottom)
                        selectionEnd = cellFromOffset(change.position, padPx, charWidth, charHeight, topLine, lastCols)
                    },
                    onDragEnd = {
                        val start = selectionStart
                        val end = selectionEnd
                        if (start != null && end != null) {
                            val text = extractSelectedText(::getLine, start, end, lastCols)
                            if (copyOnSelect && text.isNotEmpty()) {
                                clipboard.setText(AnnotatedString(text))
                            } else {
                                textToolbar.showMenu(selectionAnchor,
                                    onCopyRequested = {
                                        clipboard.setText(AnnotatedString(text))
                                        textToolbar.hide()
                                        selectionStart = null
                                        selectionEnd = null
                                    },
                                    onPasteRequested = {
                                        val pasted = clipboard.getText()?.text.orEmpty()
                                        val content = if (buffer.bracketedPaste) "\u001B[200~$pasted\u001B[201~" else pasted
                                        onInput(content.toByteArray(Charsets.UTF_8))
                                        textToolbar.hide()
                                        selectionStart = null
                                        selectionEnd = null
                                    }, onCutRequested = null, onSelectAllRequested = null)
                            }
                        }
                        if (copyOnSelect) {
                            selectionStart = null
                            selectionEnd = null
                        }
                    },
                )
            },
    ) {
        val cols = max(1, ((size.width - padPx * 2) / charWidth).toInt())
        val visibleRows = max(1, ((size.height - padPx * 2) / charHeight).toInt())
        if (cols != lastCols || visibleRows != lastRows) {
            lastCols = cols
            lastRows = visibleRows
            onSizeChanged(cols, visibleRows)
        }

        drawRect(Color(backgroundColor), size = size)

        val topLine = currentTopLine(pinnedToBottom, totalLines(), lastRows, scrollFromBottom)
        val sel = normalizeSelection(selectionStart, selectionEnd)

        // Only the grid is inset by [contentPadding] — the background above stays full-bleed, so
        // the gutter reads as breathing room around the text rather than as a border or a seam.
        translate(padPx, padPx) {
            for (viewRow in 0 until visibleRows) {
                val absoluteLine = topLine + viewRow
                if (absoluteLine < 0 || absoluteLine >= totalLines()) continue
                val row = getLine(absoluteLine)
                drawRow(row, viewRow, charWidth, charHeight, paint, defaultForeground, backgroundColor, absoluteLine, sel)
            }

            if (buffer.cursorVisible && blink) {
                val cursorAbsoluteLine = (totalLines() - buffer.rows) + buffer.cursorRow
                val cursorViewRow = cursorAbsoluteLine - topLine
                if (cursorViewRow in 0 until visibleRows) {
                    drawRect(
                        Color(defaultForeground or (0xFF shl 24)),
                        topLeft = Offset(buffer.cursorCol * charWidth, cursorViewRow * charHeight),
                        size = androidx.compose.ui.geometry.Size(charWidth, charHeight),
                        alpha = 0.5f,
                    )
                }
            }
        }
    }

    BasicTextField(
        state = fieldState,
        inputTransformation = InputTransformation {
            val oldText = originalText.toString().removePrefix(sentinel)
            val newText = asCharSequence().toString().removePrefix(sentinel)
            val shared = oldText.commonPrefixWith(newText).length
            if (length == 0 && oldText.isEmpty()) onInput(byteArrayOf(0x7F))
            else {
                repeat(oldText.substring(shared).codePointCount(0, oldText.length - shared)) { onInput(byteArrayOf(0x7F)) }
                sendText(newText.substring(shared))
            }
            // Retain a character for soft-keyboard deleteSurroundingText even at the boundary.
            // Normal edits retain composition; only trim unusually long input history.
            if (length == 0 || length > 4096) {
                replace(0, length, sentinel + newText.takeLast(2048))
                selection = TextRange(length)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.None),
        modifier = Modifier
            .testTag("terminal-input")
            .size(1.dp)
            .alpha(0f)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                if (event.isCtrlPressed && code in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z) {
                    onInput(byteArrayOf((code - android.view.KeyEvent.KEYCODE_A + 1).toByte()))
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> { onInput(byteArrayOf(0x0D)); true }
                    Key.Backspace -> { onInput(byteArrayOf(0x7F)); true }
                    Key.Delete -> { onInput(byteArrayOf(0x1B, '['.code.toByte(), '3'.code.toByte(), '~'.code.toByte())); true }
                    Key.Tab -> { onInput(byteArrayOf(9)); true }
                    Key.Escape -> { onInput(byteArrayOf(27)); true }
                    Key.DirectionUp -> { onInput(arrowKey('A', buffer.applicationCursorMode)); true }
                    Key.DirectionDown -> { onInput(arrowKey('B', buffer.applicationCursorMode)); true }
                    Key.DirectionRight -> { onInput(arrowKey('C', buffer.applicationCursorMode)); true }
                    Key.DirectionLeft -> { onInput(arrowKey('D', buffer.applicationCursorMode)); true }
                    else -> false
                }
            },
    )
    }
}

private fun DrawScope.drawRow(
    row: Row,
    viewRow: Int,
    charWidth: Float,
    charHeight: Float,
    paint: Paint,
    defaultForeground: Int,
    backgroundColor: Int,
    absoluteLine: Int,
    selection: Pair<Pair<Int, Int>, Pair<Int, Int>>?,
) {
    // backgroundColor may carry an alpha byte (e.g. a theme color's toArgb()) — the cell/ANSI
    // colors below are always packed 0xRRGGBB (see TerminalColor.resolveRgb), so mask it off
    // before comparing/using it as the "this cell is just the plain background" default.
    val defaultBgRgb = backgroundColor and 0xFFFFFF
    val baselineY = viewRow * charHeight - paint.fontMetrics.ascent
    for (col in row.indices) {
        val cell = row[col]
        if (cell.continuation) continue
        val selected = selection != null && isCellSelected(absoluteLine, col, selection)
        val inverse = cell.hasFlag(CellFlags.INVERSE) != selected
        val fgRgb = TerminalColor.resolveRgb(cell.fg, defaultForeground)
        val bgRgb = TerminalColor.resolveRgb(cell.bg, defaultBgRgb)
        val (drawFg, drawBg) = if (inverse) bgRgb to fgRgb else fgRgb to bgRgb
        if (drawBg != defaultBgRgb || selected) {
            drawRect(
                Color(drawBg or (0xFF shl 24)),
                topLeft = Offset(col * charWidth, viewRow * charHeight),
                size = androidx.compose.ui.geometry.Size(charWidth, charHeight),
            )
        }
        if (cell.codepoint != ' '.code) {
            drawIntoCanvas { canvas ->
                paint.color = drawFg or (0xFF shl 24)
                paint.isFakeBoldText = cell.hasFlag(CellFlags.BOLD)
                paint.isUnderlineText = cell.hasFlag(CellFlags.UNDERLINE)
                paint.isStrikeThruText = cell.hasFlag(CellFlags.STRIKETHROUGH)
                paint.alpha = if (cell.hasFlag(CellFlags.DIM)) 160 else 255
                if (!cell.hasFlag(CellFlags.INVISIBLE)) {
                    val chars = Character.toChars(cell.codepoint)
                    canvas.nativeCanvas.drawText(chars, 0, chars.size, col * charWidth, baselineY, paint)
                }
            }
        }
    }
}

private fun currentTopLine(pinnedToBottom: Boolean, totalLines: Int, viewportRows: Int, scrollFromBottom: Int): Int {
    val offset = if (pinnedToBottom) 0 else scrollFromBottom
    return (totalLines - viewportRows - offset).coerceAtLeast(0)
}

private fun cellFromOffset(offset: Offset, pad: Float, charWidth: Float, charHeight: Float, topLine: Int, cols: Int): Pair<Int, Int> {
    val col = ((offset.x - pad) / charWidth).toInt().coerceIn(0, max(0, cols - 1))
    val viewRow = ((offset.y - pad) / charHeight).toInt()
    return (topLine + viewRow) to col
}

private fun normalizeSelection(start: Pair<Int, Int>?, end: Pair<Int, Int>?): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
    if (start == null || end == null) return null
    return if (start.first < end.first || (start.first == end.first && start.second <= end.second)) {
        start to end
    } else {
        end to start
    }
}

private fun isCellSelected(line: Int, col: Int, selection: Pair<Pair<Int, Int>, Pair<Int, Int>>): Boolean {
    val (start, end) = selection
    if (line < start.first || line > end.first) return false
    if (line == start.first && col < start.second) return false
    if (line == end.first && col > end.second) return false
    return true
}

private fun extractSelectedText(getLine: (Int) -> Row, start: Pair<Int, Int>, end: Pair<Int, Int>, cols: Int): String {
    val (s, e) = if (start.first < end.first || (start.first == end.first && start.second <= end.second)) start to end else end to start
    val sb = StringBuilder()
    for (line in s.first..e.first) {
        val row = getLine(line)
        val fromCol = if (line == s.first) s.second else 0
        val toCol = if (line == e.first) e.second else cols - 1
        for (col in fromCol..toCol.coerceAtMost(row.size - 1)) {
            if (!row[col].continuation) sb.appendCodePoint(row[col].codepoint)
        }
        if (line != e.first) sb.append('\n')
    }
    return sb.toString().trimEnd()
}
