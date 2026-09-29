package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The table of contents as a tree, which the reference builds **at render time from the flat
 * list** rather than storing: `buildTree` walks the headings and takes as a heading's children the
 * ones that follow it until one at an equal or lower level appears
 * (`packages/toc/src/toctree.tsx:57-102`). Same rule as a heading's section in the notebook
 * ([headingSectionOf]), one rank along - and, like it, derived rather than kept.
 *
 * **Its collapse state is the ToC's own and not the notebook's.** `syncCollapseState` defaults to
 * false (`packages/toc-extension/schema/registry.json:32-69`), so folding an entry here hides rows
 * in this list and leaves `jp-MarkdownHeadingCollapsed` alone.
 */
class TocRowTest {
  /**
   * ```
   * # Chapter one      h1
   * ## Part A          h2
   * ### Detail         h3
   * ## Part B          h2b
   * # Chapter two      h1b
   * ```
   */
  private val headings = listOf(
    TocHeading("h1", 1, "Chapter one"),
    TocHeading("h2", 2, "Part A"),
    TocHeading("h3", 3, "Detail"),
    TocHeading("h2b", 2, "Part B"),
    TocHeading("h1b", 1, "Chapter two"),
  )

  // --- which rows are drawn ---------------------------------------------------------------

  @Test
  fun withNothingFoldedEveryHeadingIsARow() {
    assertEquals(
      listOf("h1", "h2", "h3", "h2b", "h1b"),
      headings.tocRows(collapsed = emptySet()).map { it.heading.cellId },
    )
  }

  /** A heading owns what follows it until an equal or lower level, so only three of the five do. */
  @Test
  fun aRowKnowsWhetherItOwnsAnything() {
    val owns = headings.tocRows(collapsed = emptySet()).filter { it.ownsSection }

    assertEquals(listOf("h1", "h2"), owns.map { it.heading.cellId })
  }

  @Test
  fun foldingAHeadingDropsEverythingUnderIt() {
    val rows = headings.tocRows(collapsed = setOf("h1")).map { it.heading.cellId }

    assertEquals(listOf("h1", "h1b"), rows)
  }

  /**
   * The reference's `localCollapsed` branch, and deriving the rows rather than marking them gives
   * it for free: unfolding an H1 over a still-folded H2 shows the H2 and leaves the H2's own
   * children hidden, because that H2 is still folded on its own account.
   */
  @Test
  fun unfoldingAParentLeavesAFoldedChildFolded() {
    val both = setOf("h1", "h2")

    assertEquals(listOf("h1", "h1b"), headings.tocRows(both).map { it.heading.cellId })
    assertEquals(
      listOf("h1", "h2", "h2b", "h1b"),
      headings.tocRows(both - "h1").map { it.heading.cellId },
    )
  }

  /** A row that is drawn says whether it is folded, which is what its chevron is pointing at. */
  @Test
  fun aDrawnRowSaysWhetherItIsFolded() {
    val rows = headings.tocRows(collapsed = setOf("h1")).associateBy { it.heading.cellId }

    assertTrue(rows.getValue("h1").collapsed)
    assertFalse(rows.getValue("h1b").collapsed)
  }

  /** An id nothing answers to is inert, which is every transform's promise in this package. */
  @Test
  fun foldingSomethingThatIsGoneChangesNothing() {
    assertEquals(
      headings.tocRows(emptySet()),
      headings.tocRows(collapsed = setOf("gone")),
    )
  }

  // --- the numbers on a row --------------------------------------------------------

  @Test
  fun aRowCarriesItsNumberWhenNumberingIsOn() {
    val rows = headings.tocRows(collapsed = emptySet(), numbering = TocNumbering(numberHeaders = true))

    assertEquals(listOf("1. ", "1.1. ", "1.1.1. ", "1.2. ", "2. "), rows.map { it.prefix })
  }

  @Test
  fun withNumberingOffARowCarriesNoNumber() {
    assertEquals(List(5) { "" }, headings.tocRows(collapsed = emptySet()).map { it.prefix })
  }

  /**
   * **Folding does not renumber.** The prefixes are the document's, computed over every heading,
   * so what a fold hides still counts - which is also why the reference computes them in the model
   * and folds in the view.
   */
  @Test
  fun aFoldedAwayHeadingStillCounts() {
    val numbering = TocNumbering(numberHeaders = true)

    val rows = headings.tocRows(collapsed = setOf("h1"), numbering = numbering)

    assertEquals(listOf("1. ", "2. "), rows.map { it.prefix })
    assertEquals(listOf("h1", "h1b"), rows.map { it.heading.cellId })
  }

  // --- toc:toggle-collapse -----------------------------------------------------------------

  /**
   * What Collapse All sets. **Only the headings that own something**, where the reference marks
   * every heading (`toggleCollapse({collapsed: true})`): a leaf's flag is inert either way, and
   * storing it would make `someExpanded` permanently true in a notebook with no nesting - so the
   * one control would collapse for ever and never expand.
   */
  @Test
  fun collapseAllTargetsTheHeadingsThatOwnSomething() {
    assertEquals(setOf("h1", "h2"), headings.tocSectionOwners())
  }

  /**
   * `someExpanded` is what picks between the two halves of the one command and what its label
   * reads from (`packages/toc-extension/src/index.ts:174-210`).
   */
  @Test
  fun someIsExpandedUntilEveryOwnerIsFolded() {
    assertTrue(headings.tocSomeExpanded(collapsed = emptySet()))
    assertTrue(headings.tocSomeExpanded(collapsed = setOf("h1")))
    assertFalse(headings.tocSomeExpanded(collapsed = setOf("h1", "h2")))
  }

  /** A notebook with no nesting has nothing to fold, so there is nothing expanded either. */
  @Test
  fun aFlatTableOfContentsHasNothingToFold() {
    val flat = listOf(TocHeading("a", 1, "One"), TocHeading("b", 1, "Two"))

    assertEquals(emptySet<String>(), flat.tocSectionOwners())
    assertFalse(flat.tocSomeExpanded(collapsed = emptySet()))
  }
}
