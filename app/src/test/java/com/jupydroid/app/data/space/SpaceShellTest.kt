package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The Space's shell is one path, written down once, the way its workspace is. */
class SpaceShellTest {
  private val baseDir = File("/data/data/com.jupydroid.app/files/spaces/base")

  @Test
  fun theShellIsTheBusyBoxShAppletInsideTheSpace() {
    assertEquals(File(baseDir, "bin/sh"), spaceShellIn(baseDir))
  }

  /**
   * The applet `BusyBoxApplets` repairs, not some other `sh`. If these two ever named different
   * directories the server would pin a shell nothing had relinked, which `execve` answers with
   * ENOENT rather than anything a log would explain.
   */
  @Test
  fun theShellSitsInADirectoryTheAppletRelinkerWalks() {
    assertEquals(File(baseDir, "bin"), spaceShellIn(baseDir).parentFile)
  }
}
