package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The notebook's half of [com.jupydroid.app.ui.fileeditor.FileEditorSaveFailureTest] - a save that
 * cannot be made is reported, not fatal.
 *
 * Same reachable cause: `WorkspaceFiles.changes` announces what this app moved, not what the
 * kernel did, so a cell that removes the notebook's folder leaves an editor writing to a path that
 * is not there. `saveNotebook` raised straight into `viewModelScope`.
 */
class NotebookSaveFailureTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aSaveIntoAWorkspaceThatMovedIsReportedRatherThanFatal() = runTest {
    val viewModel = editorWhoseWorkspaceWentAway()

    viewModel.editCell(codeCell.id, "cat = 2")
    advanceUntilIdle()

    val reported = viewModel.uiState.value.saveFailure
    assertTrue("expected the failed save to be reported, got $reported", reported?.contains("ENOENT") == true)
  }

  /**
   * **And the notebook stays dirty**, which is the half a guess gets wrong: `persist` cleared
   * `dirty` before the write, so a save that failed left a document claiming to be written. The
   * reference leaves it dirty and says so - the flag is *"cleared by the save, not by the edit"*,
   * and that is only true if the save happened.
   */
  @Test
  fun aFailedSaveLeavesTheNotebookDirty() = runTest {
    val viewModel = editorWhoseWorkspaceWentAway()

    viewModel.editCell(codeCell.id, "cat = 2")
    advanceUntilIdle()

    assertTrue("expected the notebook to still owe a write", viewModel.uiState.value.dirty)
    assertEquals("cat = 2", viewModel.uiState.value.cells.first().source)
  }
}
