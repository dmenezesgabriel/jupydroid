package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceTextReadContract
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** The disk workspace's text read, held to what the fake's is. */
class DiskWorkspaceTextReadTest : WorkspaceTextReadContract() {
  @get:Rule val temp = TemporaryFolder()

  override fun repository(): WorkspaceRepository = DiskWorkspaceRepository(temp.root)
}
