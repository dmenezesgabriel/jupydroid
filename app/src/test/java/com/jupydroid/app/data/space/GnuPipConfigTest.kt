package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GnuPipConfigTest {
  @get:Rule val tempFolder = TemporaryFolder()

  /**
   * `opt/gnu/python/pip.conf` is `<sys.prefix>/pip.conf` for the gnu payload's
   * `/opt/gnu/python/bin/python3.12` - the prefix-scoped SITE file, so the kernel's `%pip`
   * installs into its own user base unflagged while the musl side keeps reading `usr/pip.conf`
   * (ADR-0166).
   */
  @Test
  fun ensure_makesTheGnuPipsOwnInstallsLandInTheGnuUserBase() {
    val spaceDir = tempFolder.newFolder("space")

    GnuPipConfig.ensure(spaceDir)

    assertEquals("[install]\nuser = true\n", File(spaceDir, "opt/gnu/python/pip.conf").readText())
  }

  /** The file is the Space's own once written (ADR-0073): a start never overwrites a person's edit. */
  @Test
  fun ensure_leavesAPipConfAlreadyThereUntouched() {
    val spaceDir = tempFolder.newFolder("space")
    val edited = File(spaceDir, "opt/gnu/python/pip.conf").apply {
      parentFile?.mkdirs()
      writeText("[global]\nindex-url = https://mirror.example/simple\n")
    }

    GnuPipConfig.ensure(spaceDir)

    assertEquals("[global]\nindex-url = https://mirror.example/simple\n", edited.readText())
  }
}
