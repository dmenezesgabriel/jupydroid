package com.jupydroid.app

import com.jupydroid.app.domain.port.DocumentCheckpoints
import com.jupydroid.app.domain.port.catchingRefusal

/**
 * Gives the file at [path] a checkpoint when it has none, and leaves one it has where it is.
 *
 * `_maybeCheckpoint(false)`, which the reference runs as a document is first populated
 * (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`): Revert to Checkpoint then always has
 * a point to go to, and it is the file as it was opened or as the user last saved it - never moved
 * by opening it again.
 *
 * A failure is dropped rather than thrown. The reference swallows a read-only folder's 403; here
 * this runs on the application scope, where a throw ends the process, and a file that cannot be
 * checkpointed still opens - to its own error, if it is gone.
 *
 * ```
 * applicationScope.launch { documentCheckpoints.checkpointIfNone("reports/summary.ipynb") }
 * ```
 */
suspend fun DocumentCheckpoints.checkpointIfNone(path: String) {
  catchingRefusal { if (list(path).isEmpty()) create(path) }
}

/**
 * Moves the file at [path]'s checkpoint to what it holds now, after a save the user asked for.
 *
 * The reference makes one after `docmanager:save` and after a Save As's write, and **not** after
 * an autosave or Save All (jupyterlab@7f0e690 `packages/docmanager-extension/src/index.tsx`,
 * `packages/docregistry/src/context.ts` `_finishSaveAs`): the checkpoint is the user's last
 * deliberate save, not wherever the debounce happened to land. Dropped on failure for
 * [checkpointIfNone]'s reason - the save itself has already been made.
 *
 * ```
 * document.saveNow { scope.launch { documentCheckpoints.checkpointSaved(document.path) } }
 * ```
 */
suspend fun DocumentCheckpoints.checkpointSaved(path: String) {
  catchingRefusal { create(path) }
}
