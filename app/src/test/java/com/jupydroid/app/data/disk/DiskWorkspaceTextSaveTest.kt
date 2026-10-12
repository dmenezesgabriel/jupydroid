package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceTextSaveContract
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** The disk workspace's text save, held to what the fake's is. */
class DiskWorkspaceTextSaveTest : WorkspaceTextSaveContract() {
  @get:Rule val temp = TemporaryFolder()

  override fun repository(): WorkspaceRepository = DiskWorkspaceRepository(temp.root)
}
