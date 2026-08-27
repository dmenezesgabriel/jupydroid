package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * What the last replace had to say, where the walk's own messages go.
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
 * ```
 * state.report?.let { SearchReplaceReport(it) }
 * ```
 */
@Composable
fun SearchReplaceReport(report: ReplaceReport, modifier: Modifier = Modifier) {
  val refused = report.undoable == null
  Column(modifier = modifier) {
    report.lines.forEach { line ->
      PanelMessage(
        text = line,
        color = if (refused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
