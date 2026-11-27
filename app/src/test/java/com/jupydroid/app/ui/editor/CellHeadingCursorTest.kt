package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `ArrowLeft`/`ArrowRight` commands' half of the cursor keys: fold the heading under the head, or
 * jump to a heading. Their section model is `CellHeadingsTest`'s; the tests name the upstream
 * line each rule is read out of (`packages/notebook/src/actions.tsx`).
 */
class CellHeadingCursorTest {
  private fun md(id: String, source: String) = Cell(id, CellKind.MARKDOWN, source)

  private fun code(id: String) = Cell(id, CellKind.CODE, "print(1)")

  /**
   * ```
   * h1  # Chapter
   * a   code
   * h2  ## Part
   * b   code
   * h1b # Next chapter
   * c   code
   * ```
   */
  private val notebook = EditorUiState(
    cells = listOf(
      md("h1", "# Chapter"),
      code("a"),
      md("h2", "## Part"),
      code("b"),
      md("h1b", "# Next chapter"),
      code("c"),
    ),
    loaded = true,
  )

  // --- ArrowLeft / ArrowRight: fold, or jump to a heading -------------------------------

  /**
   * `selectHeadingAboveOrCollapseHeading` (`actions.tsx:1244-1270`): an **uncollapsed** heading
   * under the head folds first; the key does not move.
   */
  @Test
  fun arrowLeftOnAnUncollapsedHeading_collapsesIt() {
    val moved = notebook.copy(selectedCellId = "h1").withHeadingAboveOrCollapsed()

    assertTrue(moved.cells.first { it.id == "h1" }.headingCollapsed)
    assertEquals(setOf("a", "h2", "b"), moved.hiddenCellIds)
    assertEquals("h1", moved.selectedCellId)
    assertTrue(moved.dirty)
  }

  /**
   * Anything else - a collapsed heading included - jumps to the nearest heading above at
   * equal-or-higher level: `findLowerEqualLevelParentHeadingAbove`, the base level being the
   * head's section's (`determineHeadingLevel`, `actions.tsx:3576-3597`). A collapsed heading
   * expands on ArrowRight, not ArrowLeft.
   */
  @Test
  fun arrowLeftOnACollapsedHeading_jumpsToItsParentHeading() {
    // Collapsed as a fact on the cell, not through withHeadingCollapsed - which would set dirty
    // before the key was ever pressed and leave nothing to assert against.
    val collapsed = notebook.copy(
      selectedCellId = "h2",
      cells = notebook.cells.map { if (it.id == "h2") it.copy(headingCollapsed = true) else it },
    )

    val moved = collapsed.withHeadingAboveOrCollapsed()

    assertEquals("h1", moved.selectedCellId)
    assertTrue(moved.cells.first { it.id == "h2" }.headingCollapsed)
    assertFalse(moved.dirty)
  }

  /** A plain cell's jump lands on the heading of the section it is in - `determineHeadingLevel`. */
  @Test
  fun arrowLeftOnAPlainCell_jumpsToTheHeadingOfItsSection() {
    assertEquals("h1", notebook.copy(selectedCellId = "a").withHeadingAboveOrCollapsed().selectedCellId)
    assertEquals("h2", notebook.copy(selectedCellId = "b").withHeadingAboveOrCollapsed().selectedCellId)
  }

  /**
   * With no qualifying heading above, the head does not move - but `deselectAll()` still runs,
   * which is the reference's unconditional last line (`actions.tsx:1269`). The same unconditional
   * line makes the no-qualifying-heading case still count a key move: the frame is re-asked even
   * when the cursor had nowhere to go.
   */
  @Test
  fun arrowLeftWithNothingAbove_keepsTheHeadButDropsTheRun() {
    val bare = EditorUiState(
      cells = listOf(Cell("x", CellKind.CODE, "1"), Cell("h", CellKind.MARKDOWN, "# H")),
      loaded = true,
    ).withSelectionStartedAt("x")

    val moved = bare.withHeadingAboveOrCollapsed()

    assertEquals("x", moved.selectedCellId)
    assertNull(moved.anchorCellId)
    assertFalse(moved.dirty)
    assertEquals(bare.headMovesByKey + 1, moved.headMovesByKey)
  }

  /**
   * The jump can land on a heading itself hidden inside a collapsed section, which
   * `activeCellChanged` → `expandParent` then opens (`index.ts:2871-2873`) - here the reveal is
   * part of the transform, so the head never ends up on a cell nothing draws.
   */
  @Test
  fun arrowLeft_revealsAHeadingHiddenInsideACollapsedSection() {
    val nested = EditorUiState(
      cells = listOf(
        Cell("h0", CellKind.MARKDOWN, "# Outer", headingCollapsed = true),
        Cell("h1", CellKind.MARKDOWN, "## Inner"),
        Cell("d", CellKind.CODE, "1"),
        Cell("e", CellKind.CODE, "2"),
      ),
      loaded = true,
    ).copy(selectedCellId = "e")

    val moved = nested.withHeadingAboveOrCollapsed()

    assertEquals("h1", moved.selectedCellId)
    assertFalse(moved.cells.first { it.id == "h0" }.headingCollapsed)
    assertTrue(moved.dirty)
  }

  /**
   * `selectHeadingBelowOrExpandHeading` (`actions.tsx:1281-1300`): a **collapsed** heading under
   * the head expands first; anything else jumps to the next heading below at any level.
   */
  @Test
  fun arrowRightOnACollapsedHeading_expandsIt() {
    val collapsed = notebook.copy(
      selectedCellId = "h1",
      cells = notebook.cells.map { if (it.id == "h1") it.copy(headingCollapsed = true) else it },
    )

    val moved = collapsed.withHeadingBelowOrExpanded()

    assertFalse(moved.cells.first { it.id == "h1" }.headingCollapsed)
    assertEquals(emptySet<String>(), moved.hiddenCellIds)
    assertEquals("h1", moved.selectedCellId)
    assertTrue(moved.dirty)
  }

  @Test
  fun arrowRightOnAnythingElse_jumpsToTheNextHeadingBelow() {
    assertEquals("h2", notebook.copy(selectedCellId = "a").withHeadingBelowOrExpanded().selectedCellId)
    // Any level: from inside h2's section, the next heading is its sibling h1b.
    assertEquals("h1b", notebook.copy(selectedCellId = "b").withHeadingBelowOrExpanded().selectedCellId)
  }

  @Test
  fun arrowRightWithNoHeadingBelow_keepsTheHeadButDropsTheRun() {
    val marked = notebook.withSelectionStartedAt("h1b").withSelectionExtendedTo("c")

    val moved = marked.withHeadingBelowOrExpanded()

    assertEquals("c", moved.selectedCellId)
    assertNull(moved.anchorCellId)
    assertFalse(moved.dirty)
    assertEquals(marked.headMovesByKey + 1, moved.headMovesByKey)
  }
}
