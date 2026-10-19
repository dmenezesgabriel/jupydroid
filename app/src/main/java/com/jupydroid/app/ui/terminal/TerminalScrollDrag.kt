package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import com.jupydroid.app.terminal.TerminalGrid
import kotlin.math.abs

/**
 * A drag over the terminal scrolls it into history - and must win
 * that decision *before* `TerminalInputField`'s own `BasicTextField` does. Measured on the
 * tablet, not guessed: `Modifier.scrollable` placed on either the canvas or its ancestor never
 * once saw a real drag, because the field's own touch handling runs on the default
 * [PointerEventPass.Main] - the pass every gesture detector in this codebase already uses - which
 * a descendant processes *before* an ancestor's or a sibling's own Main-pass handler ever would,
 * and the field consumes movement unconditionally rather than only once a drag is confirmed.
 * [PointerEventPass.Initial] runs ancestor-to-descendant, ahead of the field's own pass, so
 * consuming here is what actually pre-empts it; a plain tap (no travel past
 * [PointerInputScope.viewConfiguration]'s own touch slop) is left unconsumed, so tap-to-focus
 * still reaches the field beneath.
 *
 * [onDragBy] receives the drag's own screen-space `y` delta (positive when a finger moves down),
 * negated before it reaches [TerminalPaneViewModel.scrollBy] - a downward drag reveals what is
 * *above* the live screen, i.e. history, which is [TerminalGrid.clampedTopRow]'s negative
 * direction, not its positive one.
 *
 * Drag only, not fling - this step's own gate is "scroll back... and show it", not momentum. A
 * fling is a named follow-up rather than silently dropped.
 */
internal suspend fun PointerInputScope.detectTerminalScrollDrag(onDragBy: (Float) -> Unit) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    var overSlop = 0f
    var dragging = false
    while (true) {
      val event = awaitPointerEvent(pass = PointerEventPass.Initial)
      val change = event.changes.firstOrNull { it.id == down.id }
      if (change == null || change.changedToUpIgnoreConsumed()) break
      val delta = change.positionChange().y
      if (dragging) {
        change.consume()
        onDragBy(-delta)
      } else {
        overSlop += delta
        if (abs(overSlop) > viewConfiguration.touchSlop) dragging = true
      }
    }
  }
}
