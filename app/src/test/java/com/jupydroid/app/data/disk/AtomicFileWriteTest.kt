package com.jupydroid.app.data.disk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files

class AtomicFileWriteTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private fun target(): File = File(tempFolder.root, "Analysis.ipynb")

  private fun temp(): File = File(tempFolder.root, ".~Analysis.ipynb")

  @Test
  fun aWriteThatFinishesReplacesTheFileAndLeavesNoTemp() {
    target().writeText("before")

    target().writeAtomically { it.write("after".toByteArray()) }

    assertEquals("after", target().readText())
    assertFalse(temp().exists())
  }

  @Test
  fun aWriteThatFailsPartwayLeavesThePreviousFileWhole() {
    target().writeText("before")

    assertThrows(IOException::class.java) {
      target().writeAtomically {
        it.write("half of aft".toByteArray())
        throw IOException("the disk went away")
      }
    }

    assertEquals("before", target().readText())
    assertFalse(temp().exists())
  }

  @Test
  fun aTempLeftByAnEarlierKillIsOverwrittenAndGone() {
    target().writeText("before")
    temp().writeText("a much longer half of a save that never finished")

    target().writeAtomically { it.write("after".toByteArray()) }

    assertEquals("after", target().readText())
    assertFalse(temp().exists())
  }

  @Test
  fun aFileThatDoesNotExistYetIsCreated() {
    target().writeAtomically { it.write("first".toByteArray()) }

    assertEquals("first", target().readText())
  }

  @Test
  fun aSymlinkedFileKeepsItsLinkAndItsTargetIsReplaced() {
    val real = File(tempFolder.newFolder("elsewhere"), "real.ipynb").apply { writeText("before") }
    Files.createSymbolicLink(target().toPath(), real.toPath())

    target().writeAtomically { it.write("after".toByteArray()) }

    assertEquals(true, Files.isSymbolicLink(target().toPath()))
    assertEquals("after", real.readText())
  }
}
