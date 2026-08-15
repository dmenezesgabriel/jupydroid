package com.jupydroid.app.ui.editor

import androidx.compose.ui.text.TextRange
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

    val undone = state.applyingFrom("b", CellAction.Delete).undoneCellOperation()

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

    val deleted = state.applyingFrom("only", CellAction.Delete)
    assertEquals(1, deleted.cells.size)
    assertFalse(deleted.cells.single().id == "only")

    assertEquals(listOf("only"), deleted.undoneCellOperation().cells.map { it.id })
  }

  @Test
  fun insert_andInsertAbove_areTakenBackToTheCellsThatWereThere() {
    val state = stateOf("a", "b")

    val below = state.applyingFrom("a", CellAction.Insert(CellKind.CODE))
    val above = state.applyingFrom("a", CellAction.InsertAbove(CellKind.MARKDOWN))

    assertEquals(3, below.cells.size)
    assertEquals(listOf("a", "b"), below.undoneCellOperation().cells.map { it.id })
    assertEquals(listOf("a", "b"), above.undoneCellOperation().cells.map { it.id })
  }

  @Test
  fun duplicate_isTakenBackWithTheCopiedResults() {
    val state = stateOf("a").withOutputOn("a", "hello").withCount("a", 7)

    val undone = state.applyingFrom("a", CellAction.Duplicate).undoneCellOperation()

    assertEquals(listOf("a"), undone.cells.map { it.id })
    assertEquals(1, undone.outputs.size)
    assertEquals(1, undone.executionCounts.size)
  }

  /** `actions.spec.ts:1886` and `:1917` - both directions, and the order is what is asserted. */
  @Test
  fun move_returnsTheOrderInEitherDirection() {
    val state = stateOf("a", "b", "c")

    val up = state.applyingFrom("c", CellAction.Move(CellMove.UP))
    val down = state.applyingFrom("a", CellAction.Move(CellMove.DOWN))

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

    val merged = state.applyingFrom("a", CellAction.Merge(MergeInto.BELOW))
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

    val undone = state.applyingFrom("a", CellAction.ChangeKind(CellKind.MARKDOWN)).undoneCellOperation()

    assertEquals(CellKind.CODE, undone.cells.single().kind)
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "hello")), undone.outputs["a"])
    assertEquals(7, undone.executionCounts["a"])
  }

  @Test
  fun cut_thenPaste_areEachTakenBack() {
    val state = stateOf("a", "b")

    val cut = state.applyingFrom("a", CellAction.Cut)
    assertEquals(listOf("b"), cut.cells.map { it.id })
    assertEquals(listOf("a", "b"), cut.undoneCellOperation().cells.map { it.id })

    val pasted = cut.applyingFrom("b", CellAction.Paste)
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

    val undone = state.applyingFrom("a", CellAction.ClearOutputs).undoneCellOperation()

    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "hello")), undone.outputs["a"])
    assertEquals(7, undone.executionCounts["a"])
  }

  @Test
  fun collapsing_theSourceOrTheOutputs_isTakenBack() {
    val state = stateOf("a")

    val source = state.applyingFrom("a", CellAction.ToggleSourceHidden)
    val outputs = state.applyingFrom("a", CellAction.ToggleOutputsHidden)

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
    val copied = stateOf("a").applyingFrom("a", CellAction.Copy)

    assertTrue(copied.undoable.isEmpty())
    assertEquals(copied.cells, copied.undoneCellOperation().cells)
  }

  /** The same rule covers a menu tapped just after its cell went away - `CellEdits` no-ops. */
  @Test
  fun anActionOnACellThatIsGone_recordsNothing() {
    val state = stateOf("a")

    assertTrue(state.applyingFrom("gone", CellAction.Delete).undoable.isEmpty())
    assertTrue(state.applyingFrom("a", CellAction.Move(CellMove.UP)).undoable.isEmpty())
  }

  @Test
  fun redo_replaysWhatTheUndoTookBack() {
    val state = stateOf("a", "b")

    val redone = state.applyingFrom("b", CellAction.Delete).undoneCellOperation().redoneCellOperation()

    assertEquals(listOf("a"), redone.cells.map { it.id })
    assertTrue(redone.redoable.isEmpty())
    assertFalse(redone.undoable.isEmpty())
  }

  @Test
  fun anOperationAfterAnUndo_clearsWhatCouldHaveBeenRedone() {
    val undone = stateOf("a", "b").applyingFrom("b", CellAction.Delete).undoneCellOperation()
    assertFalse(undone.redoable.isEmpty())

    assertTrue(undone.applyingFrom("a", CellAction.Insert(CellKind.CODE)).redoable.isEmpty())
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
      .applyingFrom("b", CellAction.Delete)
      .withSource("a", "forty lines")

    val undone = typed.undoneCellOperation()

    assertEquals("forty lines", undone.cells.first { it.id == "a" }.source)
    assertEquals("source of b", undone.cells.first { it.id == "b" }.source)
  }

  /**
   * **Split is the first operation that rewrites a cell that stays**, and it is why the rule above
   * needed one exception rather than a waiver. Applied literally, an undo would take the new
   * fragment away and leave the original holding only the half below the caret - the text above it
   * destroyed, with nothing left to undo. So the record says which sources the operation *itself*
   * rewrote and supplies those, which is what the reference's Yjs transaction does by recording
   * the delta rather than the document.
   */
  @Test
  fun split_isTakenBackWithTheTextItMovedIntoTheNewCell() {
    val split = stateOf("a", "b").applyingFrom("a", CellAction.Split, editing("one\ntwo", 4))
    assertEquals(listOf("one", "two"), split.cells.take(2).map { it.source })

    val undone = split.undoneCellOperation()

    assertEquals(listOf("a", "b"), undone.cells.map { it.id })
    assertEquals("one\ntwo", undone.cells.first().source)
  }

  /**
   * The **editor's** text is what gets cut, and the model's copy is allowed to be behind it: an
   * edit is reported on a 150ms debounce (`CodeCellField.EDIT_REPORT_MILLIS`), so the tap that
   * opens the cell menu can beat it. `applying` brings the cell up to the editor first, which is
   * also what puts the right text on the undo record - the case that caught this.
   */
  @Test
  fun split_cutsWhatTheEditorHolds_notTheModelsCopyFromOneDebounceAgo() {
    val split = stateOf("a").applyingFrom("a", CellAction.Split, editing("typed\nahead", 6))

    assertEquals(listOf("typed", "ahead"), split.cells.map { it.source })
    assertEquals("typed\nahead", split.undoneCellOperation().cells.single().source)
  }

  /** P2a's rule survives the exception: only the cells the *operation* rewrote are restored. */
  @Test
  fun split_undo_stillKeepsTypingDoneInAnotherCellAfterwards() {
    val typed = stateOf("a", "b")
      .applyingFrom("a", CellAction.Split, editing("one\ntwo", 4))
      .withSource("b", "forty lines")

    val undone = typed.undoneCellOperation()

    assertEquals("forty lines", undone.cells.first { it.id == "b" }.source)
    assertEquals("one\ntwo", undone.cells.first { it.id == "a" }.source)
  }

  @Test
  fun split_redo_cutsItAgainAtTheSamePlace() {
    val redone = stateOf("a")
      .applyingFrom("a", CellAction.Split, editing("one\ntwo", 4))
      .undoneCellOperation()
      .redoneCellOperation()

    assertEquals(listOf("one", "two"), redone.cells.map { it.source })
  }

  // --- the field's revision -------------------------------------------------------------

  /**
   * A `TextFieldState` on screen holds its own copy of the text, so a cell operation that rewrites
   * a surviving cell has to say so - the field cannot tell an external rewrite from the debounced
   * echo of its own typing. The counter is bumped from the same before/after diff the record uses,
   * so a later operation that rewrites a source gets this for nothing.
   */
  @Test
  fun split_bumpsTheRevisionOfTheCellItRewroteAndNoOther() {
    val split = stateOf("a", "b").applyingFrom("a", CellAction.Split, editing("one\ntwo", 4))

    assertEquals(1, split.sourceRevisions["a"])
    assertNull(split.sourceRevisions["b"])
  }

  /** Undo rewrites it back, so the field has to be told a second time. */
  @Test
  fun undoingASplitBumpsTheRevisionAgain() {
    val undone = stateOf("a")
      .applyingFrom("a", CellAction.Split, editing("one\ntwo", 4))
      .undoneCellOperation()

    assertEquals(2, undone.sourceRevisions["a"])
  }

  /**
   * Typing must never bump one: the field *is* the source of that change, and re-seeding it from
   * the model would revert whatever was typed during the 150ms the report was in flight.
   */
  @Test
  fun typingBumpsNoRevision() {
    val typed = stateOf("a").withSource("a", "typed")

    assertTrue(typed.sourceRevisions.isEmpty())
  }

  /** An operation that moves cells around without rewriting one leaves every field alone. */
  @Test
  fun anOperationThatRewritesNoSourceBumpsNothing() {
    val state = stateOf("a", "b")

    assertTrue(state.applyingFrom("b", CellAction.Move(CellMove.UP)).sourceRevisions.isEmpty())
    assertTrue(state.applyingFrom("b", CellAction.Delete).sourceRevisions.isEmpty())
    assertTrue(state.applyingFrom("a", CellAction.Duplicate).sourceRevisions.isEmpty())
  }

  /** A selection naming a cell the undo removed is worse than none - `withSelectionClamped`. */
  @Test
  fun undo_dropsASelectionTheRestoreLeftPointingAtNothing() {
    val inserted = stateOf("a").applyingFrom("a", CellAction.Insert(CellKind.CODE))
    val selected = inserted.withCellSelected(inserted.cells.last().id).withMode(CellMode.EDIT)

    val undone = selected.undoneCellOperation()

    assertNull(undone.selectedCellId)
    assertEquals(CellMode.COMMAND, undone.mode)
  }

  /** Every operation is one entry, so three of them take three undos to unwind. */
  @Test
  fun eachOperationIsOneEntry() {
    val state = stateOf("a")
      .applyingFrom("a", CellAction.Insert(CellKind.CODE))
      .applyingFrom("a", CellAction.Insert(CellKind.CODE))
      .applyingFrom("a", CellAction.Insert(CellKind.CODE))

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

  /** What the cell's own field is holding, which is the only thing [CellAction.Split] reads. */
  private fun editing(source: String, caret: Int) = CellEditorText(source, TextRange(caret))
}
