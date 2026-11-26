package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.SpaceKernelRegistration
import com.jupydroid.app.domain.port.SpaceKernelRegistrationContract

/**
 * The fake owes the port's contract too: `AppContainer` defaults to it, so every screen test
 * registers an interpreter through this and not through proot.
 */
class FakeSpaceKernelRegistrationContractTest : SpaceKernelRegistrationContract() {
  override fun registration(): SpaceKernelRegistration = FakeSpaceKernelRegistration(mapOf(SPACE_ID to listOf(MUSL)))
}
