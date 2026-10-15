package com.jupydroid.app.ui.shell

import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.Checkpoint
import com.jupydroid.app.domain.port.DocumentCheckpoints
import com.jupydroid.app.domain.port.catchingRefusal

/** What Revert to Checkpoint asks, named by the document's type: that there is none, whether to go back, or why not. */
sealed interface CheckpointAsk {
  val documentType: String

  data class NoCheckpoints(override val documentType: String) : CheckpointAsk

  data class ToRevert(override val documentType: String, val checkpoint: Checkpoint) : CheckpointAsk

  data class Refused(override val documentType: String, val message: String) : CheckpointAsk
}

/**
 * The first half of `docmanager:restore-checkpoint` (jupyterlab@7f0e690
 * `packages/docmanager-extension/src/index.tsx`): `listCheckpoints`, then "No checkpoints" or the
 * one to revert to. A file keeps one at most, so there is no picker to pass through.
 *
 * ```
 * when (val ask = checkpointAskFor(document, documentCheckpoints)) { … }
 * ```
 */
suspend fun checkpointAskFor(document: OpenDocument, checkpoints: DocumentCheckpoints): CheckpointAsk {
  val type = documentTypeOf(document.path)
  return catchingRefusal { checkpoints.list(document.path) }.fold(
    onSuccess = { listed ->
      listed.maxByOrNull { it.lastModified }?.let { CheckpointAsk.ToRevert(type, it) }
        ?: CheckpointAsk.NoCheckpoints(type)
    },
    onFailure = { CheckpointAsk.Refused(type, it.message ?: "could not read the checkpoints of '${document.path}'") },
  )
}

/**
 * The second half, once the user has said Revert: the checkpoint is written back over the file and
 * the document reads it - `restoreCheckpoint(id).then(() => context.revert())` - dropping the write
 * it owed with the edits it was for (ADR-0096). Answers why the disk refused, or null; a refused
 * restore reads nothing back, so what is on screen stays.
 *
 * ```
 * revertToCheckpoint(document, ask.checkpoint, documentCheckpoints)?.let { showError(it) }
 * ```
 */
suspend fun revertToCheckpoint(
  document: OpenDocument,
  checkpoint: Checkpoint,
  checkpoints: DocumentCheckpoints,
): String? {
  catchingRefusal { checkpoints.restore(document.path, checkpoint.id) }.exceptionOrNull()?.let {
    return it.message ?: "could not revert '${document.path}' to its checkpoint"
  }
  document.reloadFromDisk()
  return null
}
