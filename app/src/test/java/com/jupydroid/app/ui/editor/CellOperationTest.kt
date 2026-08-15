package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a cell operation applies to, which since P2c is the marked run rather than one cell.
 *
 * The transforms themselves are `CellEditsTest`, `CellRestructureTest` and `CellBatchEditsTest`;
 * this is only about the operand, the head, and what the run looks like afterwards.
 */
class CellOperationTest {
  @Test
  fun anOperationAppliesToTheWholeMarkedRun() {
    val state = markedRun("a", "b").applying(CellAction.Delete)

    assertEquals(listOf("c"), state.cells.map { it.id })
  }

  /**
   * One entry, not one per cell. [EditorUiState.applying] is the undoable unit, which is the same
   * boundary as the reference's `sharedModel.transact { }` - deleting three cells and pressing
   * undo once has to bring all three back.
   */
  @Test
  fun aBatchIsOneEntryOnTheUndoStack() {
    val deleted = markedRun("a", "b").applying(CellAction.Delete)

    assertEquals(1, deleted.undoable.size)
    assertEquals(listOf("a", "b", "c"), deleted.undoneCellOperation().cells.map { it.id })
  }

  /**
   * **Three operations keep the run and every other one clears it**, and that division is the
   * reference's rather than a rule of thumb: `move` (`actions.tsx:586-610`), `clearOutputs`
   * (`:1822-1840`) and the collapses (`:1877-2110`) are the ones with no `deselectAll()` in them,
   * because after each the same cells are still there and still worth acting on together.
   */
  @Test
  fun movingAndFoldingKeepTheRun() {
    val run = markedRun("b", "c")

    assertTrue(run.applying(CellAction.Move(CellMove.UP)).selecting)
    assertTrue(run.applying(CellAction.ClearOutputs).selecting)
    assertTrue(run.applying(CellAction.ToggleSourceHidden).selecting)
    assertTrue(run.applying(CellAction.ToggleOutputsHidden).selecting)
  }

  @Test
  fun everythingElseClearsTheRun() {
    val run = markedRun("a", "b")

    assertFalse(run.applying(CellAction.Delete).selecting)
    assertFalse(run.applying(CellAction.Copy).selecting)
    assertFalse(run.applying(CellAction.Cut).selecting)
    assertFalse(run.applying(CellAction.Duplicate).selecting)
    assertFalse(run.applying(CellAction.Merge(MergeInto.ABOVE)).selecting)
    assertFalse(run.applying(CellAction.ChangeKind(CellKind.MARKDOWN)).selecting)
    assertFalse(run.applying(CellAction.Insert(CellKind.CODE)).selecting)
    assertFalse(run.applying(CellAction.Split).selecting)
  }

  /** `NotebookActions.undo` ends on `deselectAll()`; ours has to as well, or the bar outlives it. */
  @Test
  fun undoClearsTheRunToo() {
    val moved = markedRun("a", "b").applying(CellAction.Move(CellMove.DOWN))

    assertFalse(moved.undoneCellOperation().selecting)
  }

  /** The head is where a head-only operation lands - insert below goes under it, not under the run. */
  @Test
  fun aHeadOnlyOperationUsesTheHeadAndNotTheRun() {
    val run = markedRun("a", "b")

    val inserted = run.applying(CellAction.Insert(CellKind.CODE))

    assertEquals("b", run.selectedCellId)
    assertEquals(3, inserted.cells.indexOfFirst { it.id == "c" })
  }

  /**
   * A menu opened on a cell outside the run means **that** cell: the reference sets
   * `activeCellIndex` for a press anywhere in a cell and deselects when the press is outside the
   * selection (`packages/notebook/src/widget.ts:3160-3167`). Reachable here only through a cell's
   * TalkBack context menu, because the `⋮` is hidden while a run is marked.
   */
  @Test
  fun aMenuOnACellOutsideTheRunActsOnThatCellAlone() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(state, NotebookSaveDebounce(TestScope(testScheduler)) {})

    edits.applyTo("c", CellAction.Delete)

    assertEquals(listOf("a", "b"), state.value.cells.map { it.id })
    assertFalse(state.value.selecting)
  }

  /**
   * **A stale id is a no-op, which the operand rule nearly broke.** `CellEdits` has promised since
   * 4b that a menu outliving the cell it was opened on does nothing, and the first version of
   * [EditorUiState.applyingFrom] collapsed the run *before* discovering the cell was gone - leaving
   * the old head selected and deleting **that** cell instead. Found by re-reading the diff rather
   * than by a failure, which is why the case is written down here.
   */
  @Test
  fun aMenuOnACellThatHasGoneDoesNothingAtAll() {
    val run = markedRun("a", "b")

    assertEquals(run, run.applyingFrom("gone", CellAction.Delete))
  }

  @Test
  fun aMenuOnACellInsideTheRunActsOnTheWholeRun() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(state, NotebookSaveDebounce(TestScope(testScheduler)) {})

    edits.applyTo("a", CellAction.Delete)

    assertEquals(listOf("c"), state.value.cells.map { it.id })
  }

  /** Three cells, with [marked] the run and its head on the last one named. */
  private fun markedRun(vararg marked: String) = EditorUiState(
    cells = listOf("a", "b", "c").map { Cell(it, CellKind.CODE, "source of $it") },
    loaded = true,
  ).withSelectionStartedAt(marked.first()).withSelectionExtendedTo(marked.last())
}
