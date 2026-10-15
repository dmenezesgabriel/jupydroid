package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Checkpoint

/**
 * A workspace file's checkpoint: one point it can be put back to, kept apart from the file.
 *
 * `ContentsManager`'s checkpoint half - `create_checkpoint`, `list_checkpoints`,
 * `restore_checkpoint` - as the shipped rootfs's `FileCheckpoints` answers it: **one per file**,
 * under the id `"checkpoint"`, and a second create replaces the first
 * (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/filecheckpoints.py`). A port
 * beside [WorkspaceRepository] rather than on it: the tree is what is there, and this is what a
 * file was. Paths are relative to the workspace root, as there.
 *
 * ```
 * checkpoints.restore("notes.py", checkpoints.list("notes.py").single().id)
 * ```
 */
interface DocumentCheckpoints {
  /**
   * Copies the file at [path] aside as its checkpoint, replacing the one it had, and answers with it.
   *
   * ```
   * checkpoints.create("reports/Analysis.ipynb")
   * ```
   */
  suspend fun create(path: String): Checkpoint

  /** The checkpoints the file at [path] has - none, or the one [create] last made. */
  suspend fun list(path: String): List<Checkpoint>

  /**
   * Writes the checkpoint [id] back over the file at [path]; the checkpoint stays, so the same point
   * can be gone back to again. An open document reads the file afterwards, as
   * `docmanager:restore-checkpoint` follows the restore with `context.revert()` (jupyterlab@7f0e690
   * `packages/docmanager-extension/src/index.tsx`).
   *
   * ```
   * checkpoints.restore("notes.py", "checkpoint")
   * ```
   */
  suspend fun restore(path: String, id: String)
}
