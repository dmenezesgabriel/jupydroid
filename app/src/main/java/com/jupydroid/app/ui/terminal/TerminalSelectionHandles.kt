package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** So a Compose test - and a Maestro flow, through `testTagsAsResourceId` - can reach each handle. */
const val TERMINAL_SELECTION_START_TAG = "terminal-selection-start"
const val TERMINAL_SELECTION_END_TAG = "terminal-selection-end"

/**
 * The two handles a selection's ends are dragged by - `TextSelectionHandleView` (termux-app
 * `3df69d1d`). [origin] is where column `0` of the view whose top row is [topRow] sits in this
 * composable's parent; a handle whose row is scrolled out of the view is not drawn, as the
 * reference hides one that is not visible.
 *
 * **Drawn over the gesture layer, as its sibling, not inside it.** Compose hit-tests siblings from
 * the top and stops at the first one hit, so a press on a handle never reaches the scroll drag and
 * long press read on the layer below, nor the input field inside it.
 *
 * ```
 * TerminalSelectionHandles(selection, cellPx, grid, state.topRow, origin, onMoveEnd = selector::moveEnd)
 * ```
 */
@Composable
fun TerminalSelectionHandles(
  selection: TerminalTextSelection,
  cell: IntSize,
  grid: IntSize,
  topRow: Int,
  origin: Offset,
  onMoveEnd: (TerminalSelectionEnd, TerminalCellPosition) -> Unit,
) {
  for (end in TerminalSelectionEnd.entries) {
    val row = if (end == TerminalSelectionEnd.START) selection.startRow else selection.endRow
    if (row !in topRow until topRow + grid.height) continue
    SelectionHandle(end, end.anchorIn(selection, cell, topRow), origin) { anchor ->
      onMoveEnd(end, end.cellUnder(anchor, cell, grid, topRow))
    }
  }
}

/**
 * One handle, hanging from [anchor] (from [origin]) with its square corner on it: Compose's own
 * text-field handle (`AndroidSelectionHandles.android.kt`, `drawSelectionHandle`), in
 * [LocalTextSelectionColors]' handle colour, so it is the handle every other field in the app
 * shows. [onDragTo] receives where the anchor would be now: where it was when the drag began, plus
 * how far the finger has travelled - not the current anchor plus that, since the anchor follows
 * the end the drag has already moved.
 */
@Composable
private fun SelectionHandle(end: TerminalSelectionEnd, anchor: Offset, origin: Offset, onDragTo: (Offset) -> Unit) {
  val sizePx = with(LocalDensity.current) { HANDLE_SIZE.toPx() }
  val color = LocalTextSelectionColors.current.handleColor
  val currentAnchor by rememberUpdatedState(anchor)
  val currentOnDragTo by rememberUpdatedState(onDragTo)
  val left = origin.x + if (end == TerminalSelectionEnd.START) anchor.x - sizePx else anchor.x
  val tag = if (end == TerminalSelectionEnd.START) TERMINAL_SELECTION_START_TAG else TERMINAL_SELECTION_END_TAG
  Canvas(
    Modifier.offset { IntOffset(left.roundToInt(), (origin.y + anchor.y).roundToInt()) }
      .size(HANDLE_SIZE)
      .semantics { testTagsAsResourceId = true }
      .testTag(tag)
      // Keyed on the end alone: the handle moves under the finger as its end follows it, and a
      // restarted detector would lose the drag mid-gesture.
      .pointerInput(end) {
        // No touch slop: a press on a handle can only mean dragging it, and `detectDragGestures`
        // leaves the slop out of the distance it reports, so the end trailed the finger by it.
        awaitEachGesture {
          val down = awaitFirstDown()
          var dragged = currentAnchor
          drag(down.id) { change ->
            dragged += change.positionChange()
            change.consume()
            currentOnDragTo(dragged)
          }
        }
      },
  ) {
    val radius = size.width / 2
    drawCircle(color, radius)
    val corner = if (end == TerminalSelectionEnd.START) Offset(radius, 0f) else Offset.Zero
    drawRect(color, topLeft = corner, size = Size(radius, radius))
  }
}

/** Compose's own `HandleWidth` and `HandleHeight` (`SelectionHandles.kt`). */
private val HANDLE_SIZE = 25.dp
