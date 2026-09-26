package com.jupydroid.app.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * reserves the room they need. A line carrying a [Breakpoint] draws its dot in its **own** column,
 * to the left of the numbers (P6a-12) - VS Code's own layout, a `glyphMarginWidth` one row tall
 * sitting before the line-number margin (`editorOptions.ts`). This reverses P6a-6's own choice to
 * reuse the number's column instead of spending the extra width; recorded as a reversal, not a
 * silent change, once the number disappearing under an armed breakpoint measured out as the wrong
 * trade after real use.
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
 *   `KernelDebugger.setBreakpoints`'s own numbering. Fires the moment the release resolves as an
 *   ordinary tap rather than [onGutterLongPress]'s own precondition - see [detectGutterTap].
 * @param onGutterLongPress a long-press landed on an *armed* line's gutter (P6a-16), reporting
 *   that line - opens the condition editor, in place of [onGutterTap] for that one press rather
 *   than beside it. Never fires for a line with no breakpoint: there is nothing yet to attach a
 *   condition to, so a long hold there is still an ordinary tap (arms it), same as a quick one.
 * @param stoppedLine where the kernel is paused, one-based, or null while nothing is - see
 *   [EditorPalette.debugStoppedLine]. Drawn full width, **before** the numbers/dots so those stay
 *   legible over the wash, and before `padding(start = width)` shrinks this `drawBehind`'s own
 *   size - the one reason this lives beside the gutter's geometry rather than in a modifier of
 *   its own.
 */
@Composable
fun Modifier.lineNumbers(
  text: CharSequence,
  layout: TextLayoutResult?,
  style: TextStyle,
  width: Dp,
  breakpoints: List<Breakpoint> = emptyList(),
  diagnostics: List<CellDiagnostic> = emptyList(),
  onGutterTap: (line: Int) -> Unit = {},
  onGutterLongPress: (line: Int) -> Unit = {},
  stoppedLine: Int? = null,
): Modifier {
  if (width <= 0.dp) return this
  val metrics = rememberGutterMetrics(text, style, width, breakpoints, diagnostics)
  return this
    .gutterGestures(metrics, layout, onGutterTap, onGutterLongPress)
    .gutterDrawing(metrics, layout, stoppedLine)
    .diagnosticSemantics(diagnostics)
    .padding(start = width)
}

/** Everything [lineNumbers] and [GutterOverlay] both measure once, so neither keeps its own copy. */
internal class GutterMetrics(
  val starts: List<Int>,
  val numbers: List<TextLayoutResult>,
  val badge: TextLayoutResult,
  val breakpointColumnPx: Int,
  val digitsPx: Int,
  val widthPx: Float,
  val armed: Map<Int, Breakpoint>,
  val currentArmed: State<Map<Int, Breakpoint>>,
  /** Each line's worst diagnostic severity, the smallest LSP number on it, for the mark. */
  val severity: Map<Int, Int>,
  val palette: EditorPalette,
)

@Composable
internal fun rememberGutterMetrics(
  text: CharSequence,
  style: TextStyle,
  width: Dp,
  breakpoints: List<Breakpoint>,
  diagnostics: List<CellDiagnostic> = emptyList(),
): GutterMetrics {
  val starts = logicalLineStarts(text)
  val measurer = rememberTextMeasurer()
  // Measured once per line count rather than per frame: a TextMeasurer's cache is small, and a
  // thirty-line cell would miss it on every draw.
  val numbers = remember(starts.size, style) { List(starts.size) { measurer.measure("${it + 1}", style) } }
  // The condition badge (P6a-16) - IntelliJ's own convention for a conditional breakpoint
  // (`XBreakpointUIUtil.withQuestionBadgeIfNeeded`): the base dot unchanged, a small question-mark
  // badge overlaid near its corner, rather than a different glyph altogether.
  val badgeStyle = style.copy(fontSize = style.fontSize * CONDITION_BADGE_SCALE)
  val badge = remember(badgeStyle) { measurer.measure(CONDITION_BADGE_GLYPH, badgeStyle) }
  // The breakpoint column's own width - one row tall, VS Code's own `glyphMarginWidth` - measured
  // off the digits themselves so it shares their font metrics rather than a second measurement.
  val breakpointColumnPx = numbers.first().size.height
  val digitsPx = with(LocalDensity.current) { (width - NUMBER_GAP).roundToPx() }
  val widthPx = with(LocalDensity.current) { width.toPx() }
  val armed = remember(breakpoints) { breakpoints.associateBy { it.line } }
  // The lowest severity number wins the mark: an error line also carrying warnings is an error.
  val severity = remember(diagnostics) {
    diagnostics.groupingBy { it.line }.fold(Int.MAX_VALUE) { worst, d -> minOf(worst, d.severity) }
  }
  // Read inside the gesture through this rather than closing over `armed` directly, and kept out
  // of the `pointerInput` key below on purpose: `onTap` toggles the very breakpoint a long-press
  // is asking about, and keying on `armed` restarted this coroutine the instant that toggle's own
  // recomposition produced a new map - killing every long-press mid-gesture, right after the tap
  // that starts it disarmed the line. Found live on the tablet, not guessed: the dot vanished on
  // press and the condition dialog never opened, for every hold, long or short.
  val currentArmed = rememberUpdatedState(armed)
  return GutterMetrics(
    starts, numbers, badge, breakpointColumnPx, digitsPx, widthPx, armed, currentArmed, severity,
    editorPalette,
  )
}

/** The tap/long-press gesture both [lineNumbers] and [GutterOverlay] hit-test with. */
internal fun Modifier.gutterGestures(
  metrics: GutterMetrics,
  layout: TextLayoutResult?,
  onGutterTap: (line: Int) -> Unit,
  onGutterLongPress: (line: Int) -> Unit,
): Modifier = pointerInput(metrics.widthPx, layout, metrics.starts) {
  detectGutterTap(
    metrics.widthPx,
    onTap = { y -> layout?.let { onGutterTap(it.lineNumberAt(y, metrics.starts)) } },
    // Asked before `onTap` runs, so a long-press on a line that *was* armed still counts as
    // one even though `onTap`'s own toggle disarms it in the same gesture.
    wasArmed = { y ->
      layout?.let { metrics.currentArmed.value.containsKey(it.lineNumberAt(y, metrics.starts)) } == true
    },
    onLongPress = { y -> layout?.let { onGutterLongPress(it.lineNumberAt(y, metrics.starts)) } },
  )
}

/** The numbers, the breakpoint marks and the stopped-line wash both [lineNumbers] and [GutterOverlay] paint. */
internal fun Modifier.gutterDrawing(metrics: GutterMetrics, layout: TextLayoutResult?, stoppedLine: Int?): Modifier =
  drawBehind {
    if (layout == null) return@drawBehind
    stoppedLine?.let { drawStoppedLineBackground(it, metrics.starts, layout, metrics.palette, size.width) }
    metrics.numbers.forEachIndexed { index, number ->
      val top = layout.getLineTop(layout.getLineForOffset(metrics.starts[index]))
      // Right-aligned past the breakpoint column, so the digits line up with each other rather
      // than with the code - always drawn now, whether or not the line carries a breakpoint.
      val x = (metrics.digitsPx - number.size.width).toFloat()
      drawText(number, topLeft = Offset(x, top))
      metrics.severity[index + 1]?.let { drawDiagnosticMark(it, metrics, top, number.size.height.toFloat()) }
      metrics.armed[index + 1]?.let { drawBreakpointMark(it, metrics, top, number.size.height.toFloat()) }
    }
  }

/**
 * The whole of [line]'s row, edge to edge - every visual row a wrapped logical line spans, not
 * only its first. [starts] are logical-line offsets, so this finds where the *next* logical line
 * begins (or the text's own end) to know how far down the last visual row runs.
 */
private fun DrawScope.drawStoppedLineBackground(
  line: Int,
  starts: List<Int>,
  layout: TextLayoutResult,
  palette: EditorPalette,
  width: Float,
) {
  val index = line - 1
  if (index !in starts.indices) return
  val lastOffset = if (index + 1 < starts.size) starts[index + 1] - 1 else layout.layoutInput.text.length
  val top = layout.getLineTop(layout.getLineForOffset(starts[index]))
  val bottom = layout.getLineBottom(layout.getLineForOffset(lastOffset.coerceAtLeast(starts[index])))
  drawRect(color = palette.debugStoppedLine, topLeft = Offset(0f, top), size = Size(width, bottom - top))
}

/**
 * A filled circle centred in its own column (P6a-12), dim while [Breakpoint.verified] is false,
 * with a small "?" overlaid near its corner (P6a-16) once [Breakpoint.condition] is set -
 * IntelliJ's own `withQuestionBadgeIfNeeded` badges the base icon rather than swapping it for a
 * different one, so a conditional breakpoint still reads as a breakpoint first.
 */
private fun DrawScope.drawBreakpointMark(breakpoint: Breakpoint, metrics: GutterMetrics, top: Float, rowHeight: Float) {
  val palette = metrics.palette
  val color = if (breakpoint.verified) palette.breakpointVerified else palette.breakpointUnverified
  val radius = minOf(metrics.breakpointColumnPx.toFloat(), rowHeight) / DOT_SHRINK
  drawCircle(color = color, radius = radius, center = Offset(metrics.breakpointColumnPx / 2f, top + rowHeight / 2f))
  if (breakpoint.condition != null) {
    val x = (metrics.breakpointColumnPx - metrics.badge.size.width).toFloat().coerceAtLeast(0f)
    val y = (top + rowHeight - metrics.badge.size.height).coerceAtLeast(top)
    drawText(metrics.badge, topLeft = Offset(x, y))
  }
}

/**
 * Fires **exactly one** of [onTap] or [onLongPress] once the press resolves - never both.
 *
 * **This used to fire [onTap] on press**, mirroring JupyterLab's own `domEventHandlers: {
 * mousedown }` (`packages/debugger/src/handlers/editor.ts:230-236`) and VS Code's `onMouseDown`
 * (`breakpointEditorContribution.ts:257`). P6a-16 gave that up: a mouse's down and up are
 * effectively simultaneous, so neither reference had to choose between them, but a touch long
 * enough to be a long-press is, by definition, not simultaneous - firing [onTap] eagerly on press
 * toggled the very breakpoint a long-press was asking to add a condition to, off, in the same
 * gesture (found live on the tablet, not guessed: the dot vanished under every hold, and the
 * dialog it opened was for a breakpoint that no longer existed). A quick tap still reads as
 * instant to a human, since [onTap] now fires the moment the release itself is seen rather than
 * waiting out the long-press timeout first.
 *
 * [PointerEventPass.Initial] runs ancestor-to-descendant, ahead of the field's own tap-to-place
 * pass - this codebase's established way of winning a gesture against a `BasicTextField`
 * (`TerminalPaneScreen.kt`'s `detectTerminalScrollDrag`). A tap outside the gutter is left
 * unconsumed, so placing the caret in the code still works exactly as before. The release is
 * tracked at that same pass, by [down]'s own pointer id, rather than through
 * [androidx.compose.foundation.gestures.waitForUpOrCancellation] - that function's own default
 * pass sees the field's own tap-to-place gesture consume the release first, so it never reported
 * one at all. Classified from the release's own [PointerInputChange.uptimeMillis] against
 * [down]'s, the events' own recorded timestamps, not a coroutine racing a wall-clock timer that a
 * test harness's synchronization overhead can throw off.
 *
 * **[wasArmed] is asked once, at press**, before either callback runs: a long-press only ever
 * means something on a line that already carries a breakpoint - there is nothing yet to attach a
 * condition to on a bare one, so [onTap] (arming it) is what a long hold on a bare line still
 * does.
 */
private suspend fun PointerInputScope.detectGutterTap(
  widthPx: Float,
  onTap: (y: Float) -> Unit,
  wasArmed: (y: Float) -> Boolean,
  onLongPress: (y: Float) -> Unit,
) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (down.position.x > widthPx) return@awaitEachGesture
    down.consume()
    val armed = wasArmed(down.position.y)
    var change = down
    while (change.pressed) {
      change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
    }
    val held = change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
    if (armed && held) onLongPress(down.position.y) else onTap(down.position.y)
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
 * How far the code is pushed right to make room for the breakpoint column and the numbers, or
 * **zero** when [shown] is false - which is `notebook:toggle-all-cell-line-numbers` turned off.
 *
 * The command flips one boolean for all three cell types at once
 * (`packages/notebook/src/actions.tsx:1792-1812`), so every field asks this the same way and the
 * caller supplies the one flag.
 *
 * **Includes the breakpoint column** (P6a-12) - one row tall, measured off the same digits so it
 * shares their font metrics, sitting before them the way VS Code's own glyph margin sits before
 * its line numbers.
 */
@Composable
fun gutterWidth(text: CharSequence, style: TextStyle, shown: Boolean = true): Dp {
  if (!shown) return 0.dp
  val measurer = rememberTextMeasurer()
  val lineCount = logicalLineStarts(text).size
  val digits = remember(lineCount, style) { measurer.measure("0".repeat(gutterDigits(lineCount)), style) }
  return with(LocalDensity.current) { digits.size.height.toDp() + digits.size.width.toDp() } + NUMBER_GAP
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

/** The condition badge's own glyph and size, relative to the line numbers' own font size. */
private const val CONDITION_BADGE_GLYPH = "?"
private const val CONDITION_BADGE_SCALE = 0.7f
