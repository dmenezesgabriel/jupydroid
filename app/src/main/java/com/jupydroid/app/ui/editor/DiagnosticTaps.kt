package com.jupydroid.app.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

/**
 * The line whose finding a tap at [point] reads, or null when it lands on none.
 *
 * A span narrower than [minWidthPx] is widened sideways to it, evenly: a hint's span is two
 * characters, 16.5dp, and a row is too short to widen the other way without taking the next
 * line's taps.
 *
 * ```
 * diagnosticLineAt(spans, Offset(88f, 27f), minWidthPx = 48f) // 2, for line 2's span at x=100..116
 * ```
 */
internal fun diagnosticLineAt(spans: List<DiagnosticSpanRect>, point: Offset, minWidthPx: Float): Int? =
  spans.firstOrNull { widenedTo(it.rect, minWidthPx).contains(point) }?.line

private fun widenedTo(rect: Rect, minWidthPx: Float): Rect {
  val each = (minWidthPx - rect.width).coerceAtLeast(0f) / 2f
  return Rect(rect.left - each, rect.top, rect.right + each, rect.bottom)
}

/**
 * Reports the line of the finding a tap on its squiggle reads - VS Code's marker hover
 * (`markerHoverParticipant.ts` at `vscode@3f3380c668d0`), which a phone performs as a tap.
 *
 * Observes at [PointerEventPass.Initial] and **never consumes**: the field's own tap still places
 * the caret, so reading a finding does not cost the position the finger chose. A press that moves
 * past the touch slop is a scroll and one held past the long-press timeout is a selection;
 * neither reads a finding.
 *
 * Attach after the gutter's padding, beside [diagnosticSquiggles], so the pointer's coordinates
 * are the text layout's own - and a horizontal scroll carries the hit areas with the text.
 *
 * ```
 * fieldModifier.diagnosticSquiggles(diagnostics, layout).diagnosticTaps(diagnostics, layout) { detailLine = it }
 * ```
 */
internal fun Modifier.diagnosticTaps(
  diagnostics: List<CellDiagnostic>,
  layout: TextLayoutResult?,
  onTap: (line: Int) -> Unit,
): Modifier {
  if (diagnostics.isEmpty()) return this
  return pointerInput(diagnostics, layout) {
    val minWidthPx = TAP_TARGET_WIDTH.toPx()
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      if (!awaitTapRelease(down) || layout == null) return@awaitEachGesture
      diagnosticLineAt(diagnosticSpanRects(diagnostics, layout), down.position, minWidthPx)?.let(onTap)
    }
  }
}

/** Whether [down] ends as a tap: released within the touch slop, before the long-press timeout. */
private suspend fun AwaitPointerEventScope.awaitTapRelease(down: PointerInputChange): Boolean {
  var change: PointerInputChange? = down
  while (change != null && change.pressed) {
    change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
      ?.takeIf { (it.position - down.position).getDistance() <= viewConfiguration.touchSlop }
  }
  return change != null && change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
}

/** Material's 48dp touch target, taken sideways only. */
private val TAP_TARGET_WIDTH = 48.dp
