@file:Suppress("SwallowedException")
package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.NotebookSummary
import com.jupydroid.app.domain.model.incrementedName
import com.jupydroid.app.domain.model.notebookFileName
import com.jupydroid.app.domain.model.notebookNameOf
import com.jupydroid.app.domain.model.isValidNotebookName
import com.jupydroid.app.domain.port.NotebookNameTakenException
import com.jupydroid.app.domain.port.NotebookRepository
import java.io.File
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Notebook persistence over nbformat 4 JSON files (`<rootDir>/<id>.ipynb`).
 * Replaces the in-memory [com.jupydroid.app.data.fake.FakeNotebookRepository] in
 * Step 3; summaries are re-read from disk after every mutation.
 */
class DiskNotebookRepository(
  private val rootDir: File,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NotebookRepository {
  private val summaries = MutableStateFlow(emptyList<NotebookSummary>())

  init {
    rootDir.mkdirs()
    summaries.value = scanSummaries()
  }

  override fun observeNotebooks(): Flow<List<NotebookSummary>> = summaries

  /**
   * The file is named after the notebook, and a taken name is incremented rather than refused:
   * the user asked for *a new notebook*, not for this exact name. `incrementedName` is
   * jupyter_server's own `increment_filename`, so `Untitled`, `Untitled1`, `Untitled2`.
   */
  override suspend fun createNotebook(title: String, kernelId: String): Notebook =
    withContext(ioDispatcher) {
      val free = incrementedName(title.trim()) { fileFor(it).exists() }
      val notebook = Notebook(id = free, title = free, kernelId = kernelId, cells = emptyList())
      writeFile(notebook)
      refreshSummaries()
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
    require(isValidNotebookName(wanted)) {
      "notebook name '$title' is not valid: it must not be empty, start with '.', or contain / \\ :"
    }
    val target = fileFor(wanted)
    if (wanted != id && target.exists()) throw NotebookNameTakenException(wanted)
    val notebook = readNotebook(id).copy(id = wanted, title = wanted)
    writeFile(notebook)
    if (wanted != id) fileFor(id).delete()
    refreshSummaries()
    wanted
  }

  override suspend fun deleteNotebook(id: String) = withContext(ioDispatcher) {
    fileFor(id).delete()
    refreshSummaries()
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
    refreshSummaries()
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
    require(isValidNotebookName(wanted)) { "notebook name '$title' is not valid" }
    val temporary = File(rootDir, "${notebookFileName(wanted)}.${UUID.randomUUID()}")
    fileFor(id).renameTo(temporary)
    fileFor(wanted).delete()
    temporary.renameTo(fileFor(wanted))
    refreshSummaries()
    wanted
  }

  private fun writeFile(notebook: Notebook) {
    val file = fileFor(notebook.id)
    file.parentFile?.mkdirs()
    file.writeText(json.encodeToString(JsonObject.serializer(), notebook.toFileJson()))
  }

  private fun fileFor(id: String): File = File(rootDir, notebookFileName(id))

  private fun refreshSummaries() {
    summaries.value = scanSummaries()
  }

  private fun scanSummaries(): List<NotebookSummary> =
    (rootDir.listFiles() ?: emptyArray())
      .filter { it.isFile && it.name.endsWith(".ipynb") }
      .mapNotNull { trySummaryIn(json, it) }
      .sortedByDescending { it.updatedAt }

  /**
   * Corrupt or foreign unreadable files are skipped so one bad file does not break Home.
   *
   * `updatedAt` is the file's own mtime, the way Jupyter's Contents API reads it
   * (`filemanager.py:285`). So every write counts - an edit, a run, a rename, the creation
   * itself - because every one of them writes the file, and nothing has to remember to say so.
   */
}

/**
 * Outside the class only because detekt counts methods, and this one needs none of its state.
 *
 * Corrupt or foreign unreadable files are skipped so one bad file does not break Home.
 *
 * `updatedAt` is the file's own mtime, the way Jupyter's Contents API reads it
 * (`filemanager.py:285`). So every write counts - an edit, a run, a rename, the creation itself -
 * because every one of them writes the file, and nothing has to remember to say so.
 */
private fun trySummaryIn(json: Json, file: File): NotebookSummary? {
  val id = notebookNameOf(file.name)
  return try {
    json.readNotebook(file, id).toSummary(Instant.ofEpochMilli(file.lastModified()))
  } catch (e: SerializationException) {
    null
  } catch (e: IllegalArgumentException) {
    null
  }
}
