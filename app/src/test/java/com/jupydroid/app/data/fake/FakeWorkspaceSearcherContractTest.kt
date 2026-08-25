package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.WorkspaceSearcher
import com.jupydroid.app.domain.port.WorkspaceSearcherContract

/** The fake is what `AppContainer` defaults to, so it owes the same contract as the disk one. */
class FakeWorkspaceSearcherContractTest : WorkspaceSearcherContract() {
  private val searcher = FakeWorkspaceSearcher()

  override fun searcher(): WorkspaceSearcher = searcher

  override fun given(vararg files: Pair<String, String>) = searcher.holds(*files)
}
