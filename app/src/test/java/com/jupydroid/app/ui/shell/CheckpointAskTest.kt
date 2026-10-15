package com.jupydroid.app.ui.shell

import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docmanager:restore-checkpoint` short of its dialogs (jupyterlab@7f0e690
 * `packages/docmanager-extension/src/index.tsx`): what to ask, and the restore then the re-read.
 */
class CheckpointAskTest {
  @Test
  fun aDocumentWithNoCheckpointIsToldThereAreNone() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.py" to "as written"))
    val checkpoints = FakeDocumentCheckpoints(workspace)

    val ask = checkpointAskFor(RevertingDocument("notes.py"), checkpoints)

    assertEquals(CheckpointAsk.NoCheckpoints("File"), ask)
  }

  @Test
  fun aNotebookIsNamedAsOneWhenItHasNone() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("a.ipynb" to "{}"))
    val checkpoints = FakeDocumentCheckpoints(workspace)

    val ask = checkpointAskFor(RevertingDocument("a.ipynb"), checkpoints)

    assertEquals(CheckpointAsk.NoCheckpoints("Notebook"), ask)
  }

  @Test
  fun aDocumentWithACheckpointIsAskedAboutIt() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.py" to "saved"))
    val checkpoints = FakeDocumentCheckpoints(workspace)
    val made = checkpoints.create("notes.py")

    val ask = checkpointAskFor(RevertingDocument("notes.py"), checkpoints)

    assertEquals(CheckpointAsk.ToRevert("File", made), ask)
  }

  /** The restore, then the re-read: `restoreCheckpoint(id).then(() => context.revert())`. */
  @Test
  fun aRevertRestoresTheFileThenReadsItBack() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.py" to "saved"))
    val checkpoints = FakeDocumentCheckpoints(workspace)
    val made = checkpoints.create("notes.py")
    workspace.writeText("notes.py", "edited since")
    val document = RevertingDocument("notes.py")

    val refusal = revertToCheckpoint(document, made, checkpoints)

    assertEquals(null, refusal)
    assertEquals("saved", workspace.readText("notes.py"))
    assertEquals(1, document.reloads)
  }

  /** A restore the disk refuses reads nothing back, so the document keeps what is on screen. */
  @Test
  fun aRefusedRevertSaysWhyAndReadsNothingBack() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.py" to "saved"))
    val checkpoints = FakeDocumentCheckpoints(workspace)
    val made = checkpoints.create("notes.py")
    val document = RevertingDocument("elsewhere.py")

    val refusal = revertToCheckpoint(document, made, checkpoints)

    assertTrue("expected a reason, got $refusal", refusal?.contains("elsewhere.py") == true)
    assertEquals(0, document.reloads)
  }
}
