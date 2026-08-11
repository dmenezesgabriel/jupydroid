package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.NotebookSummary
import com.jupydroid.app.domain.port.NotebookNameTakenException
import com.jupydroid.app.domain.port.NotebookRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * In-memory [NotebookRepository] feeding the UI until the disk impl lands (Step 3).
 *
 * [written] is this fake's stand-in for a file's mtime, which is where the disk one gets
 * `updatedAt`. Every mutation stamps it, because on disk every mutation writes the file - a fake
 * that only stamped on create would let the UI look right against a behaviour the real
 * repository does not have. `NotebookRepositoryContract` holds both to it.
 */
class FakeNotebookRepository : NotebookRepository {
  private val notebooks = MutableStateFlow<Map<String, Notebook>>(emptyMap())
  private val written = MutableStateFlow<Map<String, Instant>>(emptyMap())

  override fun observeNotebooks(): Flow<List<NotebookSummary>> =
    combine(notebooks, written) { all, times ->
      all.values
        .map { NotebookSummary(it.id, it.title, times[it.id] ?: Instant.EPOCH) }
        .sortedByDescending { it.updatedAt }
    }

  override suspend fun createNotebook(title: String, kernelId: String): Notebook {
    val notebook = Notebook(
      id = UUID.randomUUID().toString(),
      title = title,
      kernelId = kernelId,
      cells = emptyList(),
    )
    put(notebook)
    return notebook
  }

  /** Re-keyed, not merely re-titled: the id *is* the name, so a rename moves the entry. */
  override suspend fun renameNotebook(id: String, title: String): String {
    val existing = notebooks.value[id] ?: return id
    if (title != id && notebooks.value.containsKey(title)) throw NotebookNameTakenException(title)
    notebooks.value = notebooks.value - id
    put(existing.copy(id = title, title = title))
    return title
  }

  /** Takes the name whatever is there, which is what the user asked for when they confirmed. */
  override suspend fun overwriteNotebook(id: String, title: String): String {
    val existing = notebooks.value[id] ?: return id
    notebooks.value = notebooks.value - id - title
    put(existing.copy(id = title, title = title))
    return title
  }

  override suspend fun deleteNotebook(id: String) {
    notebooks.value = notebooks.value - id
    written.value = written.value - id
  }

  // IllegalArgumentException, not IllegalStateException: DiskNotebookRepository is the
  // reference behaviour for this port and callers catch that type.
  override suspend fun readNotebook(id: String): Notebook =
    requireNotNull(notebooks.value[id]) { "no notebook with id '$id'" }

  override suspend fun saveNotebook(notebook: Notebook) = put(notebook)

  /** The one way in, so no mutation can forget to count as an update. */
  private fun put(notebook: Notebook) {
    notebooks.value = notebooks.value + (notebook.id to notebook)
    written.value = written.value + (notebook.id to Instant.now())
  }
}
