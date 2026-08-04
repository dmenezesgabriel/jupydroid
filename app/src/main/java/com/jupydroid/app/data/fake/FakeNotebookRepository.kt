package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.NotebookSummary
import com.jupydroid.app.domain.port.NotebookRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [NotebookRepository] feeding the UI until the disk impl lands (Step 3). */
class FakeNotebookRepository : NotebookRepository {
  private val notebooks = MutableStateFlow<Map<String, Notebook>>(emptyMap())

  override fun observeNotebooks(): Flow<List<NotebookSummary>> =
    notebooks.map { all ->
      all.values.map { it.toSummary() }.sortedByDescending { it.updatedAt }
    }

  override suspend fun createNotebook(title: String, kernelId: String): Notebook {
    val notebook = Notebook(
      id = UUID.randomUUID().toString(),
      title = title,
      kernelId = kernelId,
      cells = emptyList(),
      updatedAt = Instant.now(),
    )
    notebooks.value = notebooks.value + (notebook.id to notebook)
    return notebook
  }

  override suspend fun renameNotebook(id: String, title: String) {
    notebooks.value = notebooks.value.mapValues { entry ->
      if (entry.key == id) entry.value.copy(title = title) else entry.value
    }
  }

  override suspend fun deleteNotebook(id: String) {
    notebooks.value = notebooks.value - id
  }

  // IllegalArgumentException, not IllegalStateException: DiskNotebookRepository is the
  // reference behaviour for this port and callers catch that type.
  override suspend fun readNotebook(id: String): Notebook =
    requireNotNull(notebooks.value[id]) { "no notebook with id '$id'" }

  override suspend fun saveNotebook(notebook: Notebook) {
    notebooks.value = notebooks.value + (notebook.id to notebook)
  }

  private fun Notebook.toSummary(): NotebookSummary =
    NotebookSummary(id = id, title = title, updatedAt = updatedAt)
}
