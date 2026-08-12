package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.NotebookSummary
import kotlinx.coroutines.flow.Flow

/** Persistence for notebooks. Disk-backed impl arrives in Step 3; fakes feed Steps 1-2. */
/**
 * The name is taken - the one refusal a caller can offer to resolve.
 *
 * Its own type because the caller branches on it, exactly as JupyterLab branches on the status
 * code: `if (error.response.status !== 409) { throw error }`, and only a 409 becomes an offer to
 * overwrite. An invalid name is not offered anything; it is simply not a name.
 */
class NotebookNameTakenException(name: String) :
  IllegalStateException("a notebook called '$name' already exists")

interface NotebookRepository {
  fun observeNotebooks(): Flow<List<NotebookSummary>>

  suspend fun createNotebook(title: String, kernelId: String): Notebook

  /**
   * Renames a notebook, moving its file, and answers with its new id.
   *
   * The id is the *path*, suffix included (`Contents.IModel.path`), and [title] is a bare name
   * joined onto the folder that path names - so a rename changes the id, which is why the caller
   * gets the new one back.
   * `Context._rename` does the same thing: `contents.rename(this.path, newPath)`, and the open
   * document follows its new path rather than being reopened.
   *
   * Throws when the name is not a valid one ([isValidNotebookName]) or is already taken.
   * jupyter_server answers a taken name with **409** and JupyterLab catches exactly that to ask
   * "already exists, overwrite?" - so refusing here, and asking in the dialog, is the reference's
   * division of labour rather than ours.
   */
  @Throws(NotebookNameTakenException::class)
  suspend fun renameNotebook(id: String, title: String): String

  /**
   * Renames onto a name already taken, replacing what was there.
   *
   * Offered only after the user has been asked - `"%1" already exists, overwrite?` - which is the
   * reference's division of labour: `renameFile` catches the 409 and calls `overwrite` only if the
   * dialog is accepted (`packages/docmanager/src/dialogs.ts`).
   */
  suspend fun overwriteNotebook(id: String, title: String): String

  suspend fun deleteNotebook(id: String)

  suspend fun readNotebook(id: String): Notebook

  suspend fun saveNotebook(notebook: Notebook)
}
