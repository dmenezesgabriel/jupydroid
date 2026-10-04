package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A Space's writable skeleton appears the first time it is launched, from a directory that may hold
 * nothing but `config.json` - which is all `DiskSpaceRepository` writes, and all its scan needs to
 * count the directory as a Space (ADR-0010).
 */
class SpaceDirsTest {
  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun ensure_provisionsTheWritableDirsOfASpaceThatHoldsOnlyItsConfig() {
    val spaceDir = configOnlySpace()

    SpaceDirs(spaceDir).ensure()

    listOf("home/jupydroid", "run", "tmp", "pkgs").forEach { relative ->
      assertTrue("expected $relative under $spaceDir", File(spaceDir, relative).isDirectory)
    }
  }

  @Test
  fun ensure_leavesWhatIsAlreadyThereAlone() {
    val spaceDir = configOnlySpace()
    val notebook = File(spaceDir, "home/jupydroid/kept.ipynb").apply {
      parentFile!!.mkdirs()
      writeText("{}")
    }

    SpaceDirs(spaceDir).ensure()
    SpaceDirs(spaceDir).ensure()

    assertEquals("{}", notebook.readText())
  }

  @Test
  fun ensure_namesTheDirectoryItCouldNotCreate() {
    val spaceDir = configOnlySpace()
    File(spaceDir, "run").writeText("a file where a directory must go")

    val error = assertThrows(IllegalStateException::class.java) { SpaceDirs(spaceDir).ensure() }

    assertTrue(error.message!!, File(spaceDir, "run").path in error.message!!)
  }

  @Test
  fun eachWritableDirIsNamedOnceUnderTheSpace() {
    val spaceDir = File("/data/data/com.jupydroid.app/files/spaces/space-1")

    assertEquals(notebookWorkspaceIn(spaceDir), SpaceDirs(spaceDir).workspace)
    assertEquals(File(spaceDir, "run"), SpaceDirs(spaceDir).runtime)
    assertEquals(File(spaceDir, "tmp"), SpaceDirs(spaceDir).tmp)
    assertEquals(File(spaceDir, "pkgs"), SpaceDirs(spaceDir).pkgs)
  }

  private fun configOnlySpace(): File = tempFolder.newFolder("space-1").apply {
    File(this, "config.json").writeText("{}")
  }
}
