package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which cell the notebook has to bring on screen because it is about to be typed into.
 *
 * `_ensureFocus` is the reference's rule and it is three conditions, not one: **edit mode**, an
 * active cell whose **editor does not already have focus**, and that cell **not already in the
 * viewport** (`packages/notebook/src/widget.ts:2885-2905`). The last is the call site's, since only
 * the list knows it; the first two are here.
 */
class CellScrollTargetTest {
  @Test
  fun aCellBeingTypedIntoIsTheOneToShow() {
    assertEquals(2, cellBeingEditedIndex(CellMode.EDIT, "c3", threeCells()))
  }

  /**
   * **Command mode scrolls nothing**, which is the reference's outermost guard. Stepping the
   * selection with the notebook bar is not a request to type, and scrolling on it would yank the
   * document out from under someone reading it.
   */
  @Test
  fun commandModeScrollsNothing() {
    assertNull(cellBeingEditedIndex(CellMode.COMMAND, "c3", threeCells()))
  }

  @Test
  fun aNotebookWithNoActiveCellScrollsNothing() {
    assertNull(cellBeingEditedIndex(CellMode.EDIT, null, threeCells()))
  }

  /** A stale id is a no-op, as everywhere in this package. */
  @Test
  fun anIdThatNamesNoCellScrollsNothing() {
    assertNull(cellBeingEditedIndex(CellMode.EDIT, "gone", threeCells()))
  }

  /**
   * **Counted against what is drawn, not against the document.** A cell inside a collapsed section
   * is not a row of the list, so an index into `cells` would scroll to the wrong one - which is the
   * same reason the search and the heading jump both read [cellsOnScreen].
   */
  @Test
  fun theIndexIsIntoWhatIsDrawn() {
    val withHiddenSection = listOf(
      Cell("h1", CellKind.MARKDOWN, "# One", headingCollapsed = true),
      Cell("c2", CellKind.CODE, "hidden = 1"),
      Cell("h3", CellKind.MARKDOWN, "# Two"),
      Cell("c4", CellKind.CODE, "shown = 1"),
    )
    val state = EditorUiState(cells = withHiddenSection)

    // h1, h3, c4 are drawn; c2 is folded away, so c4 is row 2 and not row 3.
    assertEquals(2, cellBeingEditedIndex(CellMode.EDIT, "c4", state.cellsOnScreen))
  }

  private fun threeCells(): List<Cell> = listOf(
    Cell("c1", CellKind.CODE, "one = 1"),
    Cell("c2", CellKind.CODE, "two = 2"),
    Cell("c3", CellKind.MARKDOWN, "## "),
  )
}
