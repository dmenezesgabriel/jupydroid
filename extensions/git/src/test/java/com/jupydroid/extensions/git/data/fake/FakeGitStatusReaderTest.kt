package com.jupydroid.extensions.git.data.fake

import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.domain.GitStatusReaderContract

class FakeGitStatusReaderTest : GitStatusReaderContract() {
  override fun reader(repositories: Map<String, String>, failures: Map<String, String>): GitStatusReader =
    FakeGitStatusReader(repositories, failures)
}
