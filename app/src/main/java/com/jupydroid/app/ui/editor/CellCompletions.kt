package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.jupydroid.app.domain.port.CompletionMatch
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.theme.EditorPalette
import com.jupydroid.app.theme.LocalEditorPalette
import com.jupydroid.app.theme.Spacing
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
 *
 * @param enabled `docs/PLAN.md`'s P6c-6 - false skips the pipeline entirely rather than running it
 *   to completion and discarding the answer, [lineNumbers]'s own `if (width <= 0.dp) return this`
 *   precedent for an early return ahead of other composable state.
 */
@Composable
fun rememberCellCompletions(
  state: TextFieldState,
  focused: State<Boolean>,
  completionsAt: suspend (code: String, cursorOffset: Int) -> CompletionResult,
  enabled: Boolean = true,
): State<CellCompletions?> {
  val completions = remember { mutableStateOf<CellCompletions?>(null) }
  if (!enabled) return completions
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
  onPick: (CompletionMatch) -> Unit,
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
      tonalElevation = POPUP_ELEVATION,
      modifier = Modifier.semantics { contentDescription = "Completions" },
    ) {
      LazyColumn(modifier = Modifier.widthIn(max = MAX_WIDTH).heightIn(max = MAX_HEIGHT)) {
        items(completions.result.matches, key = { it.insert }) { match ->
          CompletionRow(match, onPick)
        }
      }
    }
  }
}

/**
 * One offer: the kind glyph in its `symbolIcon.*` colour, then the server's `label` - which is
 * what the row must read, signature parens and all, while [CompletionMatch.insert] stays the
 * text accepting it writes.
 */
@Composable
private fun CompletionRow(match: CompletionMatch, onPick: (CompletionMatch) -> Unit) {
  val palette = LocalEditorPalette.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .clickable { onPick(match) }
      .padding(horizontal = Spacing.md, vertical = Spacing.sm)
      // Named, not left as bare text: a soft keyboard offers its own suggestions with the very
      // same words, and a test that matched "print" matched the IME's.
      .semantics { contentDescription = "Completion ${match.label}" },
  ) {
    completionBadge(match.kind)?.let { badge ->
      Text(
        text = badge.letter,
        style = codeTextStyle(),
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = badgeColour(palette, badge.family),
        modifier = Modifier.width(BADGE_WIDTH),
      )
    }
    Text(text = match.label, style = codeTextStyle(), color = MaterialTheme.colorScheme.onSurface)
  }
}

/** The `symbolIcon.*` family colour - [PLAIN] is the registered `foreground` fallback. */
private fun badgeColour(palette: EditorPalette, family: CompletionBadgeFamily): Color = when (family) {
  CompletionBadgeFamily.CALLABLE -> palette.symbolCallable
  CompletionBadgeFamily.CLASSLIKE -> palette.symbolClasslike
  CompletionBadgeFamily.MEMBER -> palette.symbolMember
  CompletionBadgeFamily.PLAIN -> palette.syntax.plain
}

/**
 * Splices [match]'s *insert text* into [state] over the range the answer said it replaces -
 * never the label, which carries the signature the user was only meant to read.
 *
 * Refuses when the code has moved on since the request, because the range is an offset into the
 * code that was asked about - applying it to anything else edits the wrong characters.
 */
fun applyCompletion(state: TextFieldState, completions: CellCompletions, match: CompletionMatch) {
  if (state.text.toString() != completions.query.code) return
  val start = completions.result.replacedStart
  val end = completions.result.replacedEnd
  if (start !in 0..end || end > state.text.length) return
  state.edit {
    replace(start, end, match.insert)
    placeCursorBeforeCharAt(start + match.insert.length)
  }
}

/** Long enough that a burst of typing is one request, short enough to feel like an answer. */
private const val SETTLE_MILLIS = 250L

/**
 * Wide enough for a fully-qualified name and no wider: a popup that grows with its longest match
 * would cover the cell it is completing, which is the one thing the reader needs to see.
 */
private val MAX_WIDTH = 280.dp

/**
 * About six rows at [CodeText]'s size. The list scrolls past that rather than
 * growing, so the popup's footprint does not depend on how many matches the kernel found.
 */
private val MAX_HEIGHT = 220.dp

/**
 * `ElevationTokens.Level2`, restated because `Surface` takes a `Dp` and not a token. The popup
 * sits over a cell that already has a background, so it needs a tonal step to separate from it.
 */
private val POPUP_ELEVATION = 3.dp

/** A monogram badge's column - wide enough for `M`, narrow enough to read as an icon. */
private val BADGE_WIDTH = 16.dp
