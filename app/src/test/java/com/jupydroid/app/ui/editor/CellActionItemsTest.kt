package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the menu says it will do, once it can be asked to do it to several cells.
 *
 * The reference pluralises with `trans._n` and only where the count changes the noun -
 * `'Delete Cell'`/`'Delete Cells'`, `'Move Cell Up'`/`'Move Cells Up'`, `'Cut Cell'`/`'Cut Cells'`
 * (`packages/notebook-extension/src/index.ts:3480`, `:3733`, `:4297`) - while `'Clear Cell Output'`
 * and `'Collapse Selected Code'` have one form each (`:3365`, `:4741`). Both halves are copied.
 */
class CellActionItemsTest {
  @Test
  fun oneCellReadsExactlyAsItAlwaysHas() {
    val labels = itemsFor(count = 1)

    assertTrue(labels.containsAll(listOf("Move up", "Duplicate", "Cut", "Copy", "Delete")))
  }

  @Test
  fun aRunPluralisesTheRowsTheReferencePluralises() {
    val labels = itemsFor(count = 3)

    assertTrue(
      labels.containsAll(
        listOf("Move cells up", "Move cells down", "Duplicate cells", "Cut cells", "Copy cells", "Delete cells"),
      ),
    )
    assertFalse(labels.contains("Delete"))
  }

  /** One form each there, so one form each here (`index.ts:3365`, `:4741`). */
  @Test
  fun aRunLeavesTheRowsTheReferenceLeaves() {
    val labels = itemsFor(count = 3)

    assertTrue(labels.containsAll(listOf("Clear outputs", COLLAPSE_CODE, "Split cell", "Insert code below")))
  }

  @Test
  fun aRunChangesTheTypeOfAllOfThem() {
    assertTrue(itemsFor(count = 3).contains("Change cells to markdown"))
    assertTrue(itemsFor(count = 1).contains(CHANGE_TO_MARKDOWN))
  }

  /**
   * **Two rows, not one**: there are three cell types and the reference has a command for each -
   * `notebook:change-cell-to-{code,markdown,raw}` (`packages/notebook-extension/src/index.ts:212-214`).
   * A single "change type" row could only ever name one of the two a cell is not, so the other
   * would be unreachable from the menu entirely.
   */
  @Test
  fun everyCellOffersTheTwoTypesItIsNot() {
    assertEquals(listOf(CHANGE_TO_MARKDOWN, CHANGE_TO_RAW), changeRowsFor(CellKind.CODE))
    assertEquals(listOf(CHANGE_TO_CODE, CHANGE_TO_RAW), changeRowsFor(CellKind.MARKDOWN))
    assertEquals(listOf(CHANGE_TO_CODE, CHANGE_TO_MARKDOWN), changeRowsFor(CellKind.RAW))
  }

  /** The rows carry the action, not only the words. */
  @Test
  fun theRawRowAsksForARawCell() {
    val items = cellActionItems(Cell("c1", CellKind.CODE, "x = 1"), canPaste = false, selecting = false)

    assertEquals(CellAction.ChangeKind(CellKind.RAW), items.single { it.first == CHANGE_TO_RAW }.second)
  }

  /** Pluralised the way every other type row is, and by the same rule. */
  @Test
  fun aRunChangesAllOfThemToRawToo() {
    assertTrue(itemsFor(count = 3).contains("Change cells to raw"))
  }

  private fun changeRowsFor(kind: CellKind) =
    cellActionItems(Cell("c1", kind, "x = 1"), canPaste = false, selecting = false)
      .map { it.first }
      .filter { it.startsWith("Change to") }

  /**
   * **The two directional merges become one row, because the direction stops applying.**
   * `mergeCells` consults `mergeAbove` only inside `if (toMerge.length === 1)`
   * (`packages/notebook/src/actions.tsx:390-425`), so with a run both rows would do the same
   * thing - and two rows that do one thing is a menu that lies about one of them.
   *
   * The replacement is not invented either: it is the reference's own third merge command,
   * `notebook:merge-cells`, labelled *"Merge Selected Cells"* (`index.ts:3953`).
   */
  @Test
  fun aRunOffersOneMergeInsteadOfTwo() {
    val labels = itemsFor(count = 3)

    assertEquals(listOf(MERGE_SELECTED), labels.filter { it.startsWith("Merge") })
  }

  @Test
  fun oneCellStillOffersBothDirections() {
    val labels = itemsFor(count = 1)

    assertEquals(listOf("Merge with above", "Merge with below"), labels.filter { it.startsWith("Merge") })
  }

  private fun itemsFor(count: Int) =
    cellActionItems(
      Cell("c1", CellKind.CODE, "x = 1"),
      canPaste = false,
      selecting = count > 1,
      count = count,
    ).map { it.first }
}
