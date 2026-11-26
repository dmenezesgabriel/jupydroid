package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeSpaceShell
import com.jupydroid.app.domain.port.SpaceEnvironmentCreation
import com.jupydroid.app.domain.port.SpaceEnvironmentCreationContract

/**
 * The SpaceShell-backed create owes the port's contract too - over [FakeSpaceShell], so the run
 * is recorded rather than exec'd; the proot run itself is `EnvironmentCreateDeviceTest`'s.
 */
class SpaceShellEnvironmentCreationContractTest : SpaceEnvironmentCreationContract() {
  override fun creation(): SpaceEnvironmentCreation = SpaceShellEnvironmentCreation(FakeSpaceShell())
}
