package com.jupydroid.app

import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Opening a document gives it a point to return to when it has none - `_maybeCheckpoint(false)` at
 * `_populate` (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`) - so Revert to Checkpoint
 * always has somewhere to go, and the user's own save moves it.
 */
class DocumentCheckpointingTest {
  @Test
  fun openingAFileWithNoCheckpointMakesOne() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.py" to "as opened"))
    val checkpoints = FakeDocumentCheckpoints(workspace)

    checkpoints.checkpointIfNone("notes.py")

    assertEquals(1, checkpoints.list("notes.py").size)
  }

  /** The user's last save is the point, and opening again must not move it to whatever is there now. */
  @Test
  fun openingAFileThatHasOneKeepsIt() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.py" to "saved"))
    val checkpoints = FakeDocumentCheckpoints(workspace)
    checkpoints.create("notes.py")
    workspace.writeText("notes.py", "written since by a cell")

    checkpoints.checkpointIfNone("notes.py")
    checkpoints.restore("notes.py", checkpoints.list("notes.py").single().id)

    assertEquals("saved", workspace.readText("notes.py"))
  }

  /** A recent whose file is gone still opens - to its own error - and the checkpoint is not what was asked for. */
  @Test
  fun openingAFileThatIsGoneMakesNoneAndDoesNotThrow() = runTest {
    val workspace = FakeWorkspaceRepository()
    val checkpoints = FakeDocumentCheckpoints(workspace)

    checkpoints.checkpointIfNone("gone.py")

    assertTrue(checkpoints.list("gone.py").isEmpty())
  }

  /** The user's save is the new point: a revert afterwards goes to it, not to the file as opened. */
  @Test
  fun aSaveTheUserAskedForMovesTheCheckpoint() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.py" to "as opened"))
    val checkpoints = FakeDocumentCheckpoints(workspace)
    checkpoints.checkpointIfNone("notes.py")
    workspace.writeText("notes.py", "saved")

    checkpoints.checkpointSaved("notes.py")
    workspace.writeText("notes.py", "edited since")
    checkpoints.restore("notes.py", checkpoints.list("notes.py").single().id)

    assertEquals("saved", workspace.readText("notes.py"))
  }

  @Test
  fun aSaveOfAFileThatIsGoneDoesNotThrow() = runTest {
    val workspace = FakeWorkspaceRepository()
    val checkpoints = FakeDocumentCheckpoints(workspace)

    checkpoints.checkpointSaved("gone.py")

    assertTrue(checkpoints.list("gone.py").isEmpty())
  }
}
