package com.jupydroid.extensions.git.data.fake

import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.domain.GitStatusReaderContract
import com.jupydroid.extensions.git.domain.RepositoryStatus

class FakeGitStatusReaderTest : GitStatusReaderContract() {
  override fun reader(
    repositories: Map<String, RepositoryStatus.Repository>,
    failures: Map<String, String>,
  ): GitStatusReader = FakeGitStatusReader(repositories, failures)
}
