package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/**
 * Keeping a cell's `TextFieldState` and the notebook in step, in both directions - as two halves,
 * because they belong to two different things.
 *
 * **[adoptRewrittenSource] belongs to the cell and [reportCellEdits] to the field**, and that split
 * is not tidiness: a cell owns *one* text state however it is being shown (`docs/DECISIONS.md`),
 * while the field over it is composed, disposed and *replaced* - a change of type swaps
 * [CodeCellField] for [MarkdownCellField] over the same state. Both halves used to live in the
 * field, and P2f-3 is what proved that wrong: `change-cell-to-heading` is the first command that
 * rewrites a cell's source **and** changes its type, so the new field's counter was seeded already
 * up to date and adopted nothing. The model held `## title`, the chevron said so, and the editor
 * showed the text from before (`evidence/p2f-3-change-to-heading/`).
 */

/**
 * Takes the notebook's copy when a cell operation rewrote this cell's text under it - split, a
 * change to a heading, or the undo of either.
 *
 * **A counter and never a comparison:** the model is routinely one debounce behind whatever was
 * just typed, so a field that is merely ahead looks exactly like one that is behind, and adopting
 * on the difference would eat keystrokes.
 *
 * Seeded with the revision the *state* arrived on, which is why this is called where the state is
 * created: `rememberTextFieldState(cell.source)` has already taken the source, so the first
 * composition adopts nothing and a cell scrolled back into view does not re-adopt.
 *
 * ```
 * val field = rememberTextFieldState(cell.source)
 * adoptRewrittenSource(field, cell.source, sourceRevision)
 * ```
 */
@Composable
internal fun adoptRewrittenSource(state: TextFieldState, source: String, revision: Int) {
  var adopted by rememberSaveable(state) { mutableIntStateOf(revision) }
  LaunchedEffect(state, revision) {
    if (revision == adopted) return@LaunchedEffect
    adopted = revision
    // The caret to the start of what arrived: for a split that is the split point, since the
    // surviving fragment begins exactly where the caret was.
    state.edit {
      replace(0, length, source)
      selection = TextRange(0)
    }
  }
}

/**
 * Reports an edit debounced, because a highlighter runs on every keystroke anyway and the
 * ViewModel does not also need to hear about each one.
 *
 * `drop(1)` skips the initial value, which would otherwise mark an untouched notebook dirty the
 * moment it opened.
 *
 * In the field rather than beside [adoptRewrittenSource] because typing is what a field is: only
 * one is on screen at a time, and only while there is something to type into - a collapsed cell
 * and a rendered markdown cell have a text state and no field at all.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun reportCellEdits(state: TextFieldState, onSourceEdited: (String) -> Unit) {
  val report by rememberUpdatedState(onSourceEdited)
  LaunchedEffect(state) {
    snapshotFlow { state.text.toString() }
      .drop(1)
      .debounce(EDIT_REPORT_MILLIS)
      .collect(report)
  }
  // A debounced edit is lost if the field leaves before the delay elapses, and the markdown
  // cell's rendered/editing toggle makes exactly that reachable: type, tap Run, lose the last
  // keystrokes. Reported straight away on the way out instead.
  DisposableEffect(state) {
    val initial = state.text.toString()
    onDispose {
      val last = state.text.toString()
      if (last != initial) report(last)
    }
  }
}

private const val EDIT_REPORT_MILLIS = 150L
