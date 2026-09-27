package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.EditorSettings
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

  /**
   * **The hover ask is not an edit** - P5e-3. The surface that raised it answers it (the popup
   * lives beside the caret it asked about, which is [EditorCell]'s scope), so a row that ever
   * reached the operation must change nothing and record no undo entry - `ChooseHeadingLevel`'s
   * own guard, which `applying` keeps for exactly this reachability.
   */
  @Test
  fun symbolInfo_changesNothingAndRecordsNoUndo() {
    val run = markedRun("a", "b")
    val asked = run.applying(CellAction.SymbolInfo)

    assertEquals(run.cells, asked.cells)
    assertEquals(0, asked.undoable.size)
    assertTrue("asking keeps the run, the way the picker's own ask does", asked.selecting)
  }

  /**
   * **The definition ask is not an edit either** - P5e-5. It reaches the language server through
   * [EditorViewModel.cellAction], which never dispatches it here, but a row that ever arrived by
   * another route must change nothing and record no undo entry - [CellAction.SymbolInfo]'s own
   * guard, kept for the same reachability.
   */
  @Test
  fun goToDefinition_changesNothingAndRecordsNoUndo() {
    val run = markedRun("a", "b")
    val asked = run.applying(CellAction.GoToDefinition)

    assertEquals(run.cells, asked.cells)
    assertEquals(0, asked.undoable.size)
    assertTrue("asking keeps the run, the way the hover ask does", asked.selecting)
  }

  /**
   * **The format ask is not an [applying] edit either** - P5e-6. It reaches the language server
   * through [EditorViewModel.cellAction] and lands through [NotebookCellEdits.cellFormatted],
   * so a row arriving here by any other route has nothing to change -
   * [CellAction.GoToDefinition]'s own guard, kept for the same reachability.
   */
  @Test
  fun formatCell_changesNothingAndRecordsNoUndo() {
    val run = markedRun("a", "b")
    val asked = run.applying(CellAction.FormatCell)

    assertEquals(run.cells, asked.cells)
    assertEquals(0, asked.undoable.size)
    assertTrue("asking keeps the run, the way the jump does", asked.selecting)
  }

  /**
   * **The rename ask is not an [applying] edit either** - P5e-7. The row is answered by the
   * surface (its dialog needs the caret), and the dialog's `RenameTo` reaches the language
   * server through [EditorViewModel.cellAction], so neither arriving here may change a cell -
   * [CellAction.FormatCell]'s own guard, kept for the same reachability.
   */
  @Test
  fun renameAsk_changesNothingAndRecordsNoUndo() {
    val run = markedRun("a", "b")

    val row = run.applying(CellAction.RenameSymbol)
    val confirmed = run.applying(CellAction.RenameTo("summed"))

    assertEquals(run.cells, row.cells)
    assertEquals(run.cells, confirmed.cells)
    assertEquals(0, row.undoable.size + confirmed.undoable.size)
    assertTrue("asking keeps the run, the way the format ask does", confirmed.selecting)
  }

  /**
   * The ask's apply half - P5e-7, `editor.action.rename`: every cell's new source lands as
   * **one undoable operation** - the `bulkEditService.apply` of the reference, a single
   * `transact` of the document - and the [EditorUiState.sourceRevisions] bump marks each
   * rewritten cell's field to adopt it.
   */
  @Test
  fun cellsRenamed_rewritesEveryTouchedCellAsOneUndo() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellsRenamed(
      "a",
      mapOf("a" to "def summed():\n    pass", "b" to "summed()"),
      askedSources = mapOf("a" to "source of a", "b" to "source of b", "c" to "source of c"),
    )

    val after = state.value
    assertEquals("def summed():\n    pass", after.cells.first { it.id == "a" }.source)
    assertEquals("summed()", after.cells.first { it.id == "b" }.source)
    assertEquals("source of c", after.cells.first { it.id == "c" }.source)
    assertEquals(1, after.undoable.size)
    assertEquals(setOf("a", "b"), after.sourceRevisions.keys)
    assertTrue(after.dirty)
  }

  /** One undo puts every cell back at once - the transaction the rename answered as. */
  @Test
  fun cellsRenamed_undone_restoresEveryCellAtOnce() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellsRenamed(
      "a",
      mapOf("a" to "x = 1", "b" to "x"),
      askedSources = mapOf("a" to "source of a", "b" to "source of b", "c" to "source of c"),
    )
    edits.undo()

    assertEquals("source of a", state.value.cells.first { it.id == "a" }.source)
    assertEquals("source of b", state.value.cells.first { it.id == "b" }.source)
  }

  /**
   * The gate for a slow answer, [cellFormatted]'s own rule widened to the whole document: the
   * reply was computed over [askedSources], so a cell that moved since - typing reported
   * mid-ask, another operation - means its "unchanged" parts would still clobber newer text.
   * The whole rename is dropped rather than half-applied.
   */
  @Test
  fun cellsRenamed_afterACellMovedSinceTheAsk_changesNothing() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellsRenamed(
      "a",
      mapOf("a" to "x = 1", "b" to "x"),
      askedSources = mapOf("a" to "source of a", "b" to "what the ask saw", "c" to "source of c"),
    )

    assertEquals("source of a", state.value.cells.first { it.id == "a" }.source)
    assertEquals("source of b", state.value.cells.first { it.id == "b" }.source)
    assertEquals(0, state.value.undoable.size)
  }

  /** An answer identical to every cell's own text is a no-op: no entry, no write. */
  @Test
  fun cellsRenamed_withTheSameSources_recordsNothing() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellsRenamed(
      "a",
      mapOf("a" to "source of a", "b" to "source of b"),
      askedSources = mapOf("a" to "source of a", "b" to "source of b", "c" to "source of c"),
    )

    assertEquals(0, state.value.undoable.size)
    assertFalse(state.value.dirty)
  }

  /**
   * A cell that left the document between ask and answer is refused with the rest - the reply
   * names a source for an id nobody holds, which is exactly the moved-since check.
   */
  @Test
  fun cellsRenamed_withACellThatHasGone_changesNothing() = runTest {
    val before = markedRun("a", "b")
    val state = MutableStateFlow(before)
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellsRenamed(
      "a",
      mapOf("a" to "x", "gone" to "y"),
      askedSources = mapOf("a" to "source of a", "gone" to "source of gone", "c" to "source of c"),
    )

    assertEquals(before.cells, state.value.cells)
    assertEquals(0, state.value.undoable.size)
  }

  /**
   * The menu-on-a-cell rule is [applyingFrom]'s own: asked on a cell outside a marked run, the
   * run collapses onto that cell - the reference's `_evtMouseDown` rule, which a rename row
   * takes no exception to.
   */
  @Test
  fun cellsRenamed_onACellOutsideTheRun_collapsesOntoIt() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellsRenamed(
      "c",
      mapOf("c" to "renamed c"),
      askedSources = mapOf("a" to "source of a", "b" to "source of b", "c" to "source of c"),
    )

    assertFalse(state.value.selecting)
    assertEquals("c", state.value.selectedCellId)
    assertEquals("renamed c", state.value.cells.first { it.id == "c" }.source)
  }

  /**
   * The ask's apply half - P5e-6, VS Code's `notebook.formatCell`: the cell's source lands as
   * **one undoable operation**, and the [EditorUiState.sourceRevisions] bump is what tells the
   * cell's own field to adopt it, the way split's rewrite is adopted.
   */
  @Test
  fun cellFormatted_rewritesTheCellUndoably() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellFormatted("a", "formatted a", expectedSource = "source of a")

    val after = state.value
    assertEquals("formatted a", after.cells.first { it.id == "a" }.source)
    assertEquals(1, after.undoable.size)
    assertEquals(1, after.sourceRevisions.getValue("a"))
    assertTrue(after.dirty)
  }

  /** The recorded entry puts back the text the ask was made over, so undo lands where the tap saw. */
  @Test
  fun cellFormatted_undone_restoresTheAskedText() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellFormatted("a", "formatted a", expectedSource = "source of a")
    edits.undo()

    assertEquals("source of a", state.value.cells.first { it.id == "a" }.source)
  }

  /**
   * The gate for a slow answer: [expectedSource] is the text the ask synced and the reply was
   * computed over, so a cell that moved since - typing that reported mid-ask, another
   * operation - is a different document than the formatter rewrote. Its answer is dropped
   * rather than clobbering the newer one.
   */
  @Test
  fun cellFormatted_onACellThatMovedSinceTheAsk_changesNothing() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellFormatted("a", "formatted a", expectedSource = "what the ask saw")

    assertEquals("source of a", state.value.cells.first { it.id == "a" }.source)
    assertEquals(0, state.value.undoable.size)
  }

  /** An answer identical to the cell's own text is the formatter's no-op: no entry, no write. */
  @Test
  fun cellFormatted_withTheTextTheCellAlreadyHas_recordsNothing() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellFormatted("a", "source of a", expectedSource = "source of a")

    assertEquals(0, state.value.undoable.size)
    assertFalse(state.value.dirty)
  }

  /**
   * The menu-on-a-cell rule is [applyingFrom]'s own: asked on a cell outside a marked run, the
   * run collapses onto that cell - the reference's `_evtMouseDown` rule
   * (`packages/notebook/src/widget.ts:3160-3167`), which a format row takes no exception to.
   */
  @Test
  fun cellFormatted_onACellOutsideTheRun_collapsesOntoIt() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellFormatted("c", "formatted c", expectedSource = "source of c")

    assertFalse(state.value.selecting)
    assertEquals("c", state.value.selectedCellId)
    assertEquals("formatted c", state.value.cells.first { it.id == "c" }.source)
  }

  /** A stale id is a no-op, as everywhere in this class. */
  @Test
  fun cellFormatted_onACellThatHasGone_changesNothing() = runTest {
    val before = markedRun("a", "b")
    val state = MutableStateFlow(before)
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.cellFormatted("gone", "formatted", expectedSource = "gone")

    assertEquals(before, state.value)
  }

  /**
   * A definition landing selects the target and arms its caret - `expandParent` on the
   * active-cell change is the reference's reveal, and [EditorUiState.caretLanding] is what the
   * scroll and the field read the rest off.
   */
  @Test
  fun landOnDefinition_revealsSelectsAndArmsTheCaret() = runTest {
    val hidden = EditorUiState(
      cells = listOf(
        Cell("h1", CellKind.MARKDOWN, "# Part", headingCollapsed = true),
        Cell("a", CellKind.CODE, "def compute_total(items):\n    return sum(items)"),
        Cell("b", CellKind.CODE, "total = compute_total([1, 2])"),
      ),
      loaded = true,
      selectedCellId = "b",
    )
    val state = MutableStateFlow(hidden)
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.landOnDefinition(DefinitionTarget("a", 4, 17))

    assertFalse("the collapsed section over the target is opened", state.value.cells[0].headingCollapsed)
    assertEquals("a", state.value.selectedCellId)
    assertEquals(DefinitionTarget("a", 4, 17), state.value.caretLanding)
  }

  /** A marked run collapses on the landing, the way a press outside it collapses one. */
  @Test
  fun landOnDefinition_dropsAMarkedRun() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.landOnDefinition(DefinitionTarget("c", 0, 4))

    assertFalse(state.value.selecting)
    assertEquals("c", state.value.selectedCellId)
  }

  /** A stale target is a no-op, as everywhere in this package. */
  @Test
  fun landOnDefinition_onACellThatHasGone_changesNothing() = runTest {
    val before = markedRun("a", "b")
    val state = MutableStateFlow(before)
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.landOnDefinition(DefinitionTarget("gone", 0, 4))

    assertEquals(before, state.value)
  }

  /** The landing is a one-shot: consumed by the field that takes it, cleared for the next ask. */
  @Test
  fun caretLanded_clearsThePendingLanding() = runTest {
    val state = MutableStateFlow(markedRun("a", "b"))
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.landOnDefinition(DefinitionTarget("c", 0, 4))
    edits.caretLanded()

    assertEquals(null, state.value.caretLanding)
    assertEquals("c", state.value.selectedCellId)
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
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

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
    val edits = NotebookCellEdits(
      state,
      DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
    )

    edits.applyTo("a", CellAction.Delete)

    assertEquals(listOf("c"), state.value.cells.map { it.id })
  }

  /** Three cells, with [marked] the run and its head on the last one named. */
  private fun markedRun(vararg marked: String) = EditorUiState(
    cells = listOf("a", "b", "c").map { Cell(it, CellKind.CODE, "source of $it") },
    loaded = true,
  ).withSelectionStartedAt(marked.first()).withSelectionExtendedTo(marked.last())
}
