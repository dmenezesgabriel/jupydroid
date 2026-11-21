package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.SpaceShell
import com.jupydroid.app.domain.port.SpaceShellContract

/**
 * The fake owes the port's contract too: `AppContainer` defaults to it, so every screen test
 * runs a Space's script through this and not through proot.
 */
class FakeSpaceShellContractTest : SpaceShellContract() {
  override fun shell(): SpaceShell = FakeSpaceShell()
}
