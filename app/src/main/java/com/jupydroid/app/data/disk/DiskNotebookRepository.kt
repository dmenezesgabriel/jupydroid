@file:Suppress("SwallowedException")
package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.NotebookSummary
import com.jupydroid.app.domain.port.NotebookRepository
import java.io.File
import java.time.Instant
import java.util.UUID
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

  override suspend fun createNotebook(title: String, kernelId: String): Notebook =
    withContext(ioDispatcher) {
      val notebook =
        Notebook(
          id = UUID.randomUUID().toString(),
          title = title,
          kernelId = kernelId,
          cells = emptyList(),
          updatedAt = Instant.now(),
        )
      writeFile(notebook)
      refreshSummaries()
      notebook
    }

  override suspend fun renameNotebook(id: String, title: String) = withContext(ioDispatcher) {
    val notebook = readNotebook(id).copy(title = title)
    writeFile(notebook)
    refreshSummaries()
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

  private fun writeFile(notebook: Notebook) {
    val file = fileFor(notebook.id)
    file.parentFile?.mkdirs()
    file.writeText(json.encodeToString(JsonObject.serializer(), notebook.toFileJson()))
  }

  private fun fileFor(id: String): File = File(rootDir, "$id.ipynb")

  private fun refreshSummaries() {
    summaries.value = scanSummaries()
  }

  private fun scanSummaries(): List<NotebookSummary> =
    (rootDir.listFiles() ?: emptyArray())
      .filter { it.isFile && it.name.endsWith(".ipynb") }
      .mapNotNull(::trySummaryOf)
      .sortedByDescending { it.updatedAt }

  /** Corrupt or foreign unreadable files are skipped so one bad file does not break Home. */
  private fun trySummaryOf(file: File): NotebookSummary? {
    val id = file.name.removeSuffix(".ipynb")
    return try {
      json.readNotebook(file, id).toSummary()
    } catch (e: SerializationException) {
      null
    } catch (e: IllegalArgumentException) {
      null
    }
  }
}
