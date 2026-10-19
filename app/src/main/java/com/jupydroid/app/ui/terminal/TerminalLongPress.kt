package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed

/**
 * A press held past the platform's long-press timeout without travelling past its touch slop -
 * `GestureDetector.onLongPress`, which `TerminalView` arms its selection from.
 *
 * **On [PointerEventPass.Initial], like `detectTerminalScrollDrag`**, and for the same reason: the
 * invisible input field over the canvas takes every pointer event on the Main pass before an
 * ancestor sees it. Nothing is consumed until the press has become a long press, so a tap [onTap] leaves still
 * focuses the field and a drag still scrolls; after it, the rest of the gesture is consumed so
 * lifting the finger is not also a tap.
 *
 * [onTap] is a press lifted before the timeout without travelling, at the point it went down: how
 * `TerminalView` ends a selection, and how a printed link is followed. It answers whether it took
 * the tap; a taken tap's lift is consumed, so the field under it does not take it as its own tap,
 * and raises no keyboard under what the tap opened.
 */
internal suspend fun PointerInputScope.detectTerminalLongPress(
  onLongPress: (Offset) -> Unit,
  onTap: (Offset) -> Boolean,
) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitTapOrTravel(down) }
    if (endedEarly is PressEnd.Tap && onTap(down.position)) endedEarly.lift.consume()
    if (endedEarly != null) return@awaitEachGesture
    onLongPress(down.position)
    do {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
  }
}

/** How a press ended before it became a long press: lifted where it went down, or moved away. */
private sealed interface PressEnd {
  class Tap(val lift: PointerInputChange) : PressEnd

  data object Travel : PressEnd
}

private suspend fun AwaitPointerEventScope.awaitTapOrTravel(down: PointerInputChange): PressEnd {
  while (true) {
    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
    val end = when {
      change == null -> PressEnd.Travel
      change.changedToUpIgnoreConsumed() -> PressEnd.Tap(change)
      (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> PressEnd.Travel
      else -> null
    }
    if (end != null) return end
  }
}
