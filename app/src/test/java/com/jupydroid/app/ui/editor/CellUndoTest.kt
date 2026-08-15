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
 * Taking a cell operation back - `notebook:undo-cell-action` and `notebook:redo-cell-action`.
 *
 * **Which operations are undoable is the reference's own test suite**, not a guess:
 * `packages/notebook/test/actions.spec.ts` has an `it('should be undo-able')` on delete (`:693`),
 * delete-them-all (`:706`), insertAbove (`:943`), insertBelow (`:997`), changeCellType (`:1059`),
 * moveUp (`:1886`), moveDown (`:1917`), merge (`:543`), cut (`:2165`) and paste (`:2260`). Each
 * case below names the one it mirrors.
 */
class CellUndoTest {
  @Test
  fun delete_comesBackWithItsOutputsAndCountWhereItWas() {
    val state = stateOf("a", "b", "c").withOutputOn("b", "hello").withCount("b", 7)

    val undone = state.applying(CellAction.Delete, "b").undoneCellOperation()

    assertEquals(listOf("a", "b", "c"), undone.cells.map { it.id })
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "hello")), undone.outputs["b"])
    assertEquals(7, undone.executionCounts["b"])
  }

  /**
   * `actions.spec.ts:706`, *"should be undo-able if all the cells are deleted"*. Deleting the last
   * cell leaves an empty replacement, and the replacement is created *inside* the same transaction
   * (`actions.tsx:3391`) precisely so one undo takes it away again.
   */
  @Test
  fun delete_ofTheLastCell_takesTheReplacementAwayAgain() {
    val state = stateOf("only")

    val deleted = state.applying(CellAction.Delete, "only")
    assertEquals(1, deleted.cells.size)
    assertFalse(deleted.cells.single().id == "only")

    assertEquals(listOf("only"), deleted.undoneCellOperation().cells.map { it.id })
  }

  @Test
  fun insert_andInsertAbove_areTakenBackToTheCellsThatWereThere() {
    val state = stateOf("a", "b")

    val below = state.applying(CellAction.Insert(CellKind.CODE), "a")
    val above = state.applying(CellAction.InsertAbove(CellKind.MARKDOWN), "a")

    assertEquals(3, below.cells.size)
    assertEquals(listOf("a", "b"), below.undoneCellOperation().cells.map { it.id })
    assertEquals(listOf("a", "b"), above.undoneCellOperation().cells.map { it.id })
  }

  @Test
  fun duplicate_isTakenBackWithTheCopiedResults() {
    val state = stateOf("a").withOutputOn("a", "hello").withCount("a", 7)

    val undone = state.applying(CellAction.Duplicate, "a").undoneCellOperation()

    assertEquals(listOf("a"), undone.cells.map { it.id })
    assertEquals(1, undone.outputs.size)
    assertEquals(1, undone.executionCounts.size)
  }

  /** `actions.spec.ts:1886` and `:1917` - both directions, and the order is what is asserted. */
  @Test
  fun move_returnsTheOrderInEitherDirection() {
    val state = stateOf("a", "b", "c")

    val up = state.applying(CellAction.Move(CellMove.UP), "c")
    val down = state.applying(CellAction.Move(CellMove.DOWN), "a")

    assertEquals(listOf("a", "b", "c"), up.undoneCellOperation().cells.map { it.id })
    assertEquals(listOf("a", "b", "c"), down.undoneCellOperation().cells.map { it.id })
  }

  /**
   * `actions.spec.ts:543`. A merge takes two cells, their ids and their outputs; the undo brings
   * all three back, which a merged cell carrying a *new* id could not do by itself.
   */
  @Test
  fun merge_bringsBackBothCellsWithTheirIdsAndOutputs() {
    val state = stateOf("a", "b").withOutputOn("a", "from a").withOutputOn("b", "from b")

    val merged = state.applying(CellAction.Merge(MergeInto.BELOW), "a")
    assertEquals(1, merged.cells.size)
    assertTrue(merged.outputs.isEmpty())

    val undone = merged.undoneCellOperation()
    assertEquals(listOf("a", "b"), undone.cells.map { it.id })
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "from a")), undone.outputs["a"])
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "from b")), undone.outputs["b"])
  }

  /**
   * `actions.spec.ts:1059` asserts the *type* comes back. The outputs have to come with it:
   * `changeCellType` drops them (*"After change of type outputs are deleted"*), so an undo that
   * restored only the type would leave a code cell claiming a run whose results it had lost.
   */
  @Test
  fun changeKind_restoresTheTypeAndTheOutputsItDropped() {
    val state = stateOf("a").withOutputOn("a", "hello").withCount("a", 7)

    val undone = state.applying(CellAction.ChangeKind(CellKind.MARKDOWN), "a").undoneCellOperation()

    assertEquals(CellKind.CODE, undone.cells.single().kind)
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "hello")), undone.outputs["a"])
    assertEquals(7, undone.executionCounts["a"])
  }

  @Test
  fun cut_thenPaste_areEachTakenBack() {
    val state = stateOf("a", "b")

    val cut = state.applying(CellAction.Cut, "a")
    assertEquals(listOf("b"), cut.cells.map { it.id })
    assertEquals(listOf("a", "b"), cut.undoneCellOperation().cells.map { it.id })

    val pasted = cut.applying(CellAction.Paste, "b")
    assertEquals(2, pasted.cells.size)
    assertEquals(listOf("b"), pasted.undoneCellOperation().cells.map { it.id })
  }

  /**
   * **The adaptation.** In the reference an undo after Clear Outputs restores the `execution`
   * metadata and nothing visible, because outputs sit outside the undo manager's tracked scope
   * (`actions.tsx`, `Private.restoreExecution`). Here the entry restores what was cleared - see
   * `docs/DECISIONS.md`, and the reason is that on a phone this row sits one above Delete.
   */
  @Test
  fun clearOutputs_isTakenBackWithTheResultsItThrewAway() {
    val state = stateOf("a").withOutputOn("a", "hello").withCount("a", 7)

    val undone = state.applying(CellAction.ClearOutputs, "a").undoneCellOperation()

    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "hello")), undone.outputs["a"])
    assertEquals(7, undone.executionCounts["a"])
  }

  @Test
  fun collapsing_theSourceOrTheOutputs_isTakenBack() {
    val state = stateOf("a")

    val source = state.applying(CellAction.ToggleSourceHidden, "a")
    val outputs = state.applying(CellAction.ToggleOutputsHidden, "a")

    assertTrue(source.cells.single().sourceHidden)
    assertFalse(source.undoneCellOperation().cells.single().sourceHidden)
    assertTrue(outputs.cells.single().outputsHidden)
    assertFalse(outputs.undoneCellOperation().cells.single().outputsHidden)
  }

  /**
   * A transaction that changes nothing produces no stack item, which is Yjs's rule and needs no
   * per-action table here: Copy only fills the clipboard, so there is nothing to take back.
   */
  @Test
  fun copy_recordsNothing_becauseItChangesNoCell() {
    val copied = stateOf("a").applying(CellAction.Copy, "a")

    assertTrue(copied.undoable.isEmpty())
    assertEquals(copied.cells, copied.undoneCellOperation().cells)
  }

  /** The same rule covers a menu tapped just after its cell went away - `CellEdits` no-ops. */
  @Test
  fun anActionOnACellThatIsGone_recordsNothing() {
    val state = stateOf("a")

    assertTrue(state.applying(CellAction.Delete, "gone").undoable.isEmpty())
    assertTrue(state.applying(CellAction.Move(CellMove.UP), "a").undoable.isEmpty())
  }

  @Test
  fun redo_replaysWhatTheUndoTookBack() {
    val state = stateOf("a", "b")

    val redone = state.applying(CellAction.Delete, "b").undoneCellOperation().redoneCellOperation()

    assertEquals(listOf("a"), redone.cells.map { it.id })
    assertTrue(redone.redoable.isEmpty())
    assertFalse(redone.undoable.isEmpty())
  }

  @Test
  fun anOperationAfterAnUndo_clearsWhatCouldHaveBeenRedone() {
    val undone = stateOf("a", "b").applying(CellAction.Delete, "b").undoneCellOperation()
    assertFalse(undone.redoable.isEmpty())

    assertTrue(undone.applying(CellAction.Insert(CellKind.CODE), "a").redoable.isEmpty())
  }

  @Test
  fun undoAndRedo_onAnEmptyStack_changeNothing() {
    val state = stateOf("a", "b")

    assertEquals(state, state.undoneCellOperation())
    assertEquals(state, state.redoneCellOperation())
  }

  /**
   * **The rule that keeps the two undos apart.** JupyterLab has two commands because text history
   * and cell history are different features - `notebook:undo` runs `cell.editor?.undo()`
   * (`packages/notebook-extension/src/index.ts:4530-4541`) and never touches the document's stack.
   * So a cell that is still in the notebook keeps the source it has *now*; the record supplies a
   * source only for cells it is bringing back. Without this, deleting a cell and then typing forty
   * lines into another one would lose the forty lines on undo.
   */
  @Test
  fun undo_neverRewritesTheSourceOfACellThatIsStillThere() {
    val typed = stateOf("a", "b")
      .applying(CellAction.Delete, "b")
      .withSource("a", "forty lines")

    val undone = typed.undoneCellOperation()

    assertEquals("forty lines", undone.cells.first { it.id == "a" }.source)
    assertEquals("source of b", undone.cells.first { it.id == "b" }.source)
  }

  /** A selection naming a cell the undo removed is worse than none - `withSelectionClamped`. */
  @Test
  fun undo_dropsASelectionTheRestoreLeftPointingAtNothing() {
    val inserted = stateOf("a").applying(CellAction.Insert(CellKind.CODE), "a")
    val selected = inserted.withCellSelected(inserted.cells.last().id).withMode(CellMode.EDIT)

    val undone = selected.undoneCellOperation()

    assertNull(undone.selectedCellId)
    assertEquals(CellMode.COMMAND, undone.mode)
  }

  /** Every operation is one entry, so three of them take three undos to unwind. */
  @Test
  fun eachOperationIsOneEntry() {
    val state = stateOf("a")
      .applying(CellAction.Insert(CellKind.CODE), "a")
      .applying(CellAction.Insert(CellKind.CODE), "a")
      .applying(CellAction.Insert(CellKind.CODE), "a")

    assertEquals(4, state.cells.size)
    assertEquals(3, state.undoable.size)
    assertEquals(1, state.undoneCellOperation().undoneCellOperation().undoneCellOperation().cells.size)
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
