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
 * The two strengths of reveal, and the line between them.
 *
 * `expandParent` on `activeCellChanged` is what *any* jump gets
 * (`packages/notebook-extension/src/index.ts:2871-2873`); the collapsed source and the rendered
 * prose are undone only by landing on a **match** (`packages/notebook/src/searchprovider.ts:630-634`
 * and `packages/cells/src/searchprovider.ts:380-410`).
 *
 * The reference is emphatic about the line: a table-of-contents jump forces command mode first,
 * *"to avoid entering Markdown cell in edit mode"* (`packages/notebook/src/toc.ts:681-684`).
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

  /**
   * **An ordinary reveal leaves prose rendered**, which is the whole of the line between the two:
   * a jump from the table of contents is somebody asking to *read* a heading, and the reference
   * forces command mode first to make sure it stays that way (`toc.ts:681-684`). One helper for
   * both put `## Part A` on screen as an editor, green against every assertion
   * (`evidence/p2i-4b-toc-reveals/`).
   */
  @Test
  fun anOrdinaryRevealLeavesProseAsProse() {
    val revealed = notebook(heading("h", "# Chapter", collapsed = true), heading("m", "## Part"))
      .withCellRevealed("m")

    assertEquals(emptySet<String>(), revealed.markdownBeingEdited)
  }

  /** And leaves a collapsed source collapsed, for the same reason - that is `activateNewMatch`'s. */
  @Test
  fun anOrdinaryRevealLeavesACollapsedSourceCollapsed() {
    val revealed = notebook(code("a", sourceHidden = true)).withCellRevealed("a")

    assertTrue(revealed.cells.single().sourceHidden)
  }

  /** `if (activeCell.inputHidden) activeCell.inputHidden = false` - a match, and only a match. */
  @Test
  fun landingOnAMatchOpensACollapsedSource() {
    val revealed = notebook(code("a", sourceHidden = true)).withMatchRevealed("a")

    assertFalse(revealed.cells.single().sourceHidden)
    assertTrue("a persisted flag changed, so the notebook has to be written", revealed.dirty)
  }

  /**
   * `MarkdownCellSearchProvider` sets `cell.rendered = false` to show the match in the source it
   * found it in - `_unrenderedByHighlight`.
   */
  @Test
  fun landingOnAMatchOpensProseToItsSource() {
    val revealed = notebook(heading("m", "# Motion")).withMatchRevealed("m")

    assertEquals(setOf("m"), revealed.markdownBeingEdited)
  }

  /**
   * **And it does not take the caret**, which is P2e-3's lesson: a command that unrenders is not a
   * tap asking to type, and a soft keyboard over a notebook nobody is writing in was the cost of
   * conflating them (`docs/DECISIONS.md`).
   */
  @Test
  fun openingProseDoesNotAskForTheKeyboard() {
    assertNull(notebook(heading("m", "# Motion")).withMatchRevealed("m").markdownTakingCaret)
  }

  /** Unrendering is not persisted, so it alone must not mark the document dirty. */
  @Test
  fun openingProseAloneDoesNotDirtyTheNotebook() {
    assertFalse(notebook(heading("m", "# Motion")).withMatchRevealed("m").dirty)
  }

  /** A match keeps the parent-expanding half too - it is [withCellRevealed] plus two, not instead. */
  @Test
  fun landingOnAMatchAlsoExpandsWhatIsAboveIt() {
    val state = notebook(
      heading("h", "# Chapter", collapsed = true),
      Cell("m", CellKind.MARKDOWN, "some *prose*", sourceHidden = true),
    )

    val revealed = state.withMatchRevealed("m")

    assertEquals(listOf("h", "m"), revealed.cellsOnScreen.map { it.id })
    assertFalse(revealed.cells.single { it.id == "m" }.sourceHidden)
    assertEquals(setOf("m"), revealed.markdownBeingEdited)
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

}
