package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.StampedNotebook

/**
 * Persistence for one notebook, as nbformat JSON. `DiskNotebookRepository` is the real one.
 *
 * A notebook's rename, overwrite and delete are not here: the id **is** the file's path, so those
 * are tree acts and [WorkspaceRepository] - which owns the tree and announces them on its
 * `changes` - is their one home. See ADR-0070.
 */
interface NotebookRepository {
  /**
   * A new notebook named [title], recording [kernel] in `metadata.kernelspec` - its name and its
   * `display_name`, both of which nbformat v4 requires.
   *
   * ```
   * repository.createNotebook("Untitled", kernelSpecs.specs(space).defaultSpec)
   * ```
   */
  suspend fun createNotebook(title: String, kernel: KernelSpec): Notebook

  /** The notebook at [id], for a reader that will not write it back. */
  suspend fun readNotebook(id: String): Notebook = readStampedNotebook(id).notebook

  /**
   * The notebook at [id] and the stamp of the file it came from, for a caller that will save it:
   * the stamp is what [saveNotebook] is handed back as `expected`.
   *
   * ```
   * val (notebook, stamp) = repository.readStampedNotebook("reports/Analysis.ipynb")
   * ```
   */
  suspend fun readStampedNotebook(id: String): StampedNotebook

  /**
   * Writes [notebook] over its file and answers with the stamp the file has now - the `expected`
   * of the save after this one.
   *
   * With an [expected] stamp the write is refused when the file is no longer the one that stamp
   * describes, because the workspace is the kernel's too and a cell may have rewritten it. A file
   * that already holds exactly what this would write is not a change, and a file that is gone is
   * written back, as `Context._maybeSave` saves on a 404 (jupyterlab@7f0e690
   * `packages/docregistry/src/context.ts`). With `null` the write is unconditional - a new
   * notebook, or the user's own "Overwrite".
   *
   * ```
   * stamp = repository.saveNotebook(edited, expected = stamp)
   * ```
   *
   * @throws FileChangedOnDiskException when the file changed since [expected] was taken.
   */
  @Throws(FileChangedOnDiskException::class)
  suspend fun saveNotebook(notebook: Notebook, expected: FileStamp? = null): FileStamp
}
