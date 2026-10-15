package com.jupydroid.app.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [DocumentCheckpoints] owes a file: one point to return to, kept apart from the file.
 *
 * The shipped rootfs's `FileCheckpoints` keeps exactly one per file, under the id `"checkpoint"`
 * (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/filecheckpoints.py`), and a
 * second create replaces the first - that is the shape held here, by the disk and the fake alike.
 */
abstract class DocumentCheckpointsContract {
  /** The tree the files live in - what a checkpoint is made from and restored over. */
  protected abstract val workspace: WorkspaceRepository

  /** Checkpoints over [workspace]: the same files, so a test writes through one and checks the other. */
  protected abstract val checkpoints: DocumentCheckpoints

  @Test
  fun aFileNeverCheckpointedHasNone() = runTest {
    workspace.writeText(NOTES, "as written")

    assertEquals(emptyList<Any>(), checkpoints.list(NOTES))
  }

  @Test
  fun aCheckpointIsListedUnderTheIdItWasMadeWith() = runTest {
    workspace.writeText(NOTES, "as written")

    val made = checkpoints.create(NOTES)

    assertEquals(listOf(made), checkpoints.list(NOTES))
    assertEquals("checkpoint", made.id)
  }

  /** The point of a checkpoint: what the file held when it was made comes back over what it holds now. */
  @Test
  fun aRestorePutsBackWhatTheFileHeldAtTheCheckpoint() = runTest {
    workspace.writeText(NOTES, "saved")
    val made = checkpoints.create(NOTES)
    workspace.writeText(NOTES, "edited since")

    checkpoints.restore(NOTES, made.id)

    assertEquals("saved", workspace.readText(NOTES))
  }

  /** One per file: a later checkpoint replaces the earlier, so a restore goes to the latest. */
  @Test
  fun aSecondCheckpointReplacesTheFirst() = runTest {
    workspace.writeText(NOTES, "first save")
    checkpoints.create(NOTES)
    workspace.writeText(NOTES, "second save")
    val latest = checkpoints.create(NOTES)
    workspace.writeText(NOTES, "edited since")

    checkpoints.restore(NOTES, latest.id)

    assertEquals(1, checkpoints.list(NOTES).size)
    assertEquals("second save", workspace.readText(NOTES))
  }

  /** A restore leaves the checkpoint where it was, so the same point can be gone back to twice. */
  @Test
  fun aRestoreKeepsTheCheckpoint() = runTest {
    workspace.writeText(NOTES, "saved")
    val made = checkpoints.create(NOTES)

    checkpoints.restore(NOTES, made.id)

    assertEquals(listOf(made), checkpoints.list(NOTES))
  }

  /** Another file's checkpoint is not this one's: the point belongs to the path it was made for. */
  @Test
  fun aCheckpointBelongsToItsOwnFile() = runTest {
    workspace.writeText(NOTES, "notes")
    workspace.writeText(OTHER, "other")

    checkpoints.create(NOTES)

    assertTrue(checkpoints.list(OTHER).isEmpty())
  }

  private companion object {
    const val NOTES = "notes.py"
    const val OTHER = "other.py"
  }
}
