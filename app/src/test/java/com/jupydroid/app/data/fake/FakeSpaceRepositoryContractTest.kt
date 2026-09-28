package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.SpaceRepositoryContract

/** The fake stands in for the disk one wherever no Space can run, so it owes the same contract. */
class FakeSpaceRepositoryContractTest : SpaceRepositoryContract() {
  override fun repository(): SpaceRepository = FakeSpaceRepository()
}
