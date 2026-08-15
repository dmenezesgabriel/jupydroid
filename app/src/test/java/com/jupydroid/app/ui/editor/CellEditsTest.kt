package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Semantics follow JupyterLab's `NotebookActions`, read rather than assumed - three of them are
 * not what a first guess would produce, and each is called out on the test that covers it.
 */
class CellEditsTest {
  @Test
  fun insert_putsAnEmptyCellAfterTheOneChosen() {
    val state = stateOf("a", "b")

    val cells = state.withCellInserted("a", CellKind.CODE, newId = "new").cells

    assertEquals(listOf("a", "new", "b"), cells.map { it.id })
    assertEquals("", cells[1].source)
    assertEquals(CellKind.CODE, cells[1].kind)
  }

  @Test
  fun insert_canAddAMarkdownCell() {
    val cells = stateOf("a").withCellInserted("a", CellKind.MARKDOWN, newId = "new").cells

    assertEquals(CellKind.MARKDOWN, cells[1].kind)
  }

  @Test
  fun move_swapsWithTheNeighbourInEachDirection() {
    val state = stateOf("a", "b", "c")

    assertEquals(listOf("a", "c", "b"), state.withCellMoved("c", CellMove.UP).cells.map { it.id })
    assertEquals(listOf("b", "a", "c"), state.withCellMoved("a", CellMove.DOWN).cells.map { it.id })
  }

  @Test
  fun move_doesNothingAtEitherEnd() {
    val state = stateOf("a", "b")

    assertEquals(state.cells, state.withCellMoved("a", CellMove.UP).cells)
    assertEquals(state.cells, state.withCellMoved("b", CellMove.DOWN).cells)
  }

  /**
   * JupyterLab duplicates the cell's nbformat JSON and only clears its id
   * (`packages/notebook/src/actions.tsx`, `addCells`), so the copy keeps what the original
   * had run - outputs and execution count included.
   */
  @Test
  fun duplicate_copiesTheResultsTooAndOnlyTheIdIsNew() {
    val state = stateOf("a").withOutputOn("a", "hello").withCount("a", 7)

    val duplicated = state.withCellDuplicated("a", newId = "copy")

    assertEquals(listOf("a", "copy"), duplicated.cells.map { it.id })
    assertEquals(state.outputs["a"], duplicated.outputs["copy"])
    assertEquals(7, duplicated.executionCounts["copy"])
  }

  @Test
  fun duplicate_keepsTheSourceAndKind() {
    val state = EditorUiState(cells = listOf(Cell("a", CellKind.MARKDOWN, "# title")), loaded = true)

    val copy = state.withCellDuplicated("a", newId = "copy").cells[1]

    assertEquals("# title", copy.source)
    assertEquals(CellKind.MARKDOWN, copy.kind)
  }

  @Test
  fun delete_removesTheCellAndTheResultsItLeftBehind() {
    val state = stateOf("a", "b").withOutputOn("a", "gone").withCount("a", 3)

    val deleted = state.withCellDeleted("a", replacementId = "unused")

    assertEquals(listOf("b"), deleted.cells.map { it.id })
    assertNull(deleted.outputs["a"])
    assertNull(deleted.executionCounts["a"])
  }

  /**
   * JupyterLab's `deleteCells` inserts a default cell when the notebook would be emptied, so a
   * notebook is never left with nothing to type into.
   */
  @Test
  fun delete_leavesAnEmptyCodeCellRatherThanAnEmptyNotebook() {
    val deleted = stateOf("only").withCellDeleted("only", replacementId = "fresh")

    assertEquals(listOf("fresh"), deleted.cells.map { it.id })
    assertEquals(CellKind.CODE, deleted.cells.single().kind)
    assertEquals("", deleted.cells.single().source)
  }

  /** Otherwise the spinner's cell is gone and Run All stays disabled for ever. */
  @Test
  fun delete_stopsTheNotebookLookingBusyWhenTheRunningCellGoes() {
    val state = stateOf("a", "b").startingRun("a", at = java.time.Instant.EPOCH)

    assertNull(state.withCellDeleted("a", replacementId = "x").runningCellId)
    assertEquals("a", state.withCellDeleted("b", replacementId = "x").runningCellId)
  }

  /**
   * JupyterLab's `clearOutputs` calls `clearExecution`, which sets `executionCount = null` as
   * well as emptying the outputs (`packages/cells/src/model.ts:706`). Leaving `[7]` beside a
   * cell with nothing under it would claim a result that is no longer there.
   */
  @Test
  fun clearOutputs_dropsTheExecutionCountWithThem() {
    val state = stateOf("a").withOutputOn("a", "old").withCount("a", 7)

    val cleared = state.withOutputsCleared("a")

    assertTrue(cleared.outputs["a"].isNullOrEmpty())
    assertNull(cleared.executionCounts["a"])
  }

  /**
   * A toggle rather than separate hide and show, which is what a one-cell menu wants -
   * JupyterLab has both pairs and a `toggleOutput` for exactly the same reason
   * (`packages/notebook/src/actions.tsx:1998`).
   */
  @Test
  fun toggleSourceHidden_hidesAnExpandedCellAndShowsAHiddenOne() {
    val hidden = stateOf("a", "b").withSourceHiddenToggled("a")

    assertTrue(hidden.cells.single { it.id == "a" }.sourceHidden)
    assertFalse(hidden.withSourceHiddenToggled("a").cells.single { it.id == "a" }.sourceHidden)
  }

  @Test
  fun toggleSourceHidden_leavesEveryOtherCellAlone() {
    val toggled = stateOf("a", "b").withSourceHiddenToggled("a")

    assertFalse(toggled.cells.single { it.id == "b" }.sourceHidden)
  }

  /** A menu outlives the cell it was opened on; a stale id is a no-op, as everywhere else here. */
  @Test
  fun toggleSourceHidden_ignoresACellThatIsGone() {
    val state = stateOf("a")

    assertEquals(state, state.withSourceHiddenToggled("gone"))
  }

  /** The two collapses are independent: JupyterLab has a command for each, not one for both. */
  @Test
  fun toggleOutputsHidden_hidesTheOutputsWithoutTouchingTheSource() {
    val hidden = stateOf("a").withOutputsHiddenToggled("a")

    assertTrue(hidden.cells.single().outputsHidden)
    assertFalse(hidden.cells.single().sourceHidden)
    assertFalse(hidden.withOutputsHiddenToggled("a").cells.single().outputsHidden)
  }

  /**
   * Collapsing outputs hides them; it does not throw them away. That is the difference from
   * Clear outputs, and the reason both are in the menu.
   */
  @Test
  fun toggleOutputsHidden_keepsTheOutputsAndTheExecutionCount() {
    val state = stateOf("a").withOutputOn("a", "kept").withCount("a", 7)

    val hidden = state.withOutputsHiddenToggled("a")

    assertEquals(state.outputs, hidden.outputs)
    assertEquals(7, hidden.executionCounts["a"])
  }

  @Test
  fun toggleOutputsHidden_ignoresACellThatIsGone() {
    val state = stateOf("a")

    assertEquals(state, state.withOutputsHiddenToggled("gone"))
  }

  /**
   * Selecting is not editing, so it goes through [applying] like every other menu item and comes
   * out the far side with no undo entry - `changesNothingFrom` compares cells, outputs and counts,
   * and this touches none of them. `Copy` has always been the same shape.
   *
   * It is on [CellAction] rather than beside it because that is what puts it on all three surfaces
   * the menu is built for - the dropdown, the sheet, and the cell's TalkBack context menu.
   */
  @Test
  fun startSelecting_marksTheCellAndIsNotSomethingToUndo() {
    val state = stateOf("a", "b").applying(CellAction.StartSelecting, "b")

    assertEquals(listOf("b"), state.selectedCellIds)
    assertTrue(state.selecting)
    assertEquals(emptyList<NotebookRevision>(), state.undoable)
    assertFalse(state.dirty)
  }

  @Test
  fun everyEditMarksTheNotebookDirty() {
    val state = stateOf("a", "b")

    assertTrue(state.withCellInserted("a", CellKind.CODE, "n").dirty)
    assertTrue(state.withCellMoved("a", CellMove.DOWN).dirty)
    assertTrue(state.withCellDuplicated("a", "n").dirty)
    assertTrue(state.withCellDeleted("a", "n").dirty)
    assertTrue(state.withOutputsCleared("a").dirty)
  }

  /** A menu can outlive the cell it was opened on; none of these may throw on a stale id. */
  @Test
  fun anUnknownCellIdChangesNothing() {
    val state = stateOf("a", "b")

    assertEquals(state, state.withCellInserted("gone", CellKind.CODE, "n"))
    assertEquals(state, state.withCellMoved("gone", CellMove.UP))
    assertEquals(state, state.withCellDuplicated("gone", "n"))
    assertEquals(state, state.withCellDeleted("gone", "n"))
    assertEquals(state, state.withOutputsCleared("gone"))
  }

  private fun stateOf(vararg ids: String) = EditorUiState(
    cells = ids.map { Cell(it, CellKind.CODE, "source of $it") },
    loaded = true,
  )

  private fun EditorUiState.withOutputOn(cellId: String, text: String) =
    copy(outputs = outputs + (cellId to listOf(CellOutput.Stream(StreamName.STDOUT, text))))

  private fun EditorUiState.withCount(cellId: String, count: Int) =
    copy(executionCounts = executionCounts + (cellId to count))
}
