package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException

/**
 * The notebook's half of [com.jupydroid.app.ui.fileeditor.FileEditorSaveFailureTest] - a save that
 * cannot be made is reported, not fatal.
 *
 * Same reachable cause: `WorkspaceRepository.changes` announces what this app moved, not what the
 * kernel did, so a cell that removes the notebook's folder leaves an editor writing to a path that
 * is not there. `saveNotebook` raised straight into `viewModelScope`.
 */
class NotebookSaveFailureTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /** Reads like the tree it was, refuses every write - the directory went while this was open. */
  private class VanishedNotebookRepository(private val stillReadable: NotebookRepository = FakeNotebookRepository()) :
    NotebookRepository by stillReadable {
    override suspend fun saveNotebook(notebook: Notebook): Unit =
      throw FileNotFoundException("${notebook.id}: open failed: ENOENT (No such file or directory)")
  }

  @Test
  fun aSaveIntoAWorkspaceThatMovedIsReportedRatherThanFatal() = runTest {
    val viewModel = anEditorWhoseWorkspaceWentAway()

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
    val viewModel = anEditorWhoseWorkspaceWentAway()

    viewModel.editCell(codeCell.id, "cat = 2")
    advanceUntilIdle()

    assertTrue("expected the notebook to still owe a write", viewModel.uiState.value.dirty)
    assertEquals("cat = 2", viewModel.uiState.value.cells.first().source)
  }

  /**
   * A notebook that loaded from a tree that then refused every write.
   *
   * Seeded through the plain repository and only *then* wrapped, because `loadedEditor` writes the
   * fixture with the repository it is handed - a refusing one would fail the seed rather than the
   * case.
   */
  private fun kotlinx.coroutines.test.TestScope.anEditorWhoseWorkspaceWentAway(): EditorViewModel {
    val readable = FakeNotebookRepository()
    launch { readable.saveNotebook(Notebook("nb-1", "Data", "python3", listOf(codeCell))) }
    advanceUntilIdle()
    val viewModel = EditorViewModel(
      notebookId = "nb-1",
      notebookRepository = VanishedNotebookRepository(readable),
      workspaceRepository = com.jupydroid.app.data.fake.FakeWorkspaceRepository(),
      kernelGateway = com.jupydroid.app.data.fake.FakeKernelGateway(),
      notebookExporter = com.jupydroid.app.data.fake.FakeNotebookExporter(),
      applicationScope = this,
      activeCellRuns = com.jupydroid.app.domain.model.ActiveCellRuns(),
    )
    advanceUntilIdle()
    return viewModel
  }
}
