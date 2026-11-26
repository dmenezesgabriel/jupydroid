package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.SpaceEnvironmentCreation
import com.jupydroid.app.domain.port.SpaceEnvironmentCreationContract

/**
 * The fake owes the port's contract too: `AppContainer` defaults to it, so every screen test
 * creates an environment through this and not through proot.
 */
class FakeSpaceEnvironmentCreationContractTest : SpaceEnvironmentCreationContract() {
  override fun creation(): SpaceEnvironmentCreation = FakeSpaceEnvironmentCreation()
}
