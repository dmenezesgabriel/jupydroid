package com.jupydroid.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupydroid.app.domain.model.ErrorLogSummary
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.MarginedAlertDialog

/**
 * What the app has recorded about its own crashes and errors, and the one thing a person can do to it
 * here: clear it, after being told how much that frees.
 *
 * ```
 * DiagnosticsSection(viewModel { DiagnosticsViewModel(appContainer.errorLog) })
 * ```
 */
@Composable
internal fun DiagnosticsSection(viewModel: DiagnosticsViewModel) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  ListItem(
    headlineContent = { Text(DIAGNOSTICS) },
    supportingContent = { Text(diagnosticsSupportingText(state)) },
  )
  val loaded = state as? DiagnosticsUiState.Loaded
  val clearable = loaded != null && loaded.summary.entryCount > 0
  // `clickable(enabled = false)` states the row is disabled to a screen reader but draws it like any other,
  // so a person is left pressing a row that does nothing; the dimming is what they see.
  ListItem(
    headlineContent = { Text(CLEAR_LOGS) },
    modifier = Modifier
      .alpha(if (clearable) 1f else DISABLED_ROW_ALPHA)
      .clickable(enabled = clearable, role = Role.Button, onClick = viewModel::askToClear),
  )
  if (loaded?.confirmingClear == true) {
    ClearLogsDialog(loaded.summary, onConfirm = viewModel::confirmClear, onDismiss = viewModel::dismissClear)
  }
}

@Composable
private fun ClearLogsDialog(summary: ErrorLogSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  MarginedAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("$CLEAR_LOGS?") },
    text = { Text("Clear ${summaryInWords(summary)}? This cannot be undone.") },
    confirmButton = {
      FocusRingTextButton(
        onClick = onConfirm,
        modifier = Modifier.semantics { contentDescription = "Confirm $CLEAR_LOGS" },
      ) {
        Text(CLEAR_LOGS, color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = { FocusRingTextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

private fun diagnosticsSupportingText(state: DiagnosticsUiState): String = when (state) {
  DiagnosticsUiState.Loading -> "Reading the error log…"
  is DiagnosticsUiState.Failed -> state.message
  is DiagnosticsUiState.Loaded -> "${entriesText(state.summary.entryCount)} · ${byteSizeText(state.summary.sizeBytes)}"
}

/** "2 entries (1.2 KB)" - the confirmation's own phrasing, since "2 entries · 1.2 KB" reads badly after "Clear". */
private fun summaryInWords(summary: ErrorLogSummary): String =
  "${entriesText(summary.entryCount)} (${byteSizeText(summary.sizeBytes)})"

private fun entriesText(count: Int): String = if (count == 1) "1 entry" else "$count entries"

/** The section's title; the log is the app's own record of what went wrong on this device. */
internal const val DIAGNOSTICS = "Diagnostics"

/** The row that deletes the log, and the confirmation's button, which share this label. */
internal const val CLEAR_LOGS = "Clear logs"

/** Material's disabled-content opacity, which a `ListItem` does not apply on its own. */
private const val DISABLED_ROW_ALPHA = 0.38f
