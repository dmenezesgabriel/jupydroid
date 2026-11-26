package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeSpaceShell
import com.jupydroid.app.domain.port.SpaceKernelRegistration
import com.jupydroid.app.domain.port.SpaceKernelRegistrationContract
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The SpaceShell-backed register owes the port's contract too - over [FakeSpaceShell], so the
 * run is recorded rather than exec'd; the proot run itself is `KernelRegisterDeviceTest`'s.
 */
class SpaceShellKernelRegistrationContractTest : SpaceKernelRegistrationContract() {
  @get:Rule val tempFolder = TemporaryFolder()

  override fun registration(): SpaceKernelRegistration {
    File(tempFolder.root, SPACE_ID).mkdirs()
    return SpaceShellKernelRegistration(FakeSpaceShell(), tempFolder.root)
  }
}
