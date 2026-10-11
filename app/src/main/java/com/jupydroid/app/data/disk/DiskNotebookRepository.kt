@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.NOTEBOOK_SUFFIX
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.incrementedFileName
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

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
  override suspend fun createNotebook(title: String, kernel: KernelSpec): Notebook = withContext(ioDispatcher) {
    val free = incrementedFileName("${title.trim()}$NOTEBOOK_SUFFIX") { fileFor(it).exists() }
    val notebook = Notebook(id = free, title = free, kernelId = kernel.name, cells = emptyList())
      .withKernelSpec(kernel.name, kernel)
    writeFile(notebook)
    notebook
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

  private fun writeFile(notebook: Notebook) {
    val file = fileFor(notebook.id)
    file.parentFile?.mkdirs()
    // Encoded before the temp is opened, so a large notebook's encoding is not time the temp sits empty.
    val bytes = json.encodeToString(JsonObject.serializer(), notebook.toFileJson()).toByteArray()
    file.writeAtomically { it.write(bytes) }
  }

  /** The id is already the path - there is no suffix to add and none to strip. */
  private fun fileFor(id: String): File = File(rootDir, id)
}
