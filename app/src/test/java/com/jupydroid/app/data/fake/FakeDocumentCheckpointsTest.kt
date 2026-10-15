package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.DocumentCheckpoints
import com.jupydroid.app.domain.port.DocumentCheckpointsContract
import com.jupydroid.app.domain.port.WorkspaceRepository

/** The fake stands in for the disk one in the tab's tests, so its checkpoint is the disk one's shape. */
class FakeDocumentCheckpointsTest : DocumentCheckpointsContract() {
  override val workspace: WorkspaceRepository = FakeWorkspaceRepository()
  override val checkpoints: DocumentCheckpoints = FakeDocumentCheckpoints(workspace)
}
