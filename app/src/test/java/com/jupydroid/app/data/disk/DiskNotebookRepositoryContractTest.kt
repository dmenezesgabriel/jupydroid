package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.NotebookRepositoryContract
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** The real one, against real files - where `updatedAt` is the file's own mtime. */
class DiskNotebookRepositoryContractTest : NotebookRepositoryContract() {
  @get:Rule val tempFolder = TemporaryFolder()

  override fun repository(): NotebookRepository = DiskNotebookRepository(tempFolder.root)
}
