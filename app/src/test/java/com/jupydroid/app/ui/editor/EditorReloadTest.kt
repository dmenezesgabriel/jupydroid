@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * `docmanager:reload` on a notebook: its cells are read back into the open editor.
 *
 * `context.revert()` (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`) re-reads the file
 * with `model.fromJSON` and leaves the model clean; a cell can write the `.ipynb` it lives in, or
 * another open one, so the editor's copy goes stale without anyone asking it to save.
 */
class EditorReloadTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val theirs = codeCell.copy(source = "theirs = 1")

  /** What a cell or a second client does: a write that asks nobody, straight onto the file. */
  private suspend fun NotebookRepository.rewrittenOutside() {
    saveNotebook(Notebook("nb-1", "Data", "python3", listOf(theirs)))
  }

  @Test
  fun aCleanNotebookReloadsTheCellsWrittenUnderIt() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    repository.rewrittenOutside()

    viewModel.document.reloadFromDisk()
    advanceUntilIdle()

    assertEquals(listOf("theirs = 1"), viewModel.uiState.value.cells.map { it.source })
    assertFalse(viewModel.uiState.value.dirty)
  }

  /** The reload took the disk's stamp with its cells, so the next save is an ordinary one. */
  @Test
  fun anEditAfterAReloadIsSavedWithoutAsking() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    repository.rewrittenOutside()
    viewModel.document.reloadFromDisk()
    advanceUntilIdle()

    viewModel.editCell(codeCell.id, "theirs = 1  # and mine")
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.saveConflict)
    assertEquals(listOf("theirs = 1  # and mine"), repository.readNotebook("nb-1").cells.map { it.source })
  }

  /**
   * The edit a reload drops was owed a debounced write, and that write must not follow the reload:
   * it would rewrite what was just read under a new modification time (ADR-0090).
   */
  @Test
  fun aReloadDropsTheWriteTheEditOwed() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    viewModel.editCell(codeCell.id, "ours = 2")
    repository.rewrittenOutside()
    val theirStamp = repository.readStampedNotebook("nb-1").stamp

    viewModel.document.reloadFromDisk()
    advanceUntilIdle()

    assertEquals(listOf("theirs = 1"), viewModel.uiState.value.cells.map { it.source })
    assertFalse(viewModel.uiState.value.dirty)
    assertFalse(viewModel.uiState.value.saveConflict)
    assertEquals("nothing may be written after a reload", theirStamp, repository.readStampedNotebook("nb-1").stamp)
  }
}
