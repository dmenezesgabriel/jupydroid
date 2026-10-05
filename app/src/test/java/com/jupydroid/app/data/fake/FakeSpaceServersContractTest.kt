package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.SpaceServers
import com.jupydroid.app.domain.port.SpaceServersContract
import kotlinx.coroutines.test.TestScope

/** The fake stands in wherever no Space can run - the x86_64 emulator - so it owes the same contract. */
class FakeSpaceServersContractTest : SpaceServersContract() {
  override fun TestScope.servers(): SpaceServers = FakeSpaceServers()
}
