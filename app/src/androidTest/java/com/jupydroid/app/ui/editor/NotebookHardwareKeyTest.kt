package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
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
import com.jupydroid.app.theme.FocusRingEdgeProbe
import com.jupydroid.app.theme.JupydroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notebook's key chords against a real [EditorScreen] - `performKeyInput` dispatches through
 * the same `onPreviewKeyEvent` a hardware or bluetooth keyboard reaches, which the JVM table test
 * ([NotebookKeymapTest]) cannot: whether the chord is *claimed* before the focused node's own key
 * handling sees it, and where the caret lands.
 *
 * The rows under test are `tracker.json`'s `notebook:run`, `notebook:run-and-advance`,
 * `notebook:run-and-insert` (`:1754-1773`) and the mode-crossing pair `notebook:enter-edit-mode` /
 * `notebook:enter-command-mode` (`:443-466`), plus the focus the command mode owns them through.
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
    // `focusActiveCell` (`actions.tsx:2585`): the head moved, and command mode's focus followed it.
    assertFrameFocused()
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

  /**
   * `notebook:enter-command-mode` on `Escape` (`tracker.json:457-462`). Upstream "just unfocuses
   * the editor"; ours moves focus onto the head cell's frame - the surface command mode owns its
   * keys through - and the blur that reports `COMMAND` is the one path out of edit mode.
   */
  @Test
  fun escape_leavesEditAndFocusesTheFrame() {
    showEditing(CELL)

    field(0).performKeyInput { pressKey(Key.Escape) }

    assertFrameFocused()
    compose.runOnIdle { assertEquals(CellMode.COMMAND, viewModel.uiState.value.mode) }
  }

  /** `Ctrl M` is the same command on a second binding (`tracker.json:464-469`). */
  @Test
  fun ctrlM_leavesEditAndFocusesTheFrame() {
    showEditing(CELL)

    field(0).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.M) } }

    assertFrameFocused()
    compose.runOnIdle { assertEquals(CellMode.COMMAND, viewModel.uiState.value.mode) }
  }

  /**
   * `notebook:enter-edit-mode` on `Enter` (`tracker.json:443-448`) - the frame holds focus in
   * command mode, its Enter asks for the caret, and the field taking it is what reports `EDIT`.
   */
  @Test
  fun enterOnTheFrame_editsTheCell() {
    showEditing(CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { pressKey(Key.Enter) }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.mode == CellMode.EDIT }
    field(0).assertIsFocused()
  }

  /**
   * The selector half of the same row: `.jp-Cell:focus`, not `:focus-within` - an `Enter` that
   * lands on a focused `⋮` is the button's own activation, and the editor is not asked for.
   */
  @Test
  fun enterOnAFocusedHeaderButton_opensItsMenu() {
    showEditing(CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    val overflow = compose.onNodeWithContentDescription("Cell actions")
    overflow.performSemanticsAction(SemanticsActions.RequestFocus)
    // The gate `Enter` is about to meet reads the frame's own snapshot flag, which the blur writes
    // a beat after the semantics `Focused` does - wait on the button's focus landing, the
    // observable edge of the same transaction, rather than hoping the write applied (the tablet
    // races it).
    compose.waitUntil(TIMEOUT_MILLIS) {
      overflow.fetchSemanticsNode().config[SemanticsProperties.Focused]
    }
    overflow.performKeyInput { pressKey(Key.Enter) }

    compose.onNodeWithContentDescription("Cell menu Insert code below").assertIsDisplayed()
    compose.runOnIdle {
      val state = viewModel.uiState.value
      assertEquals(CellMode.COMMAND, state.mode)
      assertNull(state.cellTakingCaret)
      assertNull(state.markdownTakingCaret)
    }
  }

  /**
   * `_evtMouseDown` (`widget.ts:3565-3600`): a tap on the chrome of a cell that is not the head -
   * padding, not a control - selects the cell into command mode and moves the focus with it.
   */
  @Test
  fun aTapOnTheChrome_selectsWithoutEditing() {
    showEditing(CELL, SECOND_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    cellFrame("c2").performTouchInput { click(Offset(4f, 4f)) }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.selectedCellId == "c2" }
    assertFrameFocused()
    compose.runOnIdle { assertEquals(CellMode.COMMAND, viewModel.uiState.value.mode) }
  }

  /**
   * The same tap on the head's own chrome: there is no selection change for the focus-follow
   * effect to chase, so the tap asks for the frame's focus directly. The `⋮` stands in for
   * whatever had focus, which is the case this path exists for.
   */
  @Test
  fun aTapOnTheHeadsOwnChrome_refocusesTheFrame() {
    showEditing(CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }
    compose.onNodeWithContentDescription("Cell actions")
      .performSemanticsAction(SemanticsActions.RequestFocus)

    cellFrame("c1").performTouchInput { click(Offset(4f, 4f)) }

    assertFrameFocused()
    compose.runOnIdle { assertEquals(CellMode.COMMAND, viewModel.uiState.value.mode) }
  }

  /**
   * Keyboard focus on the frame draws the `focusBorder` ring - the frame's `isFocused`, not the
   * container's `hasFocus`, because a `⋮` focused inside the same cell must not ring the cell.
   */
  @Test
  fun aKeyboardFocusOnTheFrame_drawsTheRing() {
    val probe = FocusRingEdgeProbe(compose)
    viewModel = notebookViewModel(CELL)
    probe.show(darkTheme = false) { editorScreen(viewModel) }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells.isNotEmpty() }
    compose.runOnIdle { viewModel.selection.activate("c1") }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.selectedCellId == "c1" }

    probe.assertKeyboardFocusDrawsTheRing(headFrame(), edgeOf = headFrame())
  }

  private fun field(index: Int) = compose.onAllNodes(hasSetTextAction())[index]
  private fun headFrame() = compose.onNodeWithTag(HEAD_CELL_FRAME_TAG)
  private fun cellFrame(cellId: String) = compose.onNode(SemanticsMatcher.expectValue(CellFrameIdKey, cellId))

  /**
   * The frame's own focus, waited for rather than asserted straight off: `requestFocus` settles
   * through the focus transaction's blur-then-grant events. It is read on [FrameOwnsFocusKey]
   * rather than `assertIsFocused`, because the framework's `focused` semantics only re-applies
   * when the node's semantics are invalidated - and a programmatic `requestFocus` invalidates
   * nothing, leaving the flag stale long after the target is Active.
   */
  private fun assertFrameFocused() {
    compose.waitUntil(TIMEOUT_MILLIS) {
      runCatching {
        headFrame().fetchSemanticsNode().config[FrameOwnsFocusKey]
      }.getOrDefault(false)
    }
    headFrame().assert(SemanticsMatcher.expectValue(FrameOwnsFocusKey, true))
  }

  /** The notebook open on the given cells, the first field tapped, so it is being edited. */
  private fun showEditing(vararg cells: Cell) {
    viewModel = notebookViewModel(*cells)
    compose.setContent { JupydroidTheme { editorScreen(viewModel) } }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells.isNotEmpty() }
    field(0).performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.mode == CellMode.EDIT }
  }

  private fun notebookViewModel(vararg cells: Cell): EditorViewModel {
    runBlocking { repository.saveNotebook(Notebook(NOTEBOOK_ID, "Data", "python3", cells.toList())) }
    return EditorViewModel(
      notebookId = NOTEBOOK_ID,
      notebookRepository = repository,
      workspaceRepository = FakeWorkspaceRepository(),
      kernelGateway = FakeKernelGateway(),
      notebookExporter = FakeNotebookExporter(),
      applicationScope = scope,
      activeCellRuns = ActiveCellRuns(),
      openDocuments = OpenDocuments(),
    )
  }

  @Composable
  private fun editorScreen(viewModel: EditorViewModel) = EditorScreen(
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

  private companion object {
    const val NOTEBOOK_ID = "nb-1"
    val CELL = Cell(id = "c1", kind = CellKind.CODE, source = "pass")
    val SECOND_CELL = Cell(id = "c2", kind = CellKind.CODE, source = "pass")
    const val TIMEOUT_MILLIS = 5_000L
  }
}
