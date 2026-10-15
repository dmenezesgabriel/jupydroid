package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.Checkpoint
import com.jupydroid.app.domain.port.DocumentCheckpoints
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A question open over one document: the ask itself, and the document it is about. */
private class PendingCheckpointAsk(val document: OpenDocument, val ask: CheckpointAsk)

/**
 * The Revert to Checkpoint a tab's menu starts: says there is none, or asks with the reference's
 * words and the checkpoint's time, then restores and reads the file back; a restore the disk
 * refuses says why (jupyterlab@7f0e690 `packages/docmanager-extension/src/index.tsx`,
 * `CommandIDs.restoreCheckpoint`, `Private.createRevertConfirmNode`).
 *
 * Mounted once by the pane, like `rememberTabSaveAs`, so the dialogs outlive the menu that started
 * them. Answers with the starter.
 *
 * ```
 * val revert = rememberTabRevertToCheckpoint(documentCheckpoints)
 * revert(document)
 * ```
 */
@Composable
internal fun rememberTabRevertToCheckpoint(checkpoints: DocumentCheckpoints): (OpenDocument) -> Unit {
  var pending by remember { mutableStateOf<PendingCheckpointAsk?>(null) }
  val scope = rememberCoroutineScope()
  pending?.let { open ->
    val dismiss = { pending = null }
    when (val ask = open.ask) {
      is CheckpointAsk.NoCheckpoints -> CheckpointMessageDialog(
        title = "No checkpoints",
        message = "No checkpoints are available for this ${ask.documentType}.",
        onDismiss = dismiss,
      )
      is CheckpointAsk.ToRevert -> RevertToCheckpointDialog(ask.documentType, ask.checkpoint, onCancel = dismiss) {
        scope.launch {
          pending = revertToCheckpoint(open.document, ask.checkpoint, checkpoints)
            ?.let { PendingCheckpointAsk(open.document, CheckpointAsk.Refused(ask.documentType, it)) }
        }
      }
      is CheckpointAsk.Refused -> CheckpointMessageDialog("Cannot Revert", ask.message, dismiss)
    }
  }
  return { document ->
    scope.launch {
      pending =
        PendingCheckpointAsk(document, checkpointAskFor(document, checkpoints))
    }
  }
}

/** `RevertConfirmWidget`: cannot be undone, when the checkpoint was made, Cancel and the warning Revert. */
@Composable
private fun RevertToCheckpointDialog(
  documentType: String,
  checkpoint: Checkpoint,
  onCancel: () -> Unit,
  onRevert: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text("Revert $documentType to checkpoint") },
    text = {
      Column {
        Text("Are you sure you want to revert the $documentType to checkpoint?")
        Text("This cannot be undone.", fontWeight = FontWeight.Bold)
        Text("The checkpoint was last updated at: ${CHECKPOINT_TIME.format(checkpoint.lastModified)}")
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onCancel()
        onRevert()
      }) { Text("Revert", color = MaterialTheme.colorScheme.error) }
    },
    dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
  )
}

@Composable
private fun CheckpointMessageDialog(title: String, message: String, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
  )
}

/**
 * `Time.format`: `Intl.DateTimeFormat` with a short date and a short time, in the locale and zone
 * of the device as the browser's own (jupyterlab@7f0e690 `packages/coreutils/src/time.ts`).
 */
private val CHECKPOINT_TIME: DateTimeFormatter =
  DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
