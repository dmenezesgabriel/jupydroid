package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.NotebookRepositoryContract

/** The fake stands in for the disk one in ViewModel tests, so it owes the same contract. */
class FakeNotebookRepositoryContractTest : NotebookRepositoryContract() {
  override fun repository(): NotebookRepository = FakeNotebookRepository()
}
