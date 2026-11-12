package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jupydroid.app.theme.FocusRingTextButton

/**
 * What the last replace had to say, and - while there is something to take back - the offer to.
 *
 * `clearMessage()` then `dom.append(messageEl, afterReplaceAllMessage)` (`searchView.ts:842-845`) -
 * above the tree and below the widgets, which is `settledMessages`' place too. It replaces those
 * messages rather than joining them: after a successful replace the tree is empty, and *"No results
 * found"* beside *"Replaced 2 occurrences…"* would be two answers to one question.
 *
 * A refusal is drawn in `error` and a report is not - the same split
 * `NotebookBodyMessages.SaveFailureBanner` already makes, one saying something went wrong and the
 * other saying something worked. Which of the two this is is read off [ReplaceReport.undoable]
 * rather than from a flag beside it: only a replace that wrote something has anything to take back.
 *
 * **The undo is this panel's, and the reference has no such control.** A replace-all there is one
 * entry on the workspace undo stack (`MultiModelEditStackElement`, `editStack.ts:319-330`), taken
 * back with Ctrl+Z from any editor it touched, and the search view never learns it happened. There
 * is no keyboard here to press, and the notebook's own history deliberately does not carry a
 * workspace replace - `WorkspaceReplace`'s docstring says why: one command undoable twice, in two
 * scopes that could then disagree about what the document is. So the panel that performed it is what
 * offers to take it back.
 *
 * **Beside the report rather than in a snackbar**, which is the choice between two Material idioms
 * and was decided on lifetime. A snackbar is the classic surface for a reversible bulk action, and
 * it carries the message *and the only route to the undo* away after a few seconds - where the
 * reference's undo stays available until the file is edited. Here it lives exactly as long as the
 * sentence that offers it: until the next walk, which is `clearMessage()`'s own lifetime.
 *
 * ```
 * state.report?.let { SearchReplaceReport(it, onUndo = viewModel.replaceAll::undo) }
 * ```
 */
@Composable
fun SearchReplaceReport(report: ReplaceReport, onUndo: () -> Unit, modifier: Modifier = Modifier) {
  val refused = report.undoable == null
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      report.lines.forEach { line ->
        PanelMessage(
          text = line,
          color = if (refused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    // **Absent rather than disabled**, where Replace All is the other way round: a disabled Replace
    // All states its own precondition in its label ([REPLACE_ALL_DISABLED]), and an Undo beside a
    // refusal would have nothing to state - the refusal wrote nothing, so there is nothing to undo.
    if (!refused) FocusRingTextButton(onClick = onUndo) { Text(UNDO_THE_REPLACE) }
  }
}

/**
 * What the undo answers to - **not** a bare *"Undo"*, and the reason is the notebook's own.
 *
 * `EditorTopBar` already ships *"Undo cell operation"*, and at 840dp that bar and this panel are two
 * panes of one window. *"Undo"* is a prefix of it, so a matcher for one would find both; naming the
 * scope is what [REPLACE_ALL_IN_WORKSPACE] does a few dp above for the same reason.
 *
 * There is no redo beside it, and nothing asks for one: `MultiModelEditStackElement` has both, and
 * this has the half a panel can reach.
 */
internal const val UNDO_THE_REPLACE = "Undo this replace"
