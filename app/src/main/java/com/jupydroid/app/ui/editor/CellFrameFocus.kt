package com.jupydroid.app.ui.editor

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.jupydroid.extension.api.LocalFocusRingColor
import com.jupydroid.extension.api.drawFocusRing

/**
 * What the instrumented tests and the Maestro flows find the head cell's frame by - the
 * `terminal-input` convention.
 */
internal const val HEAD_CELL_FRAME_TAG = "head-cell-frame"

/** Which cell a frame wraps, so a test can address a frame by id rather than by position. */
internal val CellFrameIdKey = SemanticsPropertyKey<String>("CellFrameId")
internal var SemanticsPropertyReceiver.cellFrameId by CellFrameIdKey

/**
 * Whether the frame's own target holds focus right now. `SemanticsProperties.Focused` cannot
 * answer it: `FocusableNode.applySemantics` writes `focused` only when the node's semantics are
 * invalidated, and a programmatic `requestFocus` schedules no such invalidation, so the flag can
 * stay false long after `onFocusChanged` reports `isFocused`. This property reads the live
 * `selfFocused` state, so the test sees the same flag the ring and the enter-edit gate use.
 */
internal val FrameOwnsFocusKey = SemanticsPropertyKey<Boolean>("FrameOwnsFocus")
internal var SemanticsPropertyReceiver.frameOwnsFocus by FrameOwnsFocusKey

/**
 * The head cell's frame as the one focusable surface a notebook has in command mode.
 *
 * JupyterLab gives every cell node `tabIndex="-1"` and the *active* cell `tabIndex="0"`
 * (`packages/notebook/src/widget.ts:2734-2750`) - one Tab stop, so the browser's focus walk
 * reaches the notebook the way ours does: a single focusable, and it is the selected cell's.
 * The modifier sits on the frame rather than on the editor, because a field's own focus is the
 * edit mode it reports; the frame is the focus a cell can hold *without* editing.
 *
 * [onFocus] gets every `isFocused` change - the dispatch site's `frameOwnsFocus` flag that
 * reproduces the `.jp-Cell:focus` selector the `Enter` binding is scoped to (`tracker.json:447`).
 * [onEnter] is `_evtFocusIn` and fires only on the subtree's `hasFocus` *rising* edge: focus
 * re-entering the cell from outside. A descendant handing focus up to the frame - the editor's
 * Escape, or the focus system's own restore while an edit-mode recomposition settles - arrives
 * with `hasFocus` already held, which is the `relatedTarget` inside the cell that upstream's
 * focus-in handler ignores.
 *
 * The ring is [drawFocusRing] gated on `isFocused` and the keyboard input mode, **not** the
 * container rule [com.jupydroid.extension.api.focusRing] encodes: `hasFocus` would ring the whole
 * cell while a `⋮` inside it is the one actually focused. And where the indicator goes on a
 * JupyterLab cell is the border the focus gives it (`jp-mod-focused`), which this draws.
 */
@Composable
internal fun Modifier.headCellFrame(
  headFocus: FocusRequester,
  onFocus: (self: Boolean) -> Unit,
  onEnter: () -> Unit,
): Modifier {
  var selfFocused by remember { mutableStateOf(false) }
  var hadFocus by remember { mutableStateOf(false) }
  val ring = LocalFocusRingColor.current
  val inputMode = LocalInputModeManager.current
  return testTag(HEAD_CELL_FRAME_TAG)
    .focusRequester(headFocus)
    .onFocusChanged { focus ->
      val entered = focus.hasFocus && !hadFocus
      hadFocus = focus.hasFocus
      selfFocused = focus.isFocused
      if (entered) onEnter()
      onFocus(focus.isFocused)
    }
    .focusable()
    .semantics { frameOwnsFocus = selfFocused }
    .drawWithContent {
      drawContent()
      if (selfFocused && inputMode.inputMode == InputMode.Keyboard) drawFocusRing(ring)
    }
}

/**
 * A press landing anywhere in a cell - upstream's `_evtMouseDown` (`packages/notebook/src/
 * widget.ts:3565-3600`), which settles the active cell on the *down* from wherever in the cell it
 * landed, `⋮` button included.
 *
 * The pass and the order are load-bearing. `PointerEventPass.Initial` delivers the down to a parent
 * *before* its children - the DOM capture `_evtMouseDown` rides - and a field's own caret request
 * goes out in the *Main* pass on the same down, after this has already activated. `COMMAND` then
 * `EDIT` is upstream's order wherever the press was: a tap into a cell's source still opens it,
 * and only the chrome (padding, no child) leaves the press ending in `COMMAND`.
 *
 * Applied on every cell frame except while a run is being marked, where the tick's own
 * gestures mean something else - the same split `mark != null` already draws. The [cellFrameId]
 * it also sets is what a test finds the frame by at all.
 */
@Composable
internal fun Modifier.cellChromeTap(enabled: Boolean, cellId: String, onChromeTap: () -> Unit): Modifier {
  val tap by rememberUpdatedState(onChromeTap)
  val tapped = if (enabled) {
    pointerInput(Unit) {
      awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        tap()
      }
    }
  } else {
    this
  }
  return semantics { cellFrameId = cellId }.then(tapped)
}

/**
 * Whether [cellId]'s item is composed and laid out right now - the precondition for a
 * `FocusRequester.requestFocus` that must not throw on a cell the list has not reached.
 */
internal fun LazyListState.composesCell(cellId: String?): Boolean =
  cellId != null && layoutInfo.visibleItemsInfo.any { it.key == cellId }
