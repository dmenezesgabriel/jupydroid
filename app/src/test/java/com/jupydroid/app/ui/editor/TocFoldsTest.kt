package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `toc:toggle-collapse` and the disclosure beside each entry, as the one piece of state they both
 * write - the ToC's own, never the notebook's.
 */
class TocFoldsTest {
  private val headings = listOf(
    TocHeading("h1", 1, "Chapter one"),
    TocHeading("h2", 2, "Part A"),
    TocHeading("h1b", 1, "Chapter two"),
  )

  @Test
  fun oneEntryFoldsAndUnfolds() {
    val folds = TocFolds()

    folds.toggle("h1")
    assertEquals(setOf("h1"), folds.collapsed)

    folds.toggle("h1")
    assertEquals(emptySet<String>(), folds.collapsed)
  }

  /**
   * `someExpanded` picks the half (`packages/toc-extension/src/index.ts:174-210`): with anything
   * still open the one control folds everything that owns something, and only then does it unfold.
   */
  @Test
  fun theOneControlFoldsEverythingAndThenUnfoldsIt() {
    val folds = TocFolds()

    folds.toggleAll(headings)
    assertEquals(setOf("h1"), folds.collapsed)

    folds.toggleAll(headings)
    assertEquals(emptySet<String>(), folds.collapsed)
  }

  /** A part-folded tree still counts as expanded, so the next tap closes the rest rather than opening. */
  @Test
  fun aPartlyFoldedTreeFoldsTheRestBeforeItUnfolds() {
    val deeper = headings + TocHeading("h2b", 2, "Part B")
    val folds = TocFolds()
    folds.toggle("h1")

    folds.toggleAll(deeper)

    assertEquals(deeper.tocSectionOwners(), folds.collapsed)
  }
}
