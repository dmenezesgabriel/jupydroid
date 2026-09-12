package com.jupydroid.app.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.theme.EditorPalette
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette

/**
 * Draws a line number beside every logical line of the text this modifier decorates, and
 * reserves the room they need. A line carrying a [Breakpoint] draws that instead of its number -
 * P6a-6's own reuse of "the existing line-number column" rather than a second gutter, named as a
 * departure in `docs/PLAN.md`.
 *
 * Positions come from the field's own [layout] rather than from counting newlines, which is the
 * only way a wrapped line keeps one number: a line that takes two visual rows must not push the
 * next number two rows down.
 *
 * ```
 * BasicTextField(state = state, modifier = Modifier.lineNumbers(state.text, layout, style, width))
 * ```
 *
 * **A [width] of zero is the gutter turned off** - `notebook:toggle-all-cell-line-numbers`. The
 * width is the one authority on whether there are numbers, rather than a second flag beside it:
 * the completion popup anchors against this same edge, so a field that drew no numbers while the
 * popup still allowed for them is a disagreement this shape cannot have.
 *
 * @param layout the field's latest [TextLayoutResult], or null before it has laid out once.
 * @param width from [gutterWidth]; passed in rather than computed here because the completion
 *   popup anchors against the same edge and the two must not disagree.
 * @param onGutterTap a tap landed in the gutter, reporting the **one-based** logical line it hit -
 *   `KernelDebugger.setBreakpoints`'s own numbering. Fires on press, matching both references'
 *   own `mousedown` handlers rather than waiting for a release.
 */
@Composable
fun Modifier.lineNumbers(
  text: CharSequence,
  layout: TextLayoutResult?,
  style: TextStyle,
  width: Dp,
  breakpoints: List<Breakpoint> = emptyList(),
  onGutterTap: (line: Int) -> Unit = {},
): Modifier {
  if (width <= 0.dp) return this
  val starts = logicalLineStarts(text)
  val measurer = rememberTextMeasurer()
  // Measured once per line count rather than per frame: a TextMeasurer's cache is small, and a
  // thirty-line cell would miss it on every draw.
  val numbers = remember(starts.size, style) { List(starts.size) { measurer.measure("${it + 1}", style) } }
  val digitsPx = with(LocalDensity.current) { (width - NUMBER_GAP).roundToPx() }
  val widthPx = with(LocalDensity.current) { width.toPx() }
  val armed = remember(breakpoints) { breakpoints.associateBy { it.line } }
  val palette = editorPalette

  return this
    .pointerInput(widthPx, layout, starts) {
      detectGutterTap(widthPx) { y -> layout?.let { onGutterTap(it.lineNumberAt(y, starts)) } }
    }
    .drawBehind {
      if (layout == null) return@drawBehind
      numbers.forEachIndexed { index, number ->
        val top = layout.getLineTop(layout.getLineForOffset(starts[index]))
        val breakpoint = armed[index + 1]
        if (breakpoint == null) {
          // Right-aligned, so the digits line up with each other rather than with the code.
          val x = (digitsPx - number.size.width).toFloat()
          drawText(number, topLeft = Offset(x, top))
        } else {
          drawBreakpointDot(breakpoint, palette, digitsPx, top, number.size.height.toFloat())
        }
      }
    }
    .padding(start = width)
}

/** A filled circle centred in the gutter, dim while [Breakpoint.verified] is false. */
private fun DrawScope.drawBreakpointDot(
  breakpoint: Breakpoint,
  palette: EditorPalette,
  digitsPx: Int,
  top: Float,
  rowHeight: Float,
) {
  val color = if (breakpoint.verified) palette.breakpointVerified else palette.breakpointUnverified
  val radius = minOf(digitsPx.toFloat(), rowHeight) / DOT_SHRINK
  drawCircle(color = color, radius = radius, center = Offset(digitsPx / 2f, top + rowHeight / 2f))
}

/**
 * Fires on **press**, mirroring JupyterLab's own `domEventHandlers: { mousedown }`
 * (`packages/debugger/src/handlers/editor.ts:230-236`) and VS Code's `onMouseDown`
 * (`breakpointEditorContribution.ts:257`) - neither reference waits for a release.
 *
 * [PointerEventPass.Initial] runs ancestor-to-descendant, ahead of the field's own tap-to-place
 * pass - this codebase's established way of winning a gesture against a `BasicTextField`
 * (`TerminalPaneScreen.kt`'s `detectTerminalScrollDrag`). A tap outside the gutter is left
 * unconsumed, so placing the caret in the code still works exactly as before.
 */
private suspend fun PointerInputScope.detectGutterTap(widthPx: Float, onTap: (y: Float) -> Unit) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (down.position.x <= widthPx) {
      down.consume()
      onTap(down.position.y)
    }
  }
}

/**
 * The **one-based** logical line whose row contains [y], the inverse of this modifier's own
 * `getLineTop(getLineForOffset(...))` pairing above.
 *
 * [starts] are logical-line offsets; [TextLayoutResult]'s own line indices are visual rows, so a
 * wrapped logical line owns more than one of them - this finds the last logical line whose first
 * visual row is at or before the tapped one, which is exactly the line a number was drawn for.
 */
private fun TextLayoutResult.lineNumberAt(y: Float, starts: List<Int>): Int {
  val visualLine = getLineForVerticalPosition(y)
  val visualStarts = starts.map { getLineForOffset(it) }
  return (visualStarts.indexOfLast { it <= visualLine } + 1).coerceAtLeast(1)
}

/**
 * How far the code is pushed right to make room for the numbers, or **zero** when [shown] is
 * false - which is `notebook:toggle-all-cell-line-numbers` turned off.
 *
 * The command flips one boolean for all three cell types at once
 * (`packages/notebook/src/actions.tsx:1792-1812`), so every field asks this the same way and the
 * caller supplies the one flag.
 */
@Composable
fun gutterWidth(text: CharSequence, style: TextStyle, shown: Boolean = true): Dp {
  if (!shown) return 0.dp
  val measurer = rememberTextMeasurer()
  val lineCount = logicalLineStarts(text).size
  val digits = remember(lineCount, style) {
    measurer.measure("0".repeat(gutterDigits(lineCount)), style).size.width
  }
  return with(LocalDensity.current) { digits.toDp() } + NUMBER_GAP
}

/**
 * Where each logical line begins: offset 0, then every offset just past a newline.
 *
 * Offsets into the source rather than the laid-out text, which is the same thing here - the
 * output transformation only styles, so it inserts and removes nothing.
 */
fun logicalLineStarts(text: CharSequence): List<Int> {
  val starts = mutableListOf(0)
  text.forEachIndexed { index, char -> if (char == '\n') starts += index + 1 }
  return starts
}

/** How wide the gutter is, in digits. Two from the start so nine-to-ten does not shift the code. */
fun gutterDigits(lineCount: Int): Int = maxOf(MIN_DIGITS, lineCount.toString().length)

private const val MIN_DIGITS = 2
private val NUMBER_GAP = Spacing.sm

/** A dot smaller than the row it sits in, so it reads as a mark rather than filling the gutter. */
private const val DOT_SHRINK = 2.5f
