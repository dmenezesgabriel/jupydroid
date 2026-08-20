package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A conversion is running, drawn where the reference has nothing to draw.
 *
 * The reference's export is a `window.open`, so the browser owns every second after the click and
 * shows its own progress. Here nbconvert runs in the Space - on a tablet, behind a server that may
 * have to be started first - and an export that takes seconds with nothing on screen is
 * indistinguishable from a row that did nothing.
 *
 * Under the app bar rather than over the notebook, so it says *the document is busy* without
 * covering the document.
 */
@Composable
internal fun ExportProgressBar(state: ExportState, modifier: Modifier = Modifier) {
  val format = state.converting ?: return
  LinearProgressIndicator(
    modifier = modifier
      .fillMaxWidth()
      // Named for what is happening and for which format, because that is what a screen reader has
      // to say and what a flow has to wait for - an indeterminate bar carries no text of its own.
      .semantics { contentDescription = exportingLabel(format) },
  )
}

/**
 * What the export came to, shown once - and, when it worked, the one thing left to do with it.
 *
 * A snackbar rather than a dialog, because neither outcome needs a decision: the file is written
 * or the server said why it could not be. [onShown] is what stops a rotation showing it twice -
 * the report is an event, and `LaunchedEffect` re-runs on recomposition with a new key.
 *
 * **The action is on the report rather than in a menu**, because the moment a file is written is
 * the moment somebody wants to send it, and a Share buried in the notebook's overflow would be a
 * second place to look for something that has just happened. Only a [ExportReport.Written] carries
 * one; there is nothing to share about a refusal.
 *
 * `SnackbarDuration.Long` because a snackbar with an action has to outlast the reading of it -
 * the default four seconds is for a message nobody has to act on.
 */
@Composable
internal fun ExportReportSnackbar(
  report: ExportReport?,
  host: SnackbarHostState,
  onShare: (ExportReport.Written) -> Unit,
  onShown: () -> Unit,
) {
  LaunchedEffect(report) {
    if (report == null) return@LaunchedEffect
    val written = report as? ExportReport.Written
    val outcome = host.showSnackbar(
      message = exportReportMessage(report),
      actionLabel = written?.let { SHARE_EXPORT },
      duration = SnackbarDuration.Long,
    )
    if (written != null && outcome == SnackbarResult.ActionPerformed) onShare(written)
    onShown()
  }
}

/** What the snackbar says, named apart so a test and the bar agree on one string. */
internal fun exportReportMessage(report: ExportReport): String = when (report) {
  is ExportReport.Written -> "Exported to ${report.path}"
  // The server's own sentence, which is the only thing that says *why*.
  is ExportReport.Refused -> report.reason
}

/** The reference has no such control - its export is a browser download. See the snackbar above. */
internal const val SHARE_EXPORT = "Share"

/** Named apart so the bar and a test agree on one string. */
internal fun exportingLabel(format: String): String = "Exporting to $format"
