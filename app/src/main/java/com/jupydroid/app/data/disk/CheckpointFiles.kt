package com.jupydroid.app.data.disk

import java.io.File

/**
 * Where a workspace file's checkpoint lies, and how it goes along when the file moves or goes.
 *
 * The shipped rootfs's `FileCheckpoints` layout, `<folder>/.ipynb_checkpoints/<stem>-checkpoint<ext>`
 * (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/filecheckpoints.py`). One home
 * for it, because [DiskDocumentCheckpoints] writes there and [DiskWorkspaceRepository] carries it as
 * `ContentsManager.rename` and `delete` do (`manager.py` in the same package).
 *
 * ```
 * CheckpointFiles(WorkspaceRoot(root)).fileOf("notes.py")   // .ipynb_checkpoints/notes-checkpoint.py
 * ```
 */
internal class CheckpointFiles(private val root: WorkspaceRoot) {
  /** `checkpoint_path`: beside the file, the stem and extension split as `os.path.splitext` splits them. */
  fun fileOf(path: String): File {
    val file = root.fileAt(path)
    val (stem, extension) = splitExtension(file.name)
    return File(File(file.parentFile, CHECKPOINT_DIR), "$stem-$CHECKPOINT_ID$extension")
  }

  /** `rename_checkpoint`: moved only when there is one, replacing whatever lay at the new name. */
  fun move(from: String, to: String) {
    val old = fileOf(from)
    if (!old.isFile) return
    check(old.renameTo(fileOf(to))) { "could not move the checkpoint of '$from' to that of '$to'" }
  }

  /**
   * An overwrite's half: the target's checkpoint goes as `delete` takes it with the target, then
   * [from]'s moves onto its name (jupyterlab@7f0e690 `packages/docmanager/src/manager.ts`
   * `overwrite`). A target left with none is not given a stale one.
   */
  fun moveOnto(from: String, to: String) {
    remove(to)
    move(from, to)
  }

  /** `delete_all_checkpoints`: the file's checkpoint, when it has one. */
  fun remove(path: String) {
    val copy = fileOf(path)
    if (copy.isFile) check(copy.delete()) { "could not delete the checkpoint of '$path'" }
  }

  /** `is_non_empty_dir`'s exception: a folder holding nothing but its checkpoints counts as empty. */
  fun isOnlyCheckpoints(folder: File): Boolean = folder.list()?.all { it == CHECKPOINT_DIR } ?: false

  /**
   * Removes [folder]'s checkpoint folder as `shutil.rmtree` would: depth first, and a link it meets
   * is unlinked rather than walked, so nothing outside the folder is reached.
   */
  fun removeCheckpointFolderIn(folder: File) = deleteTreeUnlinkingLinks(File(folder, CHECKPOINT_DIR).toPath())

  companion object {
    /** `FileCheckpoints.checkpoint_dir`'s default. */
    const val CHECKPOINT_DIR = ".ipynb_checkpoints"

    /** The only id `FileCheckpoints` ever makes. */
    const val CHECKPOINT_ID = "checkpoint"
  }
}
