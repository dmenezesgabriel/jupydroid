package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
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
 * What the export came to, shown once.
 *
 * A snackbar rather than a dialog, because neither outcome needs a decision: the file is written
 * or the server said why it could not be. [onShown] is what stops a rotation showing it twice -
 * the report is an event, and `LaunchedEffect` re-runs on recomposition with a new key.
 */
@Composable
internal fun ExportReportSnackbar(report: String?, host: SnackbarHostState, onShown: () -> Unit) {
  LaunchedEffect(report) {
    if (report == null) return@LaunchedEffect
    host.showSnackbar(report)
    onShown()
  }
}

/** Named apart so the bar and a test agree on one string. */
internal fun exportingLabel(format: String): String = "Exporting to $format"
