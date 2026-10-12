package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceTextSaveContract

/** The fake stands in for the disk one in the file editor's tests, so it refuses what the disk one refuses. */
class FakeWorkspaceTextSaveTest : WorkspaceTextSaveContract() {
  override fun repository(): WorkspaceRepository = FakeWorkspaceRepository()
}
