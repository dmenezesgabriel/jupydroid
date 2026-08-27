package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.MatchedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A match found by the **workspace** search, landed on in the notebook it names.
 *
 * `searchView.open` builds a selection from the match and hands it to `openEditor`
 * (`searchView.ts:2240-2256`); for a notebook it then re-finds the match in the widget it opened
 * (`:2283-2295`, `updateMatchesForEditorWidget`) rather than trusting the offsets the walk
 * reported. This is that second step: the walk names a cell by index, and the notebook the editor
 * loaded is what the offsets are resolved against.
 */
class MatchLandingTest {
  private fun code(id: String, source: String) = Cell(id, CellKind.CODE, source)

  private fun notebook(vararg cells: Cell) = EditorUiState(cells = cells.toList(), loaded = true)

  private val state = notebook(code("a", "import os"), code("b", "print(cat)"))

  /** The cell is found by **index**, which is the only thing a walk and an editor agree on. */
  @Test
  fun aMatchResolvesToTheCellItsIndexNames() {
    val match = MatchedLine(line = 1, text = "print(cat)", range = 6..8, cell = 1)

    assertEquals(SearchHit("b", 6..8), state.hitFor(match))
  }

  /** A later line inside the cell is offset by the lines above it **in that cell**. */
  @Test
  fun aLaterLineInTheCellIsOffsetByWhatIsAboveItThere() {
    val notebook = notebook(code("a", "import os"), code("b", "print(os)\nprint(cat)"))
    val match = MatchedLine(line = 2, text = "print(cat)", range = 6..8, cell = 1)

    assertEquals(SearchHit("b", 16..18), notebook.hitFor(match))
  }

  /**
   * **A match with no cell is not a match in this notebook.** A `.py` opened from the panel goes to
   * the file editor, so a cell-less match arriving here means the two disagree about what the file
   * is - landing on cell 0 would be a guess.
   */
  @Test
  fun aMatchThatNamesNoCellLandsNowhere() {
    assertNull(state.hitFor(MatchedLine(line = 1, text = "print(cat)", range = 6..8)))
  }

  /** A notebook that lost the cell between the walk and the tap lands nowhere. */
  @Test
  fun aCellTheNotebookNoLongerHasLandsNowhere() {
    assertNull(state.hitFor(MatchedLine(line = 1, text = "print(cat)", range = 6..8, cell = 9)))
  }

  /**
   * A cell rewritten under the match lands nowhere rather than on whatever now sits there -
   * `MatchedLine.rangeIn`'s own refusal, which the reference makes by throwing *"has changed in
   * the meantime"* on a version mismatch (`replaceService.ts:213-216`).
   */
  @Test
  fun aCellRewrittenUnderTheMatchLandsNowhere() {
    val match = MatchedLine(line = 2, text = "print(cat)", range = 6..8, cell = 1)

    assertNull(state.hitFor(match))
  }

  /**
   * **Landing reveals the cell and selects it**, which is `withMatchRevealed` plus the head moving
   * onto the match - the same pair `withSearchStepped` applies for the find bar's arrows.
   */
  @Test
  fun landingRevealsTheCellUnderACollapsedHeadingAndSelectsIt() {
    val state = notebook(
      Cell("h", CellKind.MARKDOWN, "# Part A", headingCollapsed = true),
      code("b", "print(cat)"),
    )

    val landed = state.landedOn(MatchedLine(line = 1, text = "print(cat)", range = 6..8, cell = 1))

    assertFalse(landed.cells.first().headingCollapsed)
    assertEquals("b", landed.selectedCellId)
    assertTrue(landed.cellsOnScreen.any { it.id == "b" })
  }

  /** A match in a markdown cell unrenders its prose, because that is where the characters are. */
  @Test
  fun landingOnProseOpensItsSource() {
    val state = notebook(Cell("m", CellKind.MARKDOWN, "the cat"))

    val landed = state.landedOn(MatchedLine(line = 1, text = "the cat", range = 4..6, cell = 0))

    assertTrue("m" in landed.markdownBeingEdited)
  }

  /**
   * **A match this notebook no longer holds changes nothing at all**, not even the selection: the
   * unchanged return is what keeps reading a notebook from rewriting it, exactly as it is in
   * `withCellRevealed`.
   */
  @Test
  fun aStaleMatchLeavesTheNotebookExactlyAsItWas() {
    val landed = state.landedOn(MatchedLine(line = 9, text = "gone", range = 0..3, cell = 1))

    assertEquals(state, landed)
  }
}
