@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.NOTEBOOK_SUFFIX
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.idBeside
import com.jupydroid.app.domain.model.incrementedFileName
import com.jupydroid.app.domain.model.isValidFileName
import com.jupydroid.app.domain.port.FileNameTakenException
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.UUID

/**
 * Notebook persistence over nbformat 4 JSON files, addressed by path from [rootDir].
 *
 * **The id is the path, suffix included** - `reports/Analysis.ipynb`, not `reports/Analysis`.
 * That is `Contents.IModel.path`, and it is what lets a notebook be one entry in a workspace the
 * explorer lists rather than a special case that has to have `.ipynb` put back on at every
 * boundary. Leaving the suffix off cost a defect the day folders became reachable: the explorer
 * handed over `Nested.ipynb`, this appended the suffix again, and `Nested.ipynb.ipynb` opened
 * nothing.
 *
 * Summaries are re-read from disk after every mutation.
 */
class DiskNotebookRepository(
  private val rootDir: File,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NotebookRepository {
  init {
    rootDir.mkdirs()
  }

  /**
   * The suffix is added here, not by the caller: this is the *typed* call, and `new_untitled`
   * gives `type: "notebook"` its own `ext = ".ipynb"` (`services/contents/manager.py:605-607`).
   * [title] is therefore a bare name and the id that comes back is a path with the suffix on.
   *
   * A taken name is incremented rather than refused - the user asked for *a new notebook*, not for
   * this exact name - by jupyter_server's own `increment_filename`, so `Untitled.ipynb`,
   * `Untitled1.ipynb`, with the counter on the stem.
   */
  override suspend fun createNotebook(title: String, kernelId: String): Notebook = withContext(ioDispatcher) {
    val free = incrementedFileName("${title.trim()}$NOTEBOOK_SUFFIX") { fileFor(it).exists() }
    val notebook = Notebook(id = free, title = free, kernelId = kernelId, cells = emptyList())
    writeFile(notebook)
    notebook
  }

  /**
   * Moves the file, because the name *is* the file - `Context._rename` calls
   * `contents.rename(this.path, newPath)` for the same reason.
   *
   * Refuses an invalid or taken name rather than resolving it. jupyter_server answers a taken name
   * with 409 and JupyterLab catches exactly that to ask "already exists, overwrite?", so the
   * asking belongs to the dialog and the refusing belongs here.
   */
  override suspend fun renameNotebook(id: String, title: String): String = withContext(ioDispatcher) {
    val wanted = title.trim()
    require(isValidFileName(wanted)) {
      "notebook name '$title' is not valid: it must not be empty, start with '.', or contain / \\ :"
    }
    // The new *name* joined onto the id's folder, never used as a path in its own right: a
    // notebook in `reports/` must stay in `reports/`. See `idBeside`.
    val moved = idBeside(id, wanted)
    if (moved != id && fileFor(moved).exists()) throw FileNameTakenException(wanted)
    val notebook = readNotebook(id).copy(id = moved, title = moved)
    writeFile(notebook)
    if (moved != id) fileFor(id).delete()
    moved
  }

  override suspend fun deleteNotebook(id: String) {
    withContext(ioDispatcher) { fileFor(id).delete() }
  }

  override suspend fun readNotebook(id: String): Notebook = withContext(ioDispatcher) {
    val file = fileFor(id)
    if (!file.exists()) {
      throw IllegalArgumentException("no notebook file for id '$id' at '$file'")
    }
    try {
      json.readNotebook(file, id)
    } catch (e: SerializationException) {
      throw IllegalArgumentException("notebook file '$file' is not valid nbformat JSON: ${e.message}")
    }
  }

  override suspend fun saveNotebook(notebook: Notebook) = withContext(ioDispatcher) {
    writeFile(notebook)
  }

  /**
   * Replaces the notebook at [title] with this one, by the reference's own steps.
   *
   * `DocumentManager.overwrite`: "Cleanly overwrite the file by moving it, making sure the original
   * does not exist, and then renaming to the new path." Ours goes aside under a unique temporary
   * name first, so a failure while deleting the target leaves both files on disk rather than
   * neither - deleting first would open a window in which the user has lost one and not gained
   * the other.
   */
  override suspend fun overwriteNotebook(id: String, title: String): String = withContext(ioDispatcher) {
    val wanted = title.trim()
    require(isValidFileName(wanted)) { "notebook name '$title' is not valid" }
    val moved = idBeside(id, wanted)
    val temporary = File(rootDir, "$moved.${UUID.randomUUID()}")
    fileFor(id).renameTo(temporary)
    fileFor(moved).delete()
    temporary.renameTo(fileFor(moved))
    moved
  }

  private fun writeFile(notebook: Notebook) {
    val file = fileFor(notebook.id)
    file.parentFile?.mkdirs()
    file.writeText(json.encodeToString(JsonObject.serializer(), notebook.toFileJson()))
  }

  /** The id is already the path - there is no suffix to add and none to strip. */
  private fun fileFor(id: String): File = File(rootDir, id)
}
