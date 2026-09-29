package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a cell operation does to **several** cells, which is what JupyterLab's do by default:
 * merge, move, cut, copy, duplicate, change type, clear outputs, collapse and delete all iterate
 * `isSelectedOrActive` rather than acting on the active cell alone (`actions.tsx:367`, `:594`,
 * `:1611`, `:1832`, `:3109`, `:3232`, `:3342`).
 *
 * One cell is the degenerate case of these, so `CellEditsTest` and `CellRestructureTest` still own
 * the semantics of each operation and this file owns only what changes when there is more than one.
 */
class CellBatchEditsTest {
  /**
   * A block, not each cell in turn: `move` finds the first and last selected and shifts the whole
   * run past one neighbour (`packages/notebook/src/actions.tsx:586-610`). Moving each cell
   * separately would walk them over each other and reverse their order.
   */
  @Test
  fun move_shiftsTheWholeRunPastOneNeighbour() {
    val state = stateOf("a", "b", "c", "d")

    assertEquals(
      listOf("a", "d", "b", "c"),
      state.withCellsMoved(listOf("b", "c"), CellMove.DOWN).cells.map { it.id },
    )
    assertEquals(
      listOf("b", "c", "a", "d"),
      state.withCellsMoved(listOf("b", "c"), CellMove.UP).cells.map { it.id },
    )
  }

  @Test
  fun move_doesNothingWhenTheRunIsAlreadyAgainstThatEnd() {
    val state = stateOf("a", "b", "c")

    assertEquals(state, state.withCellsMoved(listOf("a", "b"), CellMove.UP))
    assertEquals(state, state.withCellsMoved(listOf("b", "c"), CellMove.DOWN))
  }

  @Test
  fun delete_removesEveryCellInTheRun() {
    val state = stateOf("a", "b", "c").withOutputOn("b", "gone")

    val left = state.withCellsDeleted(listOf("a", "b"), replacementId = "new")

    assertEquals(listOf("c"), left.cells.map { it.id })
    assertEquals(emptyMap<String, List<CellOutput>>(), left.outputs)
  }

  /** The rule `CellEditsTest` pins for one cell, and it has to survive the whole notebook going. */
  @Test
  fun delete_leavesAnEmptyCodeCellWhenTheRunIsEverything() {
    val left = stateOf("a", "b").withCellsDeleted(listOf("a", "b"), replacementId = "new")

    assertEquals(listOf("new"), left.cells.map { it.id })
    assertEquals(CellKind.CODE, left.cells.single().kind)
  }

  /**
   * `notebook:duplicate-below` passes `'belowSelected'`, not `'below'`
   * (`packages/notebook-extension/src/index.ts:3675`), so the copies land after the **last**
   * selected cell rather than after the active one - which is what stops them appearing inside
   * the run when the head is at its top.
   */
  @Test
  fun duplicate_putsEveryCopyAfterTheLastCellInTheRun() {
    val state = stateOf("a", "b", "c").withOutputOn("a", "kept").withCount("a", 7)

    val duplicated = state.withCellsDuplicated(listOf("a", "b"), newIds = listOf("a2", "b2"))

    assertEquals(listOf("a", "b", "a2", "b2", "c"), duplicated.cells.map { it.id })
    assertEquals(state.outputs["a"], duplicated.outputs["a2"])
    assertEquals(7, duplicated.executionCounts["a2"])
  }

  @Test
  fun copy_takesEveryCellInTheRunInDocumentOrder() {
    val state = stateOf("a", "b", "c").withCellsCopied(listOf("b", "c"))

    assertEquals(listOf("b", "c"), state.clipboard.map { it.id })
    assertFalse(state.clipboardWasCut)
    assertEquals(3, state.cells.size)
  }

  @Test
  fun cut_takesTheRunAndRemovesIt() {
    val state = stateOf("a", "b", "c").withCellsCut(listOf("a", "b"), replacementId = "new")

    assertEquals(listOf("a", "b"), state.clipboard.map { it.id })
    assertTrue(state.clipboardWasCut)
    assertEquals(listOf("c"), state.cells.map { it.id })
  }

  /**
   * **The direction is ignored once more than one cell is marked.** `mergeCells` only consults
   * `mergeAbove` inside `if (toMerge.length === 1)` (`actions.tsx:390-425`): with a run there is
   * nothing to reach for, the run *is* what merges.
   */
  @Test
  fun merge_foldsTheWholeRunAndIgnoresTheDirection() {
    val state = stateOf("a", "b", "c", "d")

    val above = state.withCellsMerged(listOf("b", "c"), MergeInto.ABOVE, newId = "m")
    val below = state.withCellsMerged(listOf("b", "c"), MergeInto.BELOW, newId = "m")

    assertEquals(listOf("a", "m", "d"), above.cells.map { it.id })
    assertEquals(above.cells, below.cells)
    assertEquals("source of b\n\nsource of c", above.cells[1].source)
  }

  /** With one cell there is nothing to fold, so the direction is back in charge. */
  @Test
  fun merge_ofOneCellStillReachesForItsNeighbour() {
    val state = stateOf("a", "b", "c")

    val above = state.withCellsMerged(listOf("b"), MergeInto.ABOVE, newId = "m")

    assertEquals(listOf("m", "c"), above.cells.map { it.id })
    assertEquals("source of a\n\nsource of b", above.cells.first().source)
  }

  /** The first cell in the run decides the type of what comes out, as the active cell does there. */
  @Test
  fun merge_takesItsKindFromTheFirstCellInTheRun() {
    val state = EditorUiState(
      cells = listOf(
        Cell("a", CellKind.MARKDOWN, "# title"),
        Cell("b", CellKind.CODE, "x = 1"),
      ),
      loaded = true,
    )

    val merged = state.withCellsMerged(listOf("a", "b"), MergeInto.BELOW, newId = "m")

    assertEquals(CellKind.MARKDOWN, merged.cells.single().kind)
  }

  @Test
  fun changeKind_changesEveryCellInTheRunAndDropsTheirOutputs() {
    val state = stateOf("a", "b", "c").withOutputOn("a", "gone").withCount("b", 4)

    val changed = state.withCellsKindChanged(listOf("a", "b"), CellKind.MARKDOWN)

    assertEquals(listOf(CellKind.MARKDOWN, CellKind.MARKDOWN, CellKind.CODE), changed.cells.map { it.kind })
    assertEquals(emptyMap<String, List<CellOutput>>(), changed.outputs)
    assertEquals(emptyMap<String, Int>(), changed.executionCounts)
  }

  @Test
  fun clearOutputs_clearsEveryCellInTheRunAndLeavesTheRest() {
    val state = stateOf("a", "b").withOutputOn("a", "one").withOutputOn("b", "two").withCount("b", 3)

    val cleared = state.withOutputsCleared(listOf("a"))

    assertEquals(setOf("b"), cleared.outputs.keys)
    assertEquals(3, cleared.executionCounts["b"])
  }

  /**
   * **Set, not toggled per cell.** JupyterLab has `hideCode` and `showCode` as separate commands
   * over the selection (`actions.tsx:1877-1910`), so a run ends up uniform; toggling each cell on
   * its own state would invert a mixed run and leave it exactly as mixed.
   */
  @Test
  fun collapsingSource_leavesTheWholeRunTheSameWayUp() {
    val state = stateOf("a", "b", "c").withSourceHiddenSet(listOf("a"), hidden = true)

    val collapsed = state.withSourceHiddenSet(listOf("a", "b"), hidden = true)

    assertEquals(listOf(true, true, false), collapsed.cells.map { it.sourceHidden })
    assertEquals(
      listOf(false, false, false),
      collapsed.withSourceHiddenSet(listOf("a", "b"), hidden = false).cells.map { it.sourceHidden },
    )
  }

  @Test
  fun collapsingOutputs_leavesTheWholeRunTheSameWayUpToo() {
    val hidden = stateOf("a", "b").withOutputsHiddenSet(listOf("a", "b"), hidden = true)

    assertEquals(listOf(true, true), hidden.cells.map { it.outputsHidden })
    assertFalse(hidden.cells.any { it.sourceHidden })
  }

  // --- notebook:enable/disable/toggle-output-scrolling -----------------------------

  /**
   * `isOutputScrollingEnabled` is **all-of**, not head-of: true only when every selected code
   * cell is scrolled (`packages/notebook-extension/src/index.ts:2780-2796`). One of two not yet
   * scrolled is what every other fold in this file leaves mixed and inverts from the head; this
   * one turns every code cell in the run *on* instead, because the run as a whole was not "all
   * scrolled" yet.
   */
  @Test
  fun outputScrolling_turnsOnEveryCodeCellWhenTheRunIsNotAllScrolledYet() {
    val state = stateOf("a", "b", "c").withScrolled("a")

    val toggled = state.withOutputsScrolledSet(listOf("a", "b"), scrolled = true)

    assertEquals(listOf(true, true, false), toggled.cells.map { it.outputsScrolled })
  }

  /** Only once every code cell in the run already agrees does the toggle turn them all off. */
  @Test
  fun outputScrolling_turnsOffOnlyWhenTheWholeRunAlreadyAgrees() {
    val state = stateOf("a", "b").withScrolled("a").withScrolled("b")

    val toggled = state.withOutputsScrolledSet(listOf("a", "b"), scrolled = false)

    assertEquals(listOf(false, false), toggled.cells.map { it.outputsScrolled })
  }

  /**
   * A markdown cell caught up in the run is left alone - the reference's own guard is
   * `cell.model.type === 'code'` inside the loop that writes, not only inside the one that reads
   * (`notebook/src/actions.tsx:2088-2092`).
   */
  @Test
  fun outputScrolling_leavesAMarkdownCellInTheRunUntouched() {
    val state = EditorUiState(
      cells = listOf(
        Cell("a", CellKind.CODE, "x = 1"),
        Cell("b", CellKind.MARKDOWN, "# note"),
      ),
      loaded = true,
    )

    val toggled = state.withOutputsScrolledSet(listOf("a", "b"), scrolled = true)

    assertTrue(toggled.cells[0].outputsScrolled)
    assertFalse(toggled.cells[1].outputsScrolled)
  }

  /** A menu can outlive the cells it was opened on; none of these may throw on ids that are gone. */
  @Test
  fun idsThatAreGoneChangeNothing() {
    val state = stateOf("a", "b")
    val gone = listOf("x", "y")

    assertEquals(state, state.withCellsMoved(gone, CellMove.UP))
    assertEquals(state, state.withCellsDeleted(gone, "new"))
    assertEquals(state, state.withCellsDuplicated(gone, listOf("n1", "n2")))
    assertEquals(state, state.withCellsCopied(gone))
    assertEquals(state, state.withCellsCut(gone, "new"))
    assertEquals(state, state.withCellsMerged(gone, MergeInto.ABOVE, "m"))
    assertEquals(state, state.withCellsKindChanged(gone, CellKind.MARKDOWN))
    assertEquals(state, state.withOutputsCleared(gone))
    assertEquals(state, state.withSourceHiddenSet(gone, hidden = true))
    assertEquals(state, state.withOutputsHiddenSet(gone, hidden = true))
    assertEquals(state, state.withOutputsScrolledSet(gone, scrolled = true))
  }

  private fun stateOf(vararg ids: String) = EditorUiState(
    cells = ids.map { Cell(it, CellKind.CODE, "source of $it") },
    loaded = true,
  )

  private fun EditorUiState.withOutputOn(cellId: String, text: String) =
    copy(outputs = outputs + (cellId to listOf(CellOutput.Stream(StreamName.STDOUT, text))))

  private fun EditorUiState.withScrolled(cellId: String) =
    copy(cells = cells.map { if (it.id == cellId) it.copy(outputsScrolled = true) else it })

  private fun EditorUiState.withCount(cellId: String, count: Int) =
    copy(executionCounts = executionCounts + (cellId to count))
}
