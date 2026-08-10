package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.Popup
import com.jupydroid.app.domain.port.CompletionResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** What a kernel offered, and the code it was offered for - matched before anything is spliced. */
data class CellCompletions(val query: CompletionQuery, val result: CompletionResult)

/**
 * Asks [completionsAt] about the identifier under the cursor, and answers null the rest of the
 * time.
 *
 * `collectLatest` is the whole cancellation story: a new keystroke cancels both the wait and any
 * request already in flight, which is why [com.jupydroid.app.data.space.RealKernelGateway.complete]
 * needs no timeout of its own.
 */
@Composable
fun rememberCellCompletions(
  state: TextFieldState,
  focused: State<Boolean>,
  completionsAt: suspend (code: String, cursorOffset: Int) -> CompletionResult,
): State<CellCompletions?> {
  val completions = remember { mutableStateOf<CellCompletions?>(null) }
  // Read through rememberUpdatedState rather than keyed on: Kotlin compiles a bound callable
  // reference with invokedynamic, so `viewModel::completionsAt` is a new object every
  // recomposition. Keying on it restarted this effect on every keystroke, which cancelled the
  // settle delay before it could ever elapse and meant no request was ever sent.
  val askKernel by rememberUpdatedState(completionsAt)

  LaunchedEffect(state) {
    snapshotFlow { if (focused.value) queryFor(state) else null }
      .distinctUntilChanged()
      .collectLatest { query ->
        completions.value = null
        if (query == null) return@collectLatest
        // The debounce, and cancelled by the next keystroke rather than racing it.
        delay(SETTLE_MILLIS)
        val result = askKernel(query.code, query.cursorOffset)
        completions.value = if (result.matches.isEmpty()) null else CellCompletions(query, result)
      }
  }
  return completions
}

private fun queryFor(state: TextFieldState): CompletionQuery? {
  val selection = state.selection
  if (!selection.collapsed) return null
  return completionQueryAt(state.text.toString(), selection.start)
}

/**
 * The list of matches, anchored under the cursor.
 *
 * Placed by [CompletionPopupPosition] rather than by a raw offset, so it flips above the caret
 * when the keyboard has taken the room below it.
 *
 * ```
 * CompletionPopup(completions, caret = IntRect(...), onPick = { … })
 * ```
 */
@Composable
fun CompletionPopup(
  completions: CellCompletions,
  caret: IntRect,
  onPick: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  // The keyboard's height, plus whatever the navigation bar takes. `windowSize` does not shrink
  // for the IME under edge-to-edge, so the provider has to be told.
  val bottomInset = WindowInsets.ime.union(WindowInsets.navigationBars).getBottom(LocalDensity.current)
  Popup(
    popupPositionProvider = CompletionPopupPosition(caret, bottomInset),
    onDismissRequest = onDismiss,
  ) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 3.dp,
      modifier = Modifier.semantics { contentDescription = "Completions" },
    ) {
      LazyColumn(modifier = Modifier.widthIn(max = MAX_WIDTH).heightIn(max = MAX_HEIGHT)) {
        items(completions.result.matches, key = { it }) { match ->
          Text(
            text = match,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
              .clickable { onPick(match) }
              .padding(horizontal = 12.dp, vertical = 8.dp)
              // Named, not left as bare text: a soft keyboard offers its own suggestions with
              // the very same words, and a test that matched "print" matched the IME's.
              .semantics { contentDescription = "Completion $match" },
          )
        }
      }
    }
  }
}

/**
 * Splices [match] into [state] over the range the kernel said it replaces.
 *
 * Refuses when the code has moved on since the request, because the range is an offset into the
 * code that was asked about - applying it to anything else edits the wrong characters.
 */
fun applyCompletion(state: TextFieldState, completions: CellCompletions, match: String) {
  if (state.text.toString() != completions.query.code) return
  val start = completions.result.replacedStart
  val end = completions.result.replacedEnd
  if (start !in 0..end || end > state.text.length) return
  state.edit {
    replace(start, end, match)
    placeCursorBeforeCharAt(start + match.length)
  }
}

/** Long enough that a burst of typing is one request, short enough to feel like an answer. */
private const val SETTLE_MILLIS = 250L
private val MAX_WIDTH = 280.dp
private val MAX_HEIGHT = 220.dp
