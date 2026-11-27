package com.jupydroid.app.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.EditorSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The command-mode letter set against a real [EditorScreen] - the rows `tracker.json`
 * (`:357-466`, `:1754-1781`) binds on `.jp-Cell` and `body` that [NotebookKeymapTest] can only
 * resolve as a table: that a letter claimed for the notebook never reaches the field's own text,
 * that the `D`,`D` chord's first key spends nothing, and that the head lands where the operation
 * leaves it.
 */
@OptIn(ExperimentalTestApi::class) // `performKeyInput`'s own opt-in requirement
@RunWith(AndroidJUnit4::class)
class NotebookLetterKeyTest : NotebookKeyFixture() {

  /**
   * `D`,`D` - `notebook:delete-cell` - is the table's only two-key chord (`tracker.json:357-362`).
   * The first `D` binds to nothing yet: it is `Pending`, claimed so the `D` never reaches the
   * focused node, and the second `D` is what runs the delete.
   */
  @Test
  fun d_d_deletesTheHead_andTheFirstDDeletesNothing() {
    showEditing(CELL, SECOND_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { pressKey(Key.D) }
    compose.runOnIdle { assertEquals(2, viewModel.uiState.value.cells.size) }

    headFrame().performKeyInput { pressKey(Key.D) }

    compose.runOnIdle {
      assertEquals(listOf("c2"), viewModel.uiState.value.cells.map { it.id })
      assertEquals("c2", viewModel.uiState.value.selectedCellId)
    }
    assertFrameFocused()
  }

  /**
   * A pending `D` followed by a key that is not `D`: `matchKeystroke` finds no `[D, B]` sequence,
   * falls back to `B` alone, and it is the insert that fires - the pending chord is spent
   * (`commands.ts:1509-1540`).
   */
  @Test
  fun d_thenB_spendsThePendingChordAndInserts() {
    showEditing(CELL, SECOND_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { pressKey(Key.D) }
    headFrame().performKeyInput { pressKey(Key.B) }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells.size == 3 }
    compose.runOnIdle {
      val cells = viewModel.uiState.value.cells
      assertEquals(listOf("c1", cells[1].id, "c2"), cells.map { it.id })
      assertEquals(cells[1].id, viewModel.uiState.value.selectedCellId)
    }
    assertFrameFocused()
  }

  /**
   * `B` - `insert-cell-below` - and its head landing: upstream's `insertBelow` selects the cell
   * it made (`actions.tsx:480-481`), so the frame - command mode's focus owner - follows it down.
   */
  @Test
  fun b_insertsBelow_andTheFrameFollowsTheNewHead() {
    showEditing(CELL, SECOND_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { pressKey(Key.B) }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells.size == 3 }
    compose.runOnIdle {
      assertEquals(viewModel.uiState.value.cells[1].id, viewModel.uiState.value.selectedCellId)
    }
    assertFrameFocused()
  }

  /**
   * `M` retypes the head to markdown - and only then does `1` mean `markdown-heading1`
   * (`tracker.json:573-577` keys the digits to `.jp-MarkdownCell`, which a code cell is not).
   * The two presses together are the selector fidelity, end to end.
   */
  @Test
  fun m_turnsTheHeadToMarkdown_andOneMakesItAHeading() {
    showEditing(CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { pressKey(Key.M) }
    compose.runOnIdle { assertEquals(CellKind.MARKDOWN, viewModel.uiState.value.cells[0].kind) }

    headFrame().performKeyInput { pressKey(Key.One) }
    compose.runOnIdle { assertTrue(viewModel.uiState.value.cells[0].source.startsWith("#")) }
  }

  /** `1` on a code head is not a chord - the markdown selector rules it out, so nothing moves. */
  @Test
  fun one_onACodeHead_isNotAChord() {
    showEditing(CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { pressKey(Key.One) }

    compose.runOnIdle {
      val cell = viewModel.uiState.value.cells[0]
      assertEquals(CellKind.CODE, cell.kind)
      assertEquals("pass", cell.source)
      assertEquals("c1", viewModel.uiState.value.selectedCellId)
    }
  }

  /** `Z` - `notebook:undo-cell-action` - puts back what `D`,`D` took. */
  @Test
  fun z_undoesTheDeletion() {
    showEditing(CELL, SECOND_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }
    headFrame().performKeyInput { pressKey(Key.D) }
    headFrame().performKeyInput { pressKey(Key.D) }

    headFrame().performKeyInput { pressKey(Key.Z) }

    compose.runOnIdle {
      assertEquals(listOf("c1", "c2"), viewModel.uiState.value.cells.map { it.id })
    }
  }

  /**
   * `X` then `V` - `cut-cell` / `paste-cell-below` - walks the head cell under its neighbour, and
   * the paste's landing is the *pasted* cell, not the one the paste stood on
   * (`actions.tsx`'s `addCells` selects the last inserted).
   */
  @Test
  fun x_thenV_movesTheHeadUnderTheNextCell() {
    showEditing(CELL, SECOND_CELL, THIRD_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { pressKey(Key.X) }
    compose.runOnIdle {
      assertEquals(listOf("c2", "c3"), viewModel.uiState.value.cells.map { it.id })
    }

    headFrame().performKeyInput { pressKey(Key.V) }
    compose.runOnIdle {
      assertEquals(listOf("c2", "c1", "c3"), viewModel.uiState.value.cells.map { it.id })
      assertEquals("c1", viewModel.uiState.value.selectedCellId)
    }
  }

  /** `Shift M` - `merge-cells` - folds the next cell into the head and keeps the head on it. */
  @Test
  fun shiftM_mergesTheNextCellIntoTheHead() {
    showEditing(CELL, SECOND_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.M) } }

    compose.runOnIdle {
      val state = viewModel.uiState.value
      assertEquals(1, state.cells.size)
      assertEquals(state.cells[0].id, state.selectedCellId)
    }
    assertFrameFocused()
  }

  /**
   * `Ctrl Shift -` - `split-cell-at-cursor` (`tracker.json:690-695`) - splits where the *field's*
   * caret is, which is the one thing the document cannot see. The caret lands at "one"'s end by
   * typing two lines then `ArrowUp` back to the first.
   */
  @Test
  fun ctrlShiftMinus_splitsAtTheFieldsCaret() {
    showEditing(CELL)
    field(0).performTextClearance()
    field(0).performTextInput("one")
    field(0).performKeyInput { pressKey(Key.Enter) }
    field(0).performTextInput("two")
    field(0).performKeyInput { pressKey(Key.DirectionUp) }

    field(0).performKeyInput {
      withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Minus) } }
    }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells.size == 2 }
    compose.runOnIdle {
      assertEquals(listOf("one", "two"), viewModel.uiState.value.cells.map { it.source })
    }
  }

  /** `Ctrl A` - `notebook:select-all` (`tracker.json:803-807`) - marks the whole notebook. */
  @Test
  fun ctrlA_marksEveryCell() {
    showEditing(CELL, SECOND_CELL, THIRD_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }

    compose.runOnIdle {
      assertEquals(listOf("c1", "c2", "c3"), viewModel.uiState.value.selectedCellIds)
    }
  }

  /**
   * `Ctrl`+`Enter` from command mode (`tracker.json:1754-1759`, `body`-selector rows T3 added
   * only to edit mode) - the head runs where it stands and the frame keeps the focus.
   */
  @Test
  fun ctrlEnter_onTheFrame_runsTheHeadWhereItStands() {
    showEditing(CELL, SECOND_CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.executionCounts["c1"] == 1 }
    assertFrameFocused()
    compose.runOnIdle {
      assertEquals(CellMode.COMMAND, viewModel.uiState.value.mode)
      assertEquals("c1", viewModel.uiState.value.selectedCellId)
    }
  }

  /**
   * `Shift L` - `viewmenu:line-numbering` (`tracker.json`'s `notebook` rows) - is a settings
   * write, so the proof is the gutter's room: the field's left edge is what moves, the same
   * observable [LineNumberGutterTest] measures.
   */
  @Test
  fun shiftL_givesTheFieldBackItsGutterRoom() {
    showEditing(CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }
    val withGutter = field(0).getUnclippedBoundsInRoot().left

    headFrame().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.L) } }

    compose.waitUntil(TIMEOUT_MILLIS) { field(0).getUnclippedBoundsInRoot().left < withGutter }
  }

  /**
   * `Ctrl S` - `docmanager:save` - is claimed **in the field**: the row's selector is `body`,
   * not `.jp-Cell`, so edit mode does not stand between the chord and the save - and no `s`
   * lands in the source (`tracker.json` docmanager rows bind without a mode).
   */
  @Test
  fun ctrlS_inTheField_typesNoS() {
    showEditing(CELL)

    field(0).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }

    compose.runOnIdle { assertEquals("pass", viewModel.uiState.value.cells[0].source) }
  }

  /**
   * `Ctrl Shift S` - `docmanager:save-as` - leaves the editor for the shell: the screen's
   * `onSaveAs` is the seam the dialog hangs from, and reaching it is what the chord is for.
   */
  @Test
  fun ctrlShiftS_asksTheShellForSaveAs() {
    var asked = 0
    showEditing(CELL, onSaveAs = { asked++ })
    field(0).performKeyInput { pressKey(Key.Escape) }

    headFrame().performKeyInput {
      withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.S) } }
    }

    compose.runOnIdle { assertEquals(1, asked) }
  }

  /**
   * `Alt`+`ArrowUp` - `access-previous-history-entry` - is the setting-gated command: with
   * `accessKernelHistory` on, it pours the last run over the field's own text.
   */
  @Test
  fun altUp_recallsTheLastRunIntoTheField() {
    showEditing(CELL, settings = EditorSettings(accessKernelHistory = true))
    field(0).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.executionCounts["c1"] == 1 }

    field(0).performTextClearance()
    field(0).performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionUp) } }

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.cells[0].source == "pass" }
  }
}
