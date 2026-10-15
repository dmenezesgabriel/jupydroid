package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.StampedNotebook
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What an editor does when its notebook's file was rewritten under it.
 *
 * The workspace is the kernel's too: a cell can write the `.ipynb` it lives in, and the save that
 * follows used to replace what it wrote. `Context._maybeSave` asks instead - **File Changed**, with
 * Cancel, Revert and Overwrite (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`).
 */
class NotebookSaveConflictTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val theirs = codeCell.copy(source = "theirs = 1")

  /** What a cell or a second client does: a write that asks nobody, straight onto the file. */
  private suspend fun NotebookRepository.rewrittenOutside() {
    saveNotebook(Notebook("nb-1", "Data", "python3", listOf(theirs)))
  }

  private suspend fun NotebookRepository.sourcesOnDisk(): List<String> = readNotebook("nb-1").cells.map { it.source }

  /** An editor with an edit of its own, made after the file was rewritten, and the save attempted. */
  private suspend fun TestScope.editorAskedAboutAConflict(repository: NotebookRepository): EditorViewModel {
    val viewModel = loadedEditor(repository = repository)
    repository.rewrittenOutside()
    viewModel.editCell(codeCell.id, "ours = 2")
    advanceUntilIdle()
    return viewModel
  }

  @Test
  fun aSaveOverAnOutsideWriteAsksAndWritesNothing() = runTest {
    val repository = FakeNotebookRepository()

    val state = editorAskedAboutAConflict(repository).uiState.value

    assertTrue("expected the editor to be asking", state.saveConflict)
    assertTrue("expected the edit to still be owed", state.dirty)
    assertNull("a conflict is a question, not a failure", state.saveFailure)
    assertEquals(listOf("theirs = 1"), repository.sourcesOnDisk())
  }

  /**
   * A run's save is made without an edit having marked the notebook dirty first, so the refusal
   * has to: Cancel would otherwise leave a notebook that claims to be written while its outputs
   * are on no disk, and the close gate would let it go without asking.
   */
  @Test
  fun aRunWhoseSaveIsRefusedLeavesTheNotebookOwingAWrite() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    // A first run binds the kernel, which is an edit of its own and marks the notebook by itself.
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    assertFalse("expected the first run to have been saved", viewModel.uiState.value.dirty)
    repository.rewrittenOutside()

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertTrue("expected the editor to be asking", viewModel.uiState.value.saveConflict)
    assertTrue("expected the run's outputs to still be owed", viewModel.uiState.value.dirty)
  }

  @Test
  fun overwriteWritesTheEditorsCellsAndStopsAsking() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = editorAskedAboutAConflict(repository)

    viewModel.conflict.overwrite()
    advanceUntilIdle()

    assertEquals(listOf("ours = 2"), repository.sourcesOnDisk())
    assertFalse(viewModel.uiState.value.saveConflict)
    assertFalse(viewModel.uiState.value.dirty)
  }

  @Test
  fun revertShowsWhatIsOnDiskAndOwesNothing() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = editorAskedAboutAConflict(repository)

    viewModel.conflict.revert()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf("theirs = 1"), state.cells.map { it.source })
    assertFalse(state.saveConflict)
    assertFalse(state.dirty)
    assertEquals(listOf("theirs = 1"), repository.sourcesOnDisk())
  }

  /**
   * A cell's field holds its own copy of the text, so a cell that kept its id through the revert
   * has to be told its source changed - `EditorUiState.sourceRevisions` - or it goes on showing
   * what was typed.
   */
  @Test
  fun revertTellsARewrittenCellsFieldItsTextChanged() = runTest {
    val viewModel = editorAskedAboutAConflict(FakeNotebookRepository())
    val before = viewModel.uiState.value.sourceRevisions[codeCell.id] ?: 0

    viewModel.conflict.revert()
    advanceUntilIdle()

    assertEquals(before + 1, viewModel.uiState.value.sourceRevisions[codeCell.id])
  }

  /** The revert took the disk's stamp with its cells, so the next save is an ordinary one. */
  @Test
  fun anEditAfterARevertIsSavedWithoutAsking() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = editorAskedAboutAConflict(repository)
    viewModel.conflict.revert()
    advanceUntilIdle()

    viewModel.editCell(codeCell.id, "theirs = 1  # and mine")
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.saveConflict)
    assertEquals(listOf("theirs = 1  # and mine"), repository.sourcesOnDisk())
  }

  @Test
  fun cancelKeepsTheEditOwedAndTheFileAsItWasRewritten() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = editorAskedAboutAConflict(repository)

    viewModel.conflict.cancel()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.saveConflict)
    assertTrue("expected the edit to still be owed", state.dirty)
    assertEquals(listOf("ours = 2"), state.cells.map { it.source })
    assertEquals(listOf("theirs = 1"), repository.sourcesOnDisk())
  }

  /** Cancel answers one save, not the conflict: the file is still not the one that was read. */
  @Test
  fun theNextSaveAfterACancelAsksAgain() = runTest {
    val viewModel = editorAskedAboutAConflict(FakeNotebookRepository())
    viewModel.conflict.cancel()

    viewModel.editCell(codeCell.id, "ours = 3")
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.saveConflict)
  }

  /** Each save hands the next one its stamp, so an editor never trips over its own writes. */
  @Test
  fun anEditorsOwnSavesNeverAsk() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(repository = repository)

    viewModel.editCell(codeCell.id, "x = 1")
    advanceUntilIdle()
    viewModel.editCell(codeCell.id, "x = 22")
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.saveConflict)
    assertEquals(listOf("x = 22"), repository.sourcesOnDisk())
  }

  /** Save All waits on the write and must learn of the refusal: a refused save answers false. */
  @Test
  fun aSaveRefusedByAnOutsideWriteAnswersFalse() = runTest {
    val viewModel = editorAskedAboutAConflict(FakeNotebookRepository())

    assertFalse(viewModel.document.saveAndConfirm())
    assertTrue(viewModel.uiState.value.saveConflict)
  }

  @Test
  fun aSaveThatWritesAnswersTrue() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    viewModel.editCell(codeCell.id, "x = 1")

    assertTrue(viewModel.document.saveAndConfirm())
    assertEquals(listOf("x = 1"), repository.sourcesOnDisk())
  }

  /** The file can be gone or unreadable by the time Revert is tapped; that is reported, not fatal. */
  @Test
  fun aRevertThatCannotReadTheFileIsReportedAndKeepsTheWork() = runTest {
    val repository = UnreadableOnceAskedNotebookRepository()
    val viewModel = editorAskedAboutAConflict(repository)
    repository.refuseReads()

    viewModel.conflict.revert()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue("expected the reason, got ${state.saveFailure}", state.saveFailure?.contains("nb-1") == true)
    assertEquals(listOf("ours = 2"), state.cells.map { it.source })
    assertTrue(state.dirty)
  }
}

/** Reads until [refuseReads], then raises what the disk repository raises for a file that is gone. */
private class UnreadableOnceAskedNotebookRepository(
  private val delegate: FakeNotebookRepository = FakeNotebookRepository(),
) : NotebookRepository by delegate {
  private var refusing = false

  fun refuseReads() {
    refusing = true
  }

  override suspend fun readStampedNotebook(id: String): StampedNotebook {
    require(!refusing) { "no notebook file for id '$id'" }
    return delegate.readStampedNotebook(id)
  }
}
