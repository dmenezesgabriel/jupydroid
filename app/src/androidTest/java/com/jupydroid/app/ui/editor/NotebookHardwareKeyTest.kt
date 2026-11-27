package com.jupydroid.app.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.theme.FocusRingEdgeProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class NotebookHardwareKeyTest : NotebookKeyFixture() {

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
    // The frame's focus request is still settling when this returns - a RequestFocus on the `⋮`
    // issued now queues behind it and loses, so the button first has to wait on the transaction
    // landing, the same observable edge the Enter below then reads.
    assertFrameFocused()

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
   * `notebook:move-cursor-{up,down}` - `J`/`K` and the arrows on the focused frame move the head
   * (`actions.tsx:1099-1177`), and `focusActiveCell` moves the frame - command mode's focus
   * owner - with it.
   */
  @Test
  fun jAndK_andTheArrows_onTheFrame_moveTheHead() {
    showEditing(CELL, SECOND_CELL, THIRD_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { pressKey(Key.J) }
    compose.runOnIdle { assertEquals("c2", viewModel.uiState.value.selectedCellId) }
    assertFrameFocused()

    headFrame().performKeyInput { pressKey(Key.DirectionDown) }
    compose.runOnIdle { assertEquals("c3", viewModel.uiState.value.selectedCellId) }

    headFrame().performKeyInput { pressKey(Key.K) }
    compose.runOnIdle { assertEquals("c2", viewModel.uiState.value.selectedCellId) }
    headFrame().performKeyInput { pressKey(Key.DirectionUp) }
    compose.runOnIdle { assertEquals("c1", viewModel.uiState.value.selectedCellId) }
  }

  /**
   * `notebook:extend-marked-cells-below` - `Shift`+`J` marks a run from the keys, and the *second*
   * press extends it again. That is the case the focus owner has to survive: a marking started
   * from keys has no field attached, so the `selecting`-driven clearFocus - meant for a field's
   * caret - stands down for it.
   */
  @Test
  fun shiftJ_marksARunAndKeepsTheFrameForTheNextExtend() {
    showEditing(CELL, SECOND_CELL, THIRD_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.J) } }
    compose.runOnIdle { assertEquals(listOf("c1", "c2"), viewModel.uiState.value.selectedCellIds) }
    assertFrameFocused()

    headFrame().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.J) } }
    compose.runOnIdle { assertEquals(listOf("c1", "c2", "c3"), viewModel.uiState.value.selectedCellIds) }
    assertFrameFocused()
  }

  /**
   * `notebook:move-cursor-heading-{above-or-collapse,below-or-expand}` - `ArrowLeft` first jumps
   * to the heading of the section the head is in, then folds it under itself; `ArrowRight` opens
   * it again (`actions.tsx:1244-1300`).
   */
  @Test
  fun arrowLeft_jumpsThenCollapses_andArrowRightExpands() {
    val heading = Cell("h", CellKind.MARKDOWN, "# Chapter")
    showEditing(heading, CELL, SECOND_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    // Head on c1, a plain cell in h's section: ArrowLeft is the jump, not a fold.
    headFrame().performKeyInput { pressKey(Key.DirectionLeft) }
    compose.runOnIdle { assertEquals("h", viewModel.uiState.value.selectedCellId) }

    headFrame().performKeyInput { pressKey(Key.DirectionLeft) }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.hiddenCellIds == setOf("c1", "c2") }
    compose.onNodeWithText("2 cells hidden").assertIsDisplayed()
    compose.runOnIdle { assertEquals("h", viewModel.uiState.value.selectedCellId) }

    headFrame().performKeyInput { pressKey(Key.DirectionRight) }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.hiddenCellIds.isEmpty() }
    compose.runOnIdle { assertEquals("h", viewModel.uiState.value.selectedCellId) }
  }

  /**
   * The cursor's scan skips `isHidden` widgets (`actions.tsx:1128-1132`): `J` from the collapsed
   * heading walks over its whole section rather than stopping on a cell nobody can see.
   */
  @Test
  fun j_skipsACollapsedSection() {
    val collapsed = Cell("h", CellKind.MARKDOWN, "# Chapter", headingCollapsed = true)
    val next = Cell("h2", CellKind.MARKDOWN, "# Next")
    showEditing(collapsed, CELL, SECOND_CELL, next, THIRD_CELL)
    // The only composed field is c3's - c1/c2 are inside the collapsed section.
    field(0).performKeyInput { pressKey(Key.Escape) }

    // From c3 up to the heading, then down through it: h → h2, with c1/c2 walked over.
    headFrame().performKeyInput { pressKey(Key.DirectionUp) }
    compose.runOnIdle { assertEquals("h2", viewModel.uiState.value.selectedCellId) }
    headFrame().performKeyInput { pressKey(Key.DirectionUp) }
    compose.runOnIdle { assertEquals("h", viewModel.uiState.value.selectedCellId) }

    headFrame().performKeyInput { pressKey(Key.J) }
    compose.runOnIdle { assertEquals("h2", viewModel.uiState.value.selectedCellId) }
  }

  /**
   * `Shift`+`End` - `extend-marked-cells-bottom` - is the far jump the headMovesByKey effect
   * exists for: the head lands on a cell the list has not composed, so the scroll runs first and
   * only then can the frame's focus be re-asked.
   */
  @Test
  fun shiftEnd_marksToTheBottom_andScrollsTheHeadIntoView() {
    val many = (1..30).map { Cell("c$it", CellKind.CODE, "pass") }
    showEditing(*many.toTypedArray())
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.MoveEnd) } }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.selectedCellId == "c30" }
    // The scroll is what makes c30 composable at all - without it this fetch throws, since the
    // last cell is off-screen in a 30-cell notebook.
    compose.waitUntil(TIMEOUT_MILLIS) {
      runCatching { cellFrame("c30").fetchSemanticsNode() }.isSuccess
    }
    assertFrameFocused()
  }

  /**
   * The header's Run is a press on that cell: it selects before it runs - `_evtMouseDown`'s
   * `activeCellIndex = …` (`widget.ts:3160-3167`), which the header control alone did not do -
   * and the frame follows the head it moved to.
   */
  @Test
  fun aCellsRunButton_selectsItBeforeRunning() {
    showEditing(CELL, SECOND_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    compose.onAllNodesWithContentDescription("Run cell")[1].performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.selectedCellId == "c2" }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.executionCounts["c2"] == 1 }
    assertFrameFocused()
  }

  /**
   * Keyboard focus on the frame draws the `focusBorder` ring - the frame's `isFocused`, not the
   * container's `hasFocus`, because a `⋮` focused inside the same cell must not ring the cell.
   */
  @Test
  fun aKeyboardFocusOnTheFrame_drawsTheRing() {
    val probe = FocusRingEdgeProbe(compose)
    viewModel = notebookViewModel(FakeEditorSettingsRepository(), CELL)
    probe.show(darkTheme = false) { editorScreen(viewModel, FakeEditorSettingsRepository()) {} }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells.isNotEmpty() }
    compose.runOnIdle { viewModel.selection.activate("c1") }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.selectedCellId == "c1" }

    probe.assertKeyboardFocusDrawsTheRing(headFrame(), edgeOf = headFrame())
  }
}
