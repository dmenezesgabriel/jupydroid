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
 * ancestor sees it. Nothing is consumed until the press has become a long press, so a tap still
 * focuses the field and a drag still scrolls; after it, the rest of the gesture is consumed so
 * lifting the finger is not also a tap.
 *
 * [onTap] is a press lifted before the timeout without travelling, which is how `TerminalView`
 * ends a selection: a tap outside it while selecting.
 */
internal suspend fun PointerInputScope.detectTerminalLongPress(onLongPress: (Offset) -> Unit, onTap: () -> Unit) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitTapOrTravel(down) }
    if (endedEarly == PressEnd.TAP) onTap()
    if (endedEarly != null) return@awaitEachGesture
    onLongPress(down.position)
    do {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
  }
}

/** How a press ended before it became a long press. */
private enum class PressEnd { TAP, TRAVEL }

private suspend fun AwaitPointerEventScope.awaitTapOrTravel(down: PointerInputChange): PressEnd {
  while (true) {
    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
    val end = when {
      change == null -> PressEnd.TRAVEL
      change.changedToUpIgnoreConsumed() -> PressEnd.TAP
      (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> PressEnd.TRAVEL
      else -> null
    }
    if (end != null) return end
  }
}
