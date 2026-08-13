package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * A rename the repository refuses must not take the app down with it.
 *
 * X1b gave `renameNotebook` the power to fail: the name *is* the file now, so `a/b` is not a name
 * and a name already taken would overwrite somebody's notebook. jupyter_server answers the latter
 * with 409 and JupyterLab catches exactly that to ask the user
 * (`packages/docmanager/src/dialogs.ts`, `renameFile`).
 *
 * What must not happen is what happened when the contract changed: the ViewModel called it from a
 * bare `viewModelScope.launch`, so the refusal became an uncaught exception in a coroutine and
 * crashed the process. Typing a `/` into the rename dialog was enough.
 */
class EditorRenameRefusedTest {
  @get:Rule val dispatcher = MainDispatcherRule()

  @Test
  fun aRefusedRenameLeavesTheTitleAsItWas() = runTest {
    val viewModel = editorOn(RefusingRepository())

    viewModel.renameNotebook("reports/final")

    assertEquals("Draft", viewModel.uiState.value.title)
  }

  @Test
  fun aRefusedRenameKeepsTheNotebookUsable() = runTest {
    val viewModel = editorOn(RefusingRepository())

    viewModel.renameNotebook("reports/final")
    viewModel.editCell("c1", "still editable")

    assertEquals("still editable", viewModel.uiState.value.cells.single().source)
  }

  private fun editorOn(repository: NotebookRepository) =
    EditorViewModel(
      notebookId = "Draft",
      notebookRepository = repository,
      workspaceRepository = FakeWorkspaceRepository(),
      kernelGateway = FakeKernelGateway(),
      applicationScope = CoroutineScope(UnconfinedTestDispatcher()),
    )

  /** Answers reads, and refuses every rename the way the disk repository refuses a bad name. */
  private class RefusingRepository : NotebookRepository by FakeNotebookRepository() {
    override suspend fun readNotebook(id: String) =
      Notebook(id, id, "python3", listOf(Cell("c1", CellKind.CODE, "x = 1")))

    override suspend fun renameNotebook(id: String, title: String): String =
      throw IllegalArgumentException("notebook name '$title' is not valid")
  }
}
