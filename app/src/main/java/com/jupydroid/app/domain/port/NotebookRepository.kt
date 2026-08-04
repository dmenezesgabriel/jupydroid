package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.NotebookSummary
import kotlinx.coroutines.flow.Flow

/** Persistence for notebooks. Disk-backed impl arrives in Step 3; fakes feed Steps 1-2. */
interface NotebookRepository {
  fun observeNotebooks(): Flow<List<NotebookSummary>>

  suspend fun createNotebook(title: String, kernelId: String): Notebook

  suspend fun renameNotebook(id: String, title: String)

  suspend fun deleteNotebook(id: String)

  suspend fun readNotebook(id: String): Notebook

  suspend fun saveNotebook(notebook: Notebook)
}
