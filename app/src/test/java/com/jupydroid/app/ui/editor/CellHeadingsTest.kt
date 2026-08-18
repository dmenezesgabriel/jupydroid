package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A heading owns a **section** of the document, and what a collapsed one hides is derived from
 * that rather than stored.
 *
 * Every rule is read out of `packages/notebook/src/actions.tsx` and
 * `packages/notebook/src/widget.ts`; the tests name the line.
 */
class CellHeadingsTest {
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

  // --- the level a cell is --------------------------------------------------------------

  /**
   * `getHeadingInfo` answers for markdown alone; a code or raw cell gets `headingLevel: 7`
   * (`actions.tsx:2481-2493`), a number no section boundary can ever be. Null says the same here
   * without inviting anyone to compare it.
   */
  @Test
  fun onlyAMarkdownCellHasAHeadingLevel() {
    assertEquals(1, md("h", "# Chapter").headingLevel)
    assertNull(Cell("c", CellKind.CODE, "# not markdown").headingLevel)
    assertNull(Cell("r", CellKind.RAW, "# not markdown").headingLevel)
    assertNull(md("p", "just prose").headingLevel)
  }

  // --- the section a heading owns -------------------------------------------------------

  /**
   * `setHeadingCollapse` walks forward from the heading and stops at the first heading of **equal
   * or higher** level (`actions.tsx:2374-2405`), so an H1's section swallows the H2 under it.
   */
  @Test
  fun aSectionRunsToTheNextHeadingOfEqualOrHigherLevel() {
    assertEquals(listOf("a", "h2", "b"), notebook.headingSectionOf("h1"))
    assertEquals(listOf("b"), notebook.headingSectionOf("h2"))
  }

  /** The last section runs to the end of the notebook - the loop's `cellNum === length` branch. */
  @Test
  fun theLastSectionRunsToTheEndOfTheNotebook() {
    assertEquals(listOf("c"), notebook.headingSectionOf("h1b"))
  }

  /** A cell that is not a heading owns nothing: `setHeadingCollapse` returns before its loop. */
  @Test
  fun aCellThatIsNotAHeadingOwnsNoSection() {
    assertEquals(emptyList<String>(), notebook.headingSectionOf("a"))
    assertEquals(emptyList<String>(), notebook.headingSectionOf("gone"))
  }

  /** A heading with nothing under it before the next one owns an empty section, not a null. */
  @Test
  fun aHeadingImmediatelyFollowedByAnotherOwnsNothing() {
    val backToBack = EditorUiState(cells = listOf(md("h1", "# One"), md("h2", "# Two")), loaded = true)

    assertEquals(emptyList<String>(), backToBack.headingSectionOf("h1"))
  }

  // --- what a collapsed heading hides ----------------------------------------------------

  /** Nothing is hidden while nothing is collapsed, whatever the headings say. */
  @Test
  fun aNotebookWithNoCollapsedHeadingHidesNothing() {
    assertEquals(emptySet<String>(), notebook.hiddenCellIds)
  }

  @Test
  fun aCollapsedHeadingHidesItsSectionAndNotItself() {
    val collapsed = notebook.withHeadingCollapsed("h1", collapsed = true)

    assertEquals(setOf("a", "h2", "b"), collapsed.hiddenCellIds)
    assertTrue("the heading itself stays on screen", "h1" !in collapsed.hiddenCellIds)
  }

  /**
   * **Expanding respects a locally collapsed sub-heading.** `setHeadingCollapse`'s `localCollapsed`
   * branch leaves a collapsed H2 visible with its own children still hidden when the H1 above it is
   * expanded (`actions.tsx:2400-2420`). Deriving the hidden set as the union of every collapsed
   * heading's section gives that for free.
   */
  @Test
  fun expandingAHeadingLeavesACollapsedSubHeadingCollapsed() {
    val both = notebook
      .withHeadingCollapsed("h1", collapsed = true)
      .withHeadingCollapsed("h2", collapsed = true)

    assertEquals(setOf("a", "h2", "b"), both.hiddenCellIds)
    assertEquals(setOf("b"), both.withHeadingCollapsed("h1", collapsed = false).hiddenCellIds)
  }

  /**
   * Hidden-ness is recomputed from the order, never carried: `_refreshCollapsedHeadingVisibility`
   * is *"Recompute hidden cells from the current collapsed heading structure"*
   * (`widget.ts:916-940`), and `moveCells` calls it after reordering (`:462`). Here that is a
   * derivation rather than a re-run, so a move cannot leave a stale flag behind.
   */
  @Test
  fun movingACellOutOfACollapsedSectionShowsItAgain() {
    val collapsed = notebook.withHeadingCollapsed("h1", collapsed = true)

    val moved = collapsed.withCellsMoved(listOf("b"), CellMove.DOWN).withCellsMoved(listOf("b"), CellMove.DOWN)

    assertEquals(setOf("a", "h2"), moved.hiddenCellIds)
  }

  /** Collapsing is persisted, so it is an edit the notebook has to be written back for. */
  @Test
  fun collapsingAHeadingDirtiesTheNotebook() {
    assertTrue(notebook.withHeadingCollapsed("h1", collapsed = true).dirty)
  }

  /** A stale id is a no-op, which is every transform's promise in this package. */
  @Test
  fun collapsingACellThatIsGoneChangesNothing() {
    assertEquals(notebook, notebook.withHeadingCollapsed("gone", collapsed = true))
  }

  // --- the marked run, which must not end up on a cell nothing draws ----------------------

  /**
   * The reference never faces this: `toggleCurrentHeadingCollapse` acts on the *active* cell
   * (`actions.tsx:2440-2455`), so the only cell that matters is the heading, and it stays visible.
   * Here a heading inside a marked run can swallow the head - and a head nothing draws is one the
   * ranged runs would still compute their range from.
   */
  @Test
  fun collapsingAHeadingOverTheHeadPutsTheHeadOnTheHeading() {
    val marked = notebook.copy(anchorCellId = "h1", selectedCellId = "b")

    val collapsed = marked.withHeadingCollapsed("h1", collapsed = true)

    assertEquals("h1", collapsed.selectedCellId)
    assertNull(collapsed.anchorCellId)
  }

  /** A run whose ends are both on screen keeps them, and keeps being a run. */
  @Test
  fun collapsingAHeadingBelowTheRunLeavesItAlone() {
    val marked = notebook.copy(anchorCellId = "h1", selectedCellId = "h1b")

    val collapsed = marked.withHeadingCollapsed("h1b", collapsed = true)

    assertEquals("h1b", collapsed.selectedCellId)
    assertEquals("h1", collapsed.anchorCellId)
  }

  /**
   * `setHeadingCollapse` bails unless the cell *is* a markdown heading - *"otherwise collapsing and
   * uncollapsing already hidden stuff can cause some funny looking bugs"* (`actions.tsx:2364-2372`).
   */
  @Test
  fun aCellThatIsNotAHeadingCannotBeCollapsed() {
    assertEquals(notebook, notebook.withHeadingCollapsed("a", collapsed = true))
  }
}
