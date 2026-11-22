package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.OpenDebugStack
import com.jupydroid.app.OpenDocumentOutline
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.OpenNotebookCells
import com.jupydroid.app.data.fake.FakeDocumentSharer
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupydroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Code inserted below the active cell from outside the notebook - a chat's Insert, through the
 * [OpenNotebookCells] the editor publishes - takes the caret out of the cell being edited, as
 * VS Code's chat insert focuses no cell editor (vscode@3f3380c
 * `src/vs/workbench/contrib/notebook/browser/controller/cellOperations.ts` `insertCell`).
 *
 * Asked of a real [EditorScreen] because the mode is where the focus is: a state that says command
 * mode while the field still holds the caret passed `CellInsertedAtTest` and still sent the next
 * keystroke into the cell that was no longer active, on SM-X216B.
 */
@RunWith(AndroidJUnit4::class)
class InsertBelowActiveCellCaretTest {
  @get:Rule val compose = createComposeRule()

  private val repository = FakeNotebookRepository()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val notebookCells = OpenNotebookCells()
  private lateinit var viewModel: EditorViewModel

  @Test
  fun theCellBeingEditedLosesTheCaret() {
    showEditing()

    compose.runOnIdle { notebookCells.insertBelow("print(6*7)") }

    compose.onAllNodes(hasSetTextAction())[0].assertIsNotFocused()
  }

  @Test
  fun theNotebookIsLeftInCommandModeOnTheNewCell() {
    showEditing()

    compose.runOnIdle { notebookCells.insertBelow("print(6*7)") }

    compose.runOnIdle {
      val state = viewModel.uiState.value
      assertEquals(CellMode.COMMAND, state.mode)
      assertEquals(listOf("pass", "print(6*7)"), state.cells.map { it.source })
      assertEquals(state.cells[1].id, state.selectedCellId)
    }
  }

  /** The notebook open on one code cell, its field tapped, so it is being edited. */
  private fun showEditing() {
    runBlocking { repository.saveNotebook(Notebook(NOTEBOOK_ID, "Data", "python3", listOf(CELL))) }
    viewModel = EditorViewModel(
      notebookId = NOTEBOOK_ID,
      notebookRepository = repository,
      workspaceRepository = FakeWorkspaceRepository(),
      kernelGateway = FakeKernelGateway(),
      notebookExporter = FakeNotebookExporter(),
      applicationScope = scope,
      activeCellRuns = ActiveCellRuns(),
      openDocuments = OpenDocuments(),
    )
    compose.setContent {
      JupydroidTheme {
        EditorScreen(
          notebookId = NOTEBOOK_ID,
          notebookRepository = repository,
          workspaceRepository = FakeWorkspaceRepository(),
          kernelGateway = FakeKernelGateway(),
          kernelDebugger = FakeKernelDebugger(),
          notebookExporter = FakeNotebookExporter(),
          documentSharer = FakeDocumentSharer(),
          applicationScope = scope,
          activeCellRuns = ActiveCellRuns(),
          editorSettingsRepository = FakeEditorSettingsRepository(),
          outline = OpenDocumentOutline(),
          onShowTableOfContents = null,
          debugPanel = OpenDebugStack(),
          notebookCells = notebookCells,
          onShowDebugger = null,
          onGoToFolder = {},
          onBack = {},
          viewModel = viewModel,
        )
      }
    }
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) { viewModel.uiState.value.cells.isNotEmpty() }
    compose.onAllNodes(hasSetTextAction())[0].performClick()
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) { notebookCells.available && viewModel.uiState.value.mode == CellMode.EDIT }
  }

  private companion object {
    const val NOTEBOOK_ID = "nb-1"
    val CELL = Cell(id = "c1", kind = CellKind.CODE, source = "pass")
    const val LOAD_TIMEOUT_MILLIS = 5_000L
  }
}
