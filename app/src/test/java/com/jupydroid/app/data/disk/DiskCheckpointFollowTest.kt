package com.jupydroid.app.data.disk

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * A checkpoint goes where its file goes, as jupyter_server's `ContentsManager.rename` and `delete`
 * carry it with `rename_all_checkpoints` and `delete_all_checkpoints`
 * (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/manager.py`) - otherwise a
 * renamed file has no checkpoint to revert to and a deleted one leaves its copy behind.
 */
class DiskCheckpointFollowTest {
  @get:Rule val temp = TemporaryFolder()

  private val workspace by lazy { DiskWorkspaceRepository(temp.root) }
  private val files by lazy { DiskWorkspaceFiles(temp.root) }
  private val checkpoints by lazy { DiskDocumentCheckpoints(temp.root) }

  private fun checkpointFile(name: String) = File(temp.root, ".ipynb_checkpoints/$name")

  @Test
  fun aRenameMovesTheCheckpointToTheNewName() = runTest {
    workspace.writeText("notes.py", "saved")
    checkpoints.create("notes.py")

    files.rename("notes.py", "renamed.py")

    assertEquals(1, checkpoints.list("renamed.py").size)
    assertEquals("saved", checkpointFile("renamed-checkpoint.py").readText())
    assertFalse(checkpointFile("notes-checkpoint.py").exists())
  }

  @Test
  fun aRenameOfAFileWithNoCheckpointMovesOnlyTheFile() = runTest {
    workspace.writeText("notes.py", "saved")

    files.rename("notes.py", "renamed.py")

    assertEquals("saved", workspace.readText("renamed.py"))
    assertTrue(checkpoints.list("renamed.py").isEmpty())
  }

  /** `DocumentManager.overwrite` deletes the target, its checkpoint with it, then renames ours in. */
  @Test
  fun anOverwriteDropsTheTargetsCheckpointAndMovesOurs() = runTest {
    workspace.writeText("other.py", "ours")
    workspace.writeText("taken.py", "theirs")
    checkpoints.create("other.py")
    checkpoints.create("taken.py")

    files.overwrite("other.py", "taken.py")

    assertEquals("ours", checkpointFile("taken-checkpoint.py").readText())
    assertFalse(checkpointFile("other-checkpoint.py").exists())
  }

  @Test
  fun aDeleteRemovesTheCheckpoint() = runTest {
    workspace.writeText("notes.py", "saved")
    checkpoints.create("notes.py")

    files.delete("notes.py")

    assertFalse(checkpointFile("notes-checkpoint.py").exists())
  }

  /** `is_non_empty_dir`: "A directory containing only leftover checkpoints is considered empty." */
  @Test
  fun aFolderHoldingOnlyCheckpointsDeletesAsEmpty() = runTest {
    File(temp.root, "old/.ipynb_checkpoints").mkdirs()
    File(temp.root, "old/.ipynb_checkpoints/gone-checkpoint.py").writeText("left behind")

    files.delete("old")

    assertFalse(File(temp.root, "old").exists())
  }

  @Test
  fun aFolderWithAFileBesideItsCheckpointsIsStillRefused() = runTest {
    File(temp.root, "old/.ipynb_checkpoints").mkdirs()
    File(temp.root, "old/kept.py").writeText("kept")

    val refused = runCatching { files.delete("old") }

    assertTrue(refused.exceptionOrNull() is IllegalArgumentException)
    assertTrue(File(temp.root, "old/kept.py").isFile)
  }

  /** `shutil.rmtree` unlinks a link it meets rather than walking what it points at. */
  @Test
  fun aLinkedCheckpointFolderIsUnlinkedNotFollowed() = runTest {
    File(temp.root, "elsewhere").mkdir()
    File(temp.root, "elsewhere/precious.py").writeText("keep me")
    File(temp.root, "old").mkdir()
    val link = File(temp.root, "old/.ipynb_checkpoints").toPath()
    Files.createSymbolicLink(link, File(temp.root, "elsewhere").toPath())

    files.delete("old")

    assertFalse(File(temp.root, "old").exists())
    assertEquals("keep me", File(temp.root, "elsewhere/precious.py").readText())
  }
}
