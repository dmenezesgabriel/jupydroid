package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.port.SpaceShell
import com.jupydroid.app.domain.port.SpaceShellContract
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/**
 * The real adapter against the same contract the fake satisfies, over [FakeProotScript] and
 * [FakeSpaceShellScript]: what the contract can ask - event shape and completion - holds without
 * proot, while the bound and the status it produces are `ProotSpaceShellDeviceTest`'s to prove
 * on the tablet.
 */
class ProotSpaceShellContractTest : SpaceShellContract() {
  @get:Rule val tempFolder = TemporaryFolder()

  private val spaceDir by lazy { tempFolder.newFolder("space") }
  private val nativeDir by lazy { FakeProotScript.installIn(tempFolder.newFolder("native")) }
  private val shell by lazy {
    FakeSpaceShellScript.installIn(spaceDir)
    ProotSpaceShell(
      installSpace = { _ -> },
      settingsFor = { SpaceSettings() },
      runtimeFor = { _, _ -> SpaceRuntime(spaceDir, nativeDir) },
      dirsFor = { SpaceDirs(spaceDir) },
    )
  }

  override fun shell(): SpaceShell = shell
}
