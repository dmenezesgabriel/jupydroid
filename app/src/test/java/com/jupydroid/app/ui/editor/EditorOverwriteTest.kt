package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * "…already exists, overwrite?" - asked, and then obeyed.
 *
 * The reference's flow, from `packages/docmanager/src/dialogs.ts`: `renameFile` calls the server,
 * catches **only** a 409, and asks `shouldOverwrite(newPath)`; if the user accepts it calls
 * `manager.overwrite(oldPath, newPath)`, and if they decline the rename simply does not happen.
 *
 * Three things follow, and each is a case here: the question is only ever asked for a name that is
 * taken, accepting replaces the file, and declining leaves the notebook with the name it had.
 */
class EditorOverwriteTest {
  @get:Rule val dispatcher = MainDispatcherRule()

  private val repository = FakeNotebookRepository()

  @Test
  fun aTakenNameAsksBeforeReplacingAnything() = runTest {
    val viewModel = editorOnDraftWith("Taken")

    viewModel.renameNotebook("Taken")

    assertEquals("Taken", viewModel.uiState.value.nameTaken)
    assertEquals("the name must not change until the question is answered", "Draft", viewModel.uiState.value.title)
  }

  /** An invalid name is not a question to put to the user - `renameFile` rethrows anything but 409. */
  @Test
  fun anInvalidNameIsNotOfferedAnOverwrite() = runTest {
    val viewModel = editorOnDraftWith("Taken")

    viewModel.renameNotebook("reports/final")

    assertNull(viewModel.uiState.value.nameTaken)
  }

  @Test
  fun acceptingReplacesTheFileAndTakesItsName() = runTest {
    val viewModel = editorOnDraftWith("Taken")
    viewModel.renameNotebook("Taken")

    viewModel.answerOverwrite(accepted = true)

    assertEquals("Taken", viewModel.uiState.value.title)
    assertEquals("Taken", viewModel.uiState.value.notebookId)
    assertNull(viewModel.uiState.value.nameTaken)
  }

  @Test
  fun decliningKeepsTheNameItHad() = runTest {
    val viewModel = editorOnDraftWith("Taken")
    viewModel.renameNotebook("Taken")

    viewModel.answerOverwrite(accepted = false)

    assertEquals("Draft", viewModel.uiState.value.title)
    assertNull(viewModel.uiState.value.nameTaken)
  }

  /**
   * The id in state follows the file, which is what tells the list which row is open.
   *
   * `Context._updatePath` reassigns the live path and `_contextsForPath` looks up by it; a
   * navigation key frozen at open time cannot answer that question after a rename.
   */
  @Test
  fun theIdInStateFollowsTheRename() = runTest {
    val viewModel = editorOnDraftWith("Taken")

    viewModel.renameNotebook("Final")

    assertEquals("Final", viewModel.uiState.value.notebookId)
  }

  private suspend fun editorOnDraftWith(alsoPresent: String): EditorViewModel {
    repository.saveNotebook(Notebook("Draft", "Draft", "python3", listOf(Cell("c1", CellKind.CODE, "x = 1"))))
    repository.saveNotebook(Notebook(alsoPresent, alsoPresent, "python3", emptyList()))
    return EditorViewModel(
      notebookId = "Draft",
      notebookRepository = repository,
      workspaceRepository = FakeWorkspaceRepository(),
      kernelGateway = FakeKernelGateway(),
      notebookExporter = FakeNotebookExporter(),
      applicationScope = CoroutineScope(UnconfinedTestDispatcher()),
      activeCellRuns = ActiveCellRuns(),
    )
  }
}
