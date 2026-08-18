package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `getPrefix` (`packages/toc/src/utils/common.ts:157-196`), driven by `filterHeadings`' own loop
 * (`:29-60`): one running `levels` array threaded across every heading, with `previousLevel`
 * carried from the last one.
 *
 * The numbers are **the document's**, not one cell's, which is why the whole list is numbered at
 * once - the reference threads the same array across cells for exactly that reason.
 */
class TocNumberingTest {
  /**
   * ```
   * # Chapter one     h1
   * ## Part A         h2
   * ### Detail        h3
   * ## Part B         h2b
   * # Chapter two     h1b
   * ```
   */
  private val headings = listOf(
    TocHeading("h1", 1, "Chapter one"),
    TocHeading("h2", 2, "Part A"),
    TocHeading("h3", 3, "Detail"),
    TocHeading("h2b", 2, "Part B"),
    TocHeading("h1b", 1, "Chapter two"),
  )

  /** `numberHeaders` is false in the schema (`toc-extension/schema/registry.json:32-69`). */
  @Test
  fun nothingIsNumberedUntilItIsAskedFor() {
    assertEquals(
      listOf("", "", "", "", ""),
      headings.tocPrefixes(TocNumbering()),
    )
  }

  /**
   * Descending a level opens a new counter at 1; returning to one increments it and **drops the
   * deeper ones** - `levels.splice(level)`.
   */
  @Test
  fun eachLevelCountsWithinTheOneAboveIt() {
    assertEquals(
      listOf("1. ", "1.1. ", "1.1.1. ", "1.2. ", "2. "),
      headings.tocPrefixes(TocNumbering(numberHeaders = true)),
    )
  }

  /**
   * With `numberingH1` off the top level is not numbered and is not shown in the ones below it
   * either - `levels.slice(1)`, and an empty prefix while only one level is open. The counter for
   * the second level still restarts under each H1, because `levels` keeps counting.
   */
  @Test
  fun theTopLevelCanBeLeftOutOfTheNumbering() {
    assertEquals(
      listOf("", "1. ", "1.1. ", "2. ", ""),
      headings.tocPrefixes(TocNumbering(numberHeaders = true, numberingH1 = false)),
    )
  }

  /**
   * *"If the header list skips some level, replace missing elements by 0"* - the reference's own
   * comment, and its own arithmetic: an H1 followed by an H3 numbers the H3 `1.0.1.`
   */
  @Test
  fun askippedLevelIsCountedAsZeroRatherThanClosedUp() {
    val skipped = listOf(TocHeading("a", 1, "Chapter"), TocHeading("b", 3, "Detail"))

    assertEquals(listOf("1. ", "1.0.1. "), skipped.tocPrefixes(TocNumbering(numberHeaders = true)))
  }

  /** A notebook whose first heading is an H2 has nothing at the level above it, which counts as 0. */
  @Test
  fun aDocumentThatStartsBelowTheTopLevelCountsTheMissingOneAsZero() {
    val deep = listOf(TocHeading("a", 2, "Part"), TocHeading("b", 2, "Part two"))

    assertEquals(listOf("0.1. ", "0.2. "), deep.tocPrefixes(TocNumbering(numberHeaders = true)))
  }

  /** Every heading gets an answer, so a caller can zip the two lists without checking. */
  @Test
  fun thereIsOnePrefixPerHeadingWhicheverWayItIsAsked() {
    assertEquals(headings.size, headings.tocPrefixes(TocNumbering(numberHeaders = true)).size)
    assertEquals(0, emptyList<TocHeading>().tocPrefixes(TocNumbering(numberHeaders = true)).size)
  }
}
