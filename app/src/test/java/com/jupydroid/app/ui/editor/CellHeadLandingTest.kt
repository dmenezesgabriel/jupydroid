package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the head lands is the reference's own arithmetic, and there are two answers rather
 * than one. The inserts and `addCells` name their result - *"The last added cell becomes the
 * active cell"* (`actions.tsx:1568`), `insertBelow`'s `newIndex` (`:569`) - while a head whose
 * cell went away gets `handleState`'s clamp: the index is kept and whatever slid into it takes
 * the ring. Without the landing, `D`,`D` left `selectedCellId = null` - a headless notebook
 * that `J`/`K` then cannot move.
 */
class CellHeadLandingTest {
  @Test
  fun insertBelow_landsTheHeadOnTheNewCell() {
    val inserted = selected("b").applying(CellAction.Insert(CellKind.CODE))

    // a, b, <new>, c - `activeCellIndex = newIndex` (`actions.tsx:569`).
    assertEquals(inserted.cells[2].id, inserted.selectedCellId)
  }

  @Test
  fun insertAbove_landsTheHeadOnTheNewCell() {
    val inserted = selected("b").applying(CellAction.InsertAbove(CellKind.CODE))

    // a, <new>, b, c - `activeCellIndex = newIndex` (`actions.tsx:541`).
    assertEquals(inserted.cells[1].id, inserted.selectedCellId)
  }

  @Test
  fun paste_landsTheHeadOnTheLastPastedCell() {
    val state = selected("b").copy(
      clipboard = listOf(Cell("p1", CellKind.CODE, "x"), Cell("p2", CellKind.CODE, "y")),
    )

    val pasted = state.applying(CellAction.Paste)

    // a, b, p1, p2, c - "The last added cell becomes the active cell" (`actions.tsx:1568`).
    assertEquals(pasted.cells[3].id, pasted.selectedCellId)
    assertEquals("y", pasted.cells[3].source)
  }

  @Test
  fun delete_landsTheHeadOnTheCellSlidingIntoItsPlace() {
    val deleted = selected("b").applying(CellAction.Delete)

    // a, c - `handleState` keeps the index, so "c" slides under it (`actions.tsx:310`).
    assertEquals("c", deleted.selectedCellId)
  }

  @Test
  fun deleteOnTheLastCell_landsTheHeadOnTheSurvivor() {
    val deleted = selected("c").applying(CellAction.Delete)

    // Index 2 no longer exists; the clamp is to the list's end (`actions.tsx:310`).
    assertEquals("b", deleted.selectedCellId)
  }

  @Test
  fun cut_landsLikeDelete() {
    val cut = selected("b").applying(CellAction.Cut)

    assertEquals("c", cut.selectedCellId)
  }

  @Test
  fun mergeBelow_landsTheHeadOnTheMergedCell() {
    val merged = selected("b").applying(CellAction.Merge(MergeInto.BELOW))

    // a, <b+c> - `deleteCell(active); insertCell(active, …)` puts the merged cell at the index.
    assertEquals(merged.cells[1].id, merged.selectedCellId)
    assertEquals("source of b\n\nsource of c", merged.cells[1].source)
  }

  /**
   * The asymmetric case: a merge above puts the merged cell *before* the freed index, and
   * `handleState` does not know that - it keeps the index, so the head lands on the cell after
   * the merge, not on the merged cell (`actions.tsx:455-470`, the transact then the clamp).
   */
  @Test
  fun mergeAbove_landsOnTheIndexNotOnTheMergedCell() {
    val merged = selected("b").applying(CellAction.Merge(MergeInto.ABOVE))

    // <a+b>, c - upstream's activeCellIndex stays 1, which is "c" after the fold.
    assertEquals("c", merged.selectedCellId)
  }

  @Test
  fun duplicate_landsTheHeadOnTheLastCopy() {
    val duplicated = markedRun("a", "b").applying(CellAction.Duplicate)

    // a, b, <a'>, <b'>, c - `belowSelected`, and "the last added cell" is the active one.
    assertEquals(duplicated.cells[3].id, duplicated.selectedCellId)
  }

  /**
   * Every other operation is unchanged on purpose: the moves, kind changes and folds keep every
   * id, so the head is already where the transform put it.
   */
  @Test
  fun unchangedOps_leaveTheHeadAlone() {
    val moved = markedRun("a", "b").applying(CellAction.Move(CellMove.DOWN))

    assertEquals("b", moved.selectedCellId)
  }

  /** Three cells, with [marked] the run and its head on the last one named. */
  private fun markedRun(vararg marked: String) = EditorUiState(
    cells = listOf("a", "b", "c").map { Cell(it, CellKind.CODE, "source of $it") },
    loaded = true,
  ).withSelectionStartedAt(marked.first()).withSelectionExtendedTo(marked.last())

  /** The same three cells with a single cell headed, for the one-cell verb tests. */
  private fun selected(head: String) = EditorUiState(
    cells = listOf("a", "b", "c").map { Cell(it, CellKind.CODE, "source of $it") },
    loaded = true,
    selectedCellId = head,
  )
}
