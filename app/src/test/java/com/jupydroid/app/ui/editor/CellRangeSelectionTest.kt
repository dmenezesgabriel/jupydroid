package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contiguous cell selection, against the reference that defines it.
 *
 * Every case names the file and line it comes from, as `CellEditsTest` does - three of these are
 * not what a first guess produces.
 */
class CellRangeSelectionTest {
  /**
   * `_updateSelectedCells` filters on `isSelectedOrActive`
   * (`packages/notebook/src/widget.ts:3736-3739`), so a notebook with nothing marked still has a
   * one-cell selection. That is what makes today's single-cell behaviour the degenerate case of
   * this one rather than a second code path.
   */
  @Test
  fun selection_isTheActiveCellAloneWhenNothingIsMarked() {
    val state = stateOf("a", "b", "c").withCellSelected("b")

    assertEquals(listOf("b"), state.selectedCellIds)
    assertFalse(state.selecting)
  }

  @Test
  fun selection_isEmptyWithNoActiveCell() {
    assertEquals(emptyList<String>(), stateOf("a", "b").selectedCellIds)
  }

  @Test
  fun startingASelection_marksTheOneCellAndTurnsTheModeOn() {
    val state = stateOf("a", "b", "c").withSelectionStartedAt("b")

    assertEquals(listOf("b"), state.selectedCellIds)
    assertTrue(state.selecting)
    assertEquals("b", state.selectedCellId)
  }

  @Test
  fun startingASelection_onACellThatIsGoneChangesNothing() {
    val state = stateOf("a", "b")

    assertEquals(state, state.withSelectionStartedAt("missing"))
  }

  /**
   * `extendContiguousSelectionTo` moves the **head** and leaves the anchor
   * (`packages/notebook/src/widget.ts:2216-2320`), so the range runs from where the selection
   * started to wherever it was last extended - in document order whichever way it was dragged.
   */
  @Test
  fun extending_coversEveryCellBetweenTheAnchorAndTheHead() {
    val state = stateOf("a", "b", "c", "d").withSelectionStartedAt("b").withSelectionExtendedTo("d")

    assertEquals(listOf("b", "c", "d"), state.selectedCellIds)
    assertEquals("d", state.selectedCellId)
  }

  @Test
  fun extending_upwardsIsStillInDocumentOrder() {
    val state = stateOf("a", "b", "c", "d").withSelectionStartedAt("c").withSelectionExtendedTo("a")

    assertEquals(listOf("a", "b", "c"), state.selectedCellIds)
    assertEquals("a", state.selectedCellId)
  }

  /**
   * Extending with nothing marked starts a selection from the active cell - *"Handle the case of
   * no current selection… head = anchor = this.activeCellIndex"* (`widget.ts:2222-2232`). It is
   * what makes the four `extend-marked-cells-*` commands work without a selection mode first.
   */
  @Test
  fun extending_withoutASelectionAnchorsOnTheActiveCell() {
    val state = stateOf("a", "b", "c").withCellSelected("a").withSelectionExtendedTo("b")

    assertEquals(listOf("a", "b"), state.selectedCellIds)
    assertTrue(state.selecting)
  }

  /**
   * **Extending back onto the anchor drops the selection outright**, rather than leaving that one
   * cell marked: *"Collapse the selection if it is only the active cell"* → `deselectAll()`
   * (`widget.ts:2245-2249`). On a touch screen that is also the Material rule - clearing the last
   * item leaves selection mode - so the reference and the platform agree here.
   */
  @Test
  fun extending_ontoTheAnchorEndsTheSelection() {
    val state = stateOf("a", "b", "c").withSelectionStartedAt("b").withSelectionExtendedTo("b")

    assertEquals(listOf("b"), state.selectedCellIds)
    assertFalse(state.selecting)
  }

  @Test
  fun extending_toACellThatIsGoneChangesNothing() {
    val state = stateOf("a", "b").withSelectionStartedAt("a")

    assertEquals(state, state.withSelectionExtendedTo("missing"))
  }

  @Test
  fun reachingAboveAndBelow_movesTheHeadOneCell() {
    val start = stateOf("a", "b", "c", "d").withSelectionStartedAt("b")

    assertEquals(listOf("a", "b"), start.withSelectionExtended(SelectionReach.ABOVE).selectedCellIds)
    assertEquals(listOf("b", "c"), start.withSelectionExtended(SelectionReach.BELOW).selectedCellIds)
  }

  @Test
  fun reachingToTopAndBottom_takesEverythingOnThatSide() {
    val start = stateOf("a", "b", "c", "d").withSelectionStartedAt("c")

    assertEquals(
      listOf("a", "b", "c"),
      start.withSelectionExtended(SelectionReach.TOP).selectedCellIds,
    )
    assertEquals(listOf("c", "d"), start.withSelectionExtended(SelectionReach.BOTTOM).selectedCellIds)
  }

  /**
   * *"Do not wrap around"* - `extendSelectionAbove` bails on index 0 and `extendSelectionBelow` on
   * the last cell (`packages/notebook/src/actions.tsx:1318-1322`, `:1352-1356`).
   */
  @Test
  fun reaching_pastEitherEndDoesNothing() {
    val top = stateOf("a", "b").withSelectionStartedAt("a")
    val bottom = stateOf("a", "b").withSelectionStartedAt("b")

    assertEquals(top, top.withSelectionExtended(SelectionReach.ABOVE))
    assertEquals(bottom, bottom.withSelectionExtended(SelectionReach.BELOW))
  }

  @Test
  fun reaching_withNoActiveCellDoesNothing() {
    val state = stateOf("a", "b")

    assertEquals(state, state.withSelectionExtended(SelectionReach.BELOW))
  }

  /**
   * **A departure, and it is the reference's own invariant kept against one call site that breaks
   * it.** `selectAll` marks every widget and leaves `activeCellIndex` alone
   * (`actions.tsx:1376-1386`); with the active cell in the middle, the next
   * `extendSelectionAbove` reaches `getContiguousSelection()`, which throws *"Active cell not at
   * endpoint of selection"* (`widget.ts:2344-2348`) with nothing catching it. The same file
   * documents the rule being broken - *"The head of a contiguous selection is always the active
   * cell"* - so the head goes on the last cell and every later extend is well defined.
   */
  @Test
  fun selectAll_takesEveryCellAndLeavesTheHeadAtTheEnd() {
    val state = stateOf("a", "b", "c").withCellSelected("b").withAllCellsSelected()

    assertEquals(listOf("a", "b", "c"), state.selectedCellIds)
    assertEquals("c", state.selectedCellId)
    assertTrue(state.selecting)
  }

  @Test
  fun deselectAll_leavesTheHeadWhereItWas() {
    val state = stateOf("a", "b", "c").withSelectionStartedAt("b").withSelectionExtendedTo("c")

    val collapsed = state.withSelectionCollapsed()

    assertEquals(listOf("c"), collapsed.selectedCellIds)
    assertFalse(collapsed.selecting)
  }

  /**
   * *"Edit mode deselects all cells"* - `setMode`, `packages/notebook/src/widget.ts:1925-1929`. It
   * is one line there and it is what lets a tap into a cell mean "type here" while a selection is
   * live, instead of needing a gesture that says "leave selection mode first".
   */
  @Test
  fun editing_dropsTheSelection() {
    val state = stateOf("a", "b", "c").withSelectionStartedAt("a").withSelectionExtendedTo("c")

    val editing = state.withMode(CellMode.EDIT)

    assertFalse(editing.selecting)
    assertEquals(listOf("c"), editing.selectedCellIds)
  }

  @Test
  fun commandMode_keepsTheSelection() {
    val state = stateOf("a", "b").withSelectionStartedAt("a").withSelectionExtendedTo("b")

    assertTrue(state.withMode(CellMode.COMMAND).selecting)
  }

  /**
   * The anchor is clamped for the reason the head is: an anchor naming a cell that has gone leaves
   * a range that cannot be computed, and every action scoped to it silently does the wrong thing.
   */
  @Test
  fun clamping_dropsAnAnchorWhoseCellIsGone() {
    val state = stateOf("a", "b", "c").withSelectionStartedAt("a").withSelectionExtendedTo("c")

    val clamped = state.copy(cells = state.cells.filterNot { it.id == "a" }).withSelectionClamped()

    assertNull(clamped.anchorCellId)
    assertEquals(listOf("c"), clamped.selectedCellIds)
  }

  @Test
  fun clamping_dropsBothWhenTheHeadIsGoneToo() {
    val state = stateOf("a", "b").withSelectionStartedAt("a").withSelectionExtendedTo("b")

    val clamped = state.copy(cells = emptyList()).withSelectionClamped()

    assertNull(clamped.anchorCellId)
    assertNull(clamped.selectedCellId)
    assertEquals(CellMode.COMMAND, clamped.mode)
  }

  private fun stateOf(vararg ids: String) = EditorUiState(
    cells = ids.map { Cell(it, CellKind.CODE, "source of $it") },
    loaded = true,
  )
}
