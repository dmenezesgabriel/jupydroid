package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Notebook

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

  suspend fun readNotebook(id: String): Notebook

  suspend fun saveNotebook(notebook: Notebook)
}
