package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * **A Space's `pylsp` runs in that Space**: under proot with the Space as `/`, from its workspace,
 * with the variables that place its writes naming the Space's own directories - provisioned on the
 * spot from a directory holding nothing but `config.json` and the image.
 *
 * A real process: [FakeProotScript] stands in for proot and [FakePylspScript] for the console
 * script, so what is asserted is what the process actually saw.
 */
class StartPylspTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val nativeDir by lazy { FakeProotScript.installIn(tempFolder.newFolder("native")) }
  private val spaceOneDir by lazy {
    spaceDirIn(tempFolder.newFolder("spaces"), "space-1").apply {
      mkdirs()
      File(this, "config.json").writeText("{}")
      FakePylspScript.installIn(this)
    }
  }

  @Test
  fun startPylsp_provisionsASpaceThatHoldsOnlyItsConfig() {
    runPylsp()

    listOf("home/jupydroid", "run", "tmp", "pkgs").forEach { relative ->
      assertTrue("expected $relative under $spaceOneDir", File(spaceOneDir, relative).isDirectory)
    }
  }

  @Test
  fun startPylsp_runsInTheSpacesOwnWorkspaceAndEnvironment() {
    runPylsp()

    // cwd, HOME, PYTHONUSERBASE, TMPDIR - the order FakePylspScript records them in.
    assertEquals(
      listOf("/home/jupydroid", "/home/jupydroid", "/pkgs", "/tmp"),
      FakePylspScript.recordedIn(spaceOneDir),
    )
  }

  private fun runPylsp() {
    val process = startPylsp(SpaceRuntime(spaceOneDir, nativeDir), SpaceDirs(spaceOneDir))
    assertTrue("fake pylsp did not exit", process.waitFor(10, TimeUnit.SECONDS))
    assertEquals(0, process.exitValue())
  }
}
