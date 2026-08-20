package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `activateNewMatch` (`packages/notebook/src/searchprovider.ts:604-650`) plus the two reveals it
 * gets for free from setting `activeCellIndex`.
 *
 * Three different collapses can be hiding a cell here, and the reference undoes all three:
 * `inputHidden` in `activateNewMatch` itself; a rendered markdown cell in
 * `MarkdownCellSearchProvider` (`packages/cells/src/searchprovider.ts:380-410`); and a collapsed
 * parent heading through `NotebookActions.expandParent`, which is wired to `activeCellChanged`
 * (`packages/notebook-extension/src/index.ts:2871-2873`).
 */
class CellRevealTest {
  private fun code(id: String, source: String = "x = 1", sourceHidden: Boolean = false) =
    Cell(id, CellKind.CODE, source, sourceHidden = sourceHidden)

  private fun heading(id: String, source: String, collapsed: Boolean = false) =
    Cell(id, CellKind.MARKDOWN, source, headingCollapsed = collapsed)

  private fun notebook(vararg cells: Cell) = EditorUiState(cells = cells.toList(), loaded = true)

  /**
   * **Returns `this` when there was nothing to reveal**, which is load-bearing rather than an
   * optimisation: stepping through matches in an ordinary notebook would otherwise mark it dirty
   * and rewrite the file for having been read.
   */
  @Test
  fun aCellNothingIsHidingIsLeftExactlyAsItWas() {
    val state = notebook(code("a"), code("b"))

    assertSame(state, state.withCellRevealed("b"))
  }

  @Test
  fun aStaleIdIsANoOpLikeEverywhereElseInThisPackage() {
    val state = notebook(code("a"))

    assertSame(state, state.withCellRevealed("gone"))
  }

  /** `if (activeCell.inputHidden) activeCell.inputHidden = false`. */
  @Test
  fun aCollapsedSourceIsOpened() {
    val revealed = notebook(code("a", sourceHidden = true)).withCellRevealed("a")

    assertFalse(revealed.cells.single().sourceHidden)
    assertTrue("a persisted flag changed, so the notebook has to be written", revealed.dirty)
  }

  /**
   * `MarkdownCellSearchProvider` sets `cell.rendered = false` to show the match in the source it
   * found it in - `_unrenderedByHighlight`.
   */
  @Test
  fun aRenderedMarkdownCellIsOpenedToItsSource() {
    val revealed = notebook(heading("m", "# Motion")).withCellRevealed("m")

    assertEquals(setOf("m"), revealed.markdownBeingEdited)
  }

  /**
   * **And it does not take the caret**, which is P2e-3's lesson: a command that unrenders is not a
   * tap asking to type, and a soft keyboard over a notebook nobody is writing in was the cost of
   * conflating them (`docs/DECISIONS.md`).
   */
  @Test
  fun openingProseDoesNotAskForTheKeyboard() {
    assertNull(notebook(heading("m", "# Motion")).withCellRevealed("m").markdownTakingCaret)
  }

  /** Unrendering is not persisted, so it alone must not mark the document dirty. */
  @Test
  fun openingProseAloneDoesNotDirtyTheNotebook() {
    assertFalse(notebook(heading("m", "# Motion")).withCellRevealed("m").dirty)
  }

  /** `expandParent`: the nearest parent heading, uncollapsed. */
  @Test
  fun aCollapsedHeadingAboveIsExpanded() {
    val state = notebook(heading("h", "# Chapter", collapsed = true), code("under"))
    // The premise: this cell really is hidden.
    assertEquals(listOf("h"), state.cellsOnScreen.map { it.id })

    val revealed = state.withCellRevealed("under")

    assertEquals(listOf("h", "under"), revealed.cellsOnScreen.map { it.id })
    assertTrue(revealed.dirty)
  }

  /**
   * `expandParent` recurses through a hidden parent before uncollapsing it (`actions.tsx:2296-2303`),
   * so a cell two collapses deep comes all the way out.
   */
  @Test
  fun everyCollapseHidingTheCellGivesWay() {
    val state = notebook(
      heading("h1", "# Chapter", collapsed = true),
      heading("h2", "## Part", collapsed = true),
      code("under"),
    )

    val revealed = state.withCellRevealed("under")

    assertEquals(listOf("h1", "h2", "under"), revealed.cellsOnScreen.map { it.id })
  }

  /**
   * And only those - a collapsed heading somewhere else in the document is not touched, which is
   * `expandParent` walking *up* from the cell rather than over the notebook.
   */
  @Test
  fun aCollapsedHeadingHidingSomethingElseIsLeftAlone() {
    val state = notebook(
      heading("h1", "# Chapter", collapsed = true),
      code("under"),
      heading("h2", "# Other", collapsed = true),
      code("elsewhere"),
    )

    val revealed = state.withCellRevealed("under")

    assertTrue(revealed.cells.single { it.id == "h2" }.headingCollapsed)
    assertEquals(listOf("h1", "under", "h2"), revealed.cellsOnScreen.map { it.id })
  }

  /** All three at once, since nothing says a hidden cell has only one thing hiding it. */
  @Test
  fun theThreeCollapsesComeOffTogether() {
    val state = notebook(
      heading("h", "# Chapter", collapsed = true),
      Cell("m", CellKind.MARKDOWN, "some *prose*", sourceHidden = true),
    )

    val revealed = state.withCellRevealed("m")

    assertEquals(listOf("h", "m"), revealed.cellsOnScreen.map { it.id })
    assertFalse(revealed.cells.single { it.id == "m" }.sourceHidden)
    assertEquals(setOf("m"), revealed.markdownBeingEdited)
  }
}
