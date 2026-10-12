package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Notebook
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [NotebookRepository] must do, whoever implements it.
 *
 * Run against the fake and the disk one alike: a port whose implementations agree only by
 * inspection is a port that will drift, and both were wrong about the same thing here - a
 * notebook you edited kept the timestamp it was created with.
 *
 * **Every write is an update.** Jupyter takes `last_modified` straight off the file
 * (`jupyter_server/services/contents/filemanager.py:285`, `os.lstat().st_mtime`), so editing,
 * running, renaming and creating all move a notebook to the top of Recents because they all
 * write the file. There is no notion of an edit that does not count.
 *
 * Written as *ordering* rather than as a comparison against the clock, because that is the
 * behaviour a person sees and because it needs no clock to inject and no sleep to be reliable:
 * touch the older notebook and it must not still sort below the newer one.
 */
abstract class NotebookRepositoryContract {
  protected abstract fun repository(): NotebookRepository

  /**
   * An edit is written where it can be read back, which is all a save owes.
   *
   * This used to assert the edit also moved the notebook to the top of Recents. That ordering was
   * a property of Recents-as-a-scan and went with it; when a file was last written is now
   * `WorkspaceEntry.updatedAt`, and `WorkspaceRepositoryContract` holds every implementation to it.
   */
  @Test
  fun savingANotebookKeepsTheEdit() = runTest {
    val repository = repository()
    val edited = repository.createNotebook(title = "Edited", kernel = BASE_SPACE.kernels.single())

    repository.saveNotebook(
      repository.readNotebook(edited.id).copy(cells = listOf(Cell("c1", CellKind.CODE, "x = 1"))),
    )

    assertEquals(listOf("x = 1"), repository.readNotebook(edited.id).cells.map { it.source })
  }

  /** A new notebook records the kernel it was created for, whichever the Space's default is. */
  @Test
  fun aNewNotebookRecordsItsKernel() = runTest {
    val repository = repository()

    val created = repository.createNotebook(
      title = "Thesis",
      kernel = KernelSpec("xpython", "Python 3.13 (XPython)", "python"),
    )

    assertEquals("xpython", repository.readNotebook(created.id).kernelId)
  }

  /**
   * A save handed the stamp of its read is refused once someone else has written the file, and
   * what they wrote is still there. "Someone else" is a save with no stamp: the port's own
   * unconditional write, which is what a kernel's `open(path, "w")` is to this repository.
   */
  @Test
  fun aSaveOverANotebookWrittenSinceItWasReadIsRefused() = runTest {
    val repository = repository()
    val opened = repository.readStampedNotebook(newNotebookIn(repository))
    repository.saveNotebook(opened.notebook.withSource("theirs = 1"))

    val refusal = runCatching { repository.saveNotebook(opened.notebook.withSource("ours = 2"), opened.stamp) }

    assertTrue("expected a refusal, got $refusal", refusal.exceptionOrNull() is FileChangedOnDiskException)
    assertEquals(listOf("theirs = 1"), repository.readNotebook(opened.notebook.id).cells.map { it.source })
  }

  /** Each save answers with the stamp the next one expects, so an editor's own writes never refuse it. */
  @Test
  fun aSaveHandedTheStampOfTheLastSaveGoesThrough() = runTest {
    val repository = repository()
    val opened = repository.readStampedNotebook(newNotebookIn(repository))

    val first = repository.saveNotebook(opened.notebook.withSource("x = 1"), opened.stamp)
    repository.saveNotebook(opened.notebook.withSource("x = 22"), first)

    assertEquals(listOf("x = 22"), repository.readNotebook(opened.notebook.id).cells.map { it.source })
  }

  /** A file that already holds what the save would write has not changed in any way that matters. */
  @Test
  fun aSaveOfWhatIsAlreadyThereIsNotAConflict() = runTest {
    val repository = repository()
    val opened = repository.readStampedNotebook(newNotebookIn(repository))
    val same = opened.notebook.withSource("x = 1")
    repository.saveNotebook(same)

    repository.saveNotebook(same, opened.stamp)

    assertEquals(listOf("x = 1"), repository.readNotebook(same.id).cells.map { it.source })
  }

  /** The stamp of a read is the stamp of the save before it, so reopening owes no conflict. */
  @Test
  fun aReadAnswersTheStampTheLastSaveLeft() = runTest {
    val repository = repository()
    val opened = repository.readStampedNotebook(newNotebookIn(repository))

    val saved = repository.saveNotebook(opened.notebook.withSource("x = 1"), opened.stamp)

    assertEquals(saved, repository.readStampedNotebook(opened.notebook.id).stamp)
  }

  private suspend fun newNotebookIn(repository: NotebookRepository): String =
    repository.createNotebook(title = "Shared", kernel = BASE_SPACE.kernels.single()).id

  private fun Notebook.withSource(source: String): Notebook = copy(cells = listOf(Cell("c1", CellKind.CODE, source)))
}
