package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the screen and the disk look like while a write is suspended on it.
 *
 * A write snapshots the cells and then suspends on the disk, so the editor goes on taking
 * operations. JupyterLab's `Context._save` only clears `dirty` when the write returns
 * (`packages/docregistry/src/context.ts:627-640`); it never puts the snapshot back over the
 * model, and nobody may cancel the write itself - a save superseded is not a save failed.
 */
@OptIn(ExperimentalCoroutinesApi::class) // `advanceTimeBy`/`runCurrent` - the debounce is on the test clock
class NotebookSaveInFlightTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aCellDeletedWhileTheWriteIsInFlightStaysDeleted() = runTest {
    val repository = SlowToSaveNotebookRepository()
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell), repository = repository)
    repository.holdSaves()
    viewModel.editCell(codeCell.id, "print(10)")
    advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)

    viewModel.edits.applyTo(secondCodeCell.id, CellAction.Delete)
    advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)
    drainHeldWrites(repository)

    assertEquals(listOf(codeCell.id), viewModel.uiState.value.cells.map { it.id })
    assertEquals(listOf(codeCell.id), repository.readNotebook("nb-1").cells.map { it.id })
  }

  /**
   * The later of two overlapping writes holds the newer snapshot - an older one landing second
   * must not put its older document back on disk.
   */
  @Test
  fun theDiskKeepsTheNewerOfTwoOverlappingWrites() = runTest {
    val repository = SlowToSaveNotebookRepository()
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell), repository = repository)
    repository.holdSaves()
    viewModel.editCell(codeCell.id, "print(10)")
    advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)
    viewModel.edits.applyTo(secondCodeCell.id, CellAction.Delete)
    advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)

    drainHeldWrites(repository)

    assertEquals(listOf(codeCell.id), repository.readNotebook("nb-1").cells.map { it.id })
  }

  /**
   * `dirty` is cleared by a write that covers the edit, not by whichever write happens to land -
   * an edit that reached the model mid-flight is still owed a write when the older one returns.
   */
  @Test
  fun anEditMadeWhileTheWriteIsInFlightStaysDirtyUntilItsOwnWriteLands() = runTest {
    val repository = SlowToSaveNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    repository.holdSaves()
    viewModel.editCell(codeCell.id, "print(10)")
    advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)
    runCurrent()

    viewModel.editCell(codeCell.id, "print(10); print(11)")
    repository.releaseNewest()
    runCurrent()

    assertTrue(viewModel.uiState.value.dirty)

    advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)
    drainHeldWrites(repository)

    assertFalse(viewModel.uiState.value.dirty)
  }

  /**
   * A run finishing while an earlier write is still on the disk is the other way two writes
   * overlap - the checkpoint `persist` takes no debounce, so nothing cancels it - and the older
   * write landing last must not take the run's outputs back off the document or the file.
   */
  @Test
  fun aRunEndingWhileAWriteIsInFlightIsNotErasedWhenTheOlderWriteLands() = runTest {
    val repository = SlowToSaveNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    repository.holdSaves()
    viewModel.editCell(codeCell.id, "print(10)")
    advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)
    runCurrent()

    viewModel.kernel.runCell(codeCell)
    drainHeldWrites(repository)

    assertTrue(viewModel.uiState.value.cells.first().outputs.isNotEmpty())
    assertTrue(repository.readNotebook("nb-1").cells.first().outputs.isNotEmpty())
  }

  /**
   * A write superseded by a later edit still ran - it is not a *failed* write. The debounce
   * restarts the delay, not the disk, so a cancelled `await` must never reach the screen as
   * `saveFailure` - or be lost at all: both writes complete, the newer one last.
   */
  @Test
  fun aWriteSupersededMidFlightIsNotCancelledOrReportedAsFailed() = runTest {
    val repository = SlowToSaveNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    // `loadedEditor` seeds the file through this same repository - that write is the baseline.
    val seeded = repository.completedWrites.size
    repository.holdSaves()
    viewModel.editCell(codeCell.id, "print(10)")
    advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)
    runCurrent()
    viewModel.editCell(codeCell.id, "print(10); print(11)")
    drainHeldWrites(repository)

    // The window between the first write landing and the second's debounce elapsing: a superseded
    // write is not a failure, and reporting it as one is the defect this reads.
    assertNull(viewModel.uiState.value.saveFailure)
    assertEquals(seeded + 1, repository.completedWrites.size)

    advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)
    drainHeldWrites(repository)

    assertEquals(seeded + 2, repository.completedWrites.size)
    assertEquals("print(10); print(11)", repository.readNotebook("nb-1").cells.first().source)
  }

  /**
   * Lets every write the repository is holding complete, in arrival order - first pumping once,
   * since a write that has not been dispatched yet is not being held yet either.
   */
  private fun TestScope.drainHeldWrites(repository: SlowToSaveNotebookRepository) {
    runCurrent()
    while (repository.writeIsHeld) {
      repository.releaseNewest()
      runCurrent()
    }
  }

  private companion object {
    /** Past `DocumentAutosave`'s 500 ms debounce, so the write has started and is held. */
    const val DEBOUNCE_ELAPSED_MILLIS = 501L
  }
}
