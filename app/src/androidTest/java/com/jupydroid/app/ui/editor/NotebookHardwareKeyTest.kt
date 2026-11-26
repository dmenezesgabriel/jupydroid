package com.jupydroid.app.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The three run chords against a real [EditorScreen] - `performKeyInput` dispatches through the
 * same `onPreviewKeyEvent` a hardware or bluetooth keyboard reaches, which the JVM table test
 * ([NotebookKeymapTest]) cannot: whether the chord is *claimed* before the editor's own Enter
 * handling sees it, and whether the caret lands where the intent says.
 *
 * JupyterLab binds `Accel Enter`, `Shift Enter` and `Alt Enter` to `notebook:run`,
 * `notebook:run-and-advance` and `notebook:run-and-insert`
 * (`packages/notebook-extension/schema/tracker.json:1754-1773`).
 */
@OptIn(ExperimentalTestApi::class) // `performKeyInput`'s own opt-in requirement
@RunWith(AndroidJUnit4::class)
class NotebookHardwareKeyTest {
  @get:Rule val compose = createComposeRule()

  private val repository = FakeNotebookRepository()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var viewModel: EditorViewModel

  /**
   * `Ctrl`+`Enter` runs the cell where it stands. **The caret stays in the cell** - the classic
   * notebook's behaviour, kept deliberately where JupyterLab's `runSelected` would set
   * `mode = 'command'` (`packages/notebook/src/actions.tsx:3020`).
   */
  @Test
  fun ctrlEnter_runsTheCellAndKeepsTheCaret() {
    showEditing(CELL)

    field(0).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.executionCounts["c1"] == 1 }
    field(0).assertIsFocused()
    compose.runOnIdle { assertEquals(CellMode.EDIT, viewModel.uiState.value.mode) }
  }

  /** `Shift`+`Enter` runs the cell and moves on - the head is the next cell and the caret is gone. */
  @Test
  fun shiftEnter_runsTheCellAndAdvancesToTheNext() {
    showEditing(CELL, SECOND_CELL)

    field(0).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.executionCounts["c1"] == 1 }
    field(0).assertIsNotFocused()
    field(1).assertIsNotFocused()
    compose.runOnIdle {
      assertEquals(CellMode.COMMAND, viewModel.uiState.value.mode)
      assertEquals("c2", viewModel.uiState.value.selectedCellId)
    }
  }

  /**
   * `Shift`+`Enter` on the last cell appends a code cell and puts the caret in it
   * (`actions.tsx:830-849` - `mode = 'edit'` once the new widget is in view).
   */
  @Test
  fun shiftEnter_onTheLastCell_appendsACellAndEditsIt() {
    showEditing(CELL)

    field(0).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells.size == 2 }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.mode == CellMode.EDIT }
    compose.runOnIdle {
      val state = viewModel.uiState.value
      assertEquals(1, state.executionCounts["c1"])
      val appended = state.cells[1]
      assertEquals(CellKind.CODE, appended.kind)
      assertEquals("", appended.source)
      assertEquals(appended.id, state.selectedCellId)
    }
  }

  /**
   * `Alt`+`Enter` runs the cell and opens a fresh code cell below it, caret first
   * (`actions.tsx:864-918`).
   */
  @Test
  fun altEnter_runsTheCellAndEditsANewOneBelow() {
    showEditing(CELL, SECOND_CELL)

    field(0).performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.Enter) } }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells.size == 3 }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.mode == CellMode.EDIT }
    compose.runOnIdle {
      val state = viewModel.uiState.value
      assertEquals(1, state.executionCounts["c1"])
      assertEquals(CellKind.CODE, state.cells[1].kind)
      assertEquals("c2", state.cells[2].id)
      assertEquals(state.cells[1].id, state.selectedCellId)
    }
  }

  /** Keyboards that send `KEYCODE_NUMPAD_ENTER` run too - the chord keys on the *key*, not the pad. */
  @Test
  fun ctrlNumpadEnter_runsTheCellLikeCtrlEnter() {
    showEditing(CELL)

    field(0).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.NumPadEnter) } }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.executionCounts["c1"] == 1 }
  }

  /** An unmodified `Enter` is the editor's own newline - no run, no head move, still editing. */
  @Test
  fun bareEnter_staysANewline() {
    showEditing(CELL)

    field(0).performKeyInput { pressKey(Key.Enter) }

    compose.runOnIdle {
      val state = viewModel.uiState.value
      assertTrue(state.executionCounts.isEmpty())
      assertEquals("c1", state.selectedCellId)
      assertEquals(CellMode.EDIT, state.mode)
    }
  }

  private fun field(index: Int) = compose.onAllNodes(hasSetTextAction())[index]

  /** The notebook open on the given cells, the first field tapped, so it is being edited. */
  private fun showEditing(vararg cells: Cell) {
    runBlocking { repository.saveNotebook(Notebook(NOTEBOOK_ID, "Data", "python3", cells.toList())) }
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
          notebookCells = OpenNotebookCells(),
          onShowDebugger = null,
          onGoToFolder = {},
          onBack = {},
          viewModel = viewModel,
        )
      }
    }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells.isNotEmpty() }
    field(0).performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.mode == CellMode.EDIT }
  }

  private companion object {
    const val NOTEBOOK_ID = "nb-1"
    val CELL = Cell(id = "c1", kind = CellKind.CODE, source = "pass")
    val SECOND_CELL = Cell(id = "c2", kind = CellKind.CODE, source = "pass")
    const val TIMEOUT_MILLIS = 5_000L
  }
}
