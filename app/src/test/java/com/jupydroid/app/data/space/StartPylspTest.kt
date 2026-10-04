package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * **A Space's `pylsp` runs in that Space**, not base's: its cwd and the variables that place its
 * writes all resolve under the Space's own directory, provisioned on the spot from a directory
 * holding nothing but `config.json`. The script itself comes from base's rootfs.
 *
 * A real process: [FakePylspScript] stands in for the console script, so what is asserted is what
 * the process actually saw.
 */
class StartPylspTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val spacesDir by lazy { tempFolder.newFolder("spaces") }
  private val baseDir by lazy { spaceDirIn(spacesDir, "base").apply { FakePylspScript.installIn(this) } }
  private val spaceOneDir by lazy {
    spaceDirIn(spacesDir, "space-1").apply {
      mkdirs()
      File(this, "config.json").writeText("{}")
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

    val workspace = notebookWorkspaceIn(spaceOneDir)
    // cwd, HOME, PYTHONUSERBASE, TMPDIR - the order FakePylspScript records them in.
    assertEquals(
      listOf(workspace, workspace, File(spaceOneDir, "pkgs"), File(spaceOneDir, "tmp")).map { it.canonicalPath },
      FakePylspScript.recordedIn(spaceOneDir).map { File(it).canonicalPath },
    )
    assertFalse("base's pkgs/ must stay untouched", File(baseDir, "pkgs").exists())
  }

  private fun runPylsp() {
    val runtime = SpaceRuntime(spaceOneDir, File("/bin/false"), rootfsDir = baseDir)
    val process = startPylsp(runtime, SpaceDirs(spaceOneDir), baseDir)
    assertTrue("fake pylsp did not exit", process.waitFor(10, TimeUnit.SECONDS))
    assertEquals(0, process.exitValue())
  }
}
