package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SpacePipConfigTest {
  @get:Rule val tempFolder = TemporaryFolder()

  /**
   * `usr/pip.conf` is `<sys.prefix>/pip.conf` for the image's `/usr/bin/python3`, pip's SITE file,
   * so base's own pip installs into the user base unflagged while a venv's pip, whose `sys.prefix`
   * is the venv, never reads it.
   */
  @Test
  fun ensure_makesTheImagesOwnPipInstallIntoTheUserBase() {
    val spaceDir = tempFolder.newFolder("space")

    SpacePipConfig.ensure(spaceDir)

    assertEquals("[install]\nuser = true\n", File(spaceDir, "usr/pip.conf").readText())
  }

  /** The file is the Space's own once written (ADR-0073): a start never overwrites a person's edit. */
  @Test
  fun ensure_leavesAPipConfAlreadyThereUntouched() {
    val spaceDir = tempFolder.newFolder("space")
    val edited = File(spaceDir, "usr/pip.conf").apply {
      parentFile?.mkdirs()
      writeText("[global]\nindex-url = https://mirror.example/simple\n")
    }

    SpacePipConfig.ensure(spaceDir)

    assertEquals("[global]\nindex-url = https://mirror.example/simple\n", edited.readText())
  }
}
