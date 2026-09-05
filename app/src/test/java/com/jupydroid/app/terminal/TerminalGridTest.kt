package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two buffer-level tests of
 * `terminal-emulator/src/test/java/com/termux/terminal/ScreenBufferTest.java` (termux-app
 * `3df69d1d`, Apache 2.0 - see [TerminalGrid]), plus the ring, which upstream only ever covers
 * through the emulator - and the emulator is P4f.
 *
 * `ScreenBufferTest`'s other three tests drive `enterString`, so they go with P4f.
 */
class TerminalGridTest {
  private val plain = CellStyle(0L)

  @Test
  fun writingCellsAndReadingTheTranscriptBack() {
    val grid = TerminalGrid(columns = 5, totalRows = 3, screenRows = 3)
    assertEquals("", grid.transcriptText)
    grid.setChar(0, 0, 'a'.code, plain)
    assertEquals("a", grid.transcriptText)
    grid.setChar(0, 0, 'b'.code, plain)
    assertEquals("b", grid.transcriptText)
    grid.setChar(2, 0, 'c'.code, plain)
    assertEquals("b c", grid.transcriptText)
    grid.setChar(2, 2, 'f'.code, plain)
    assertEquals("b c\n\n  f", grid.transcriptText)
  }

  @Test
  fun blockSetFillsARectangleAndLeavesTheRestAlone() {
    val grid = TerminalGrid(columns = 5, totalRows = 3, screenRows = 3)
    grid.blockSet(0, 0, 2, 2, 'X'.code, plain)
    assertEquals("XX\nXX", grid.transcriptText)
    grid.blockSet(1, 1, 2, 2, 'Y'.code, plain)
    assertEquals("XX\nXYY\n YY", grid.transcriptText)
  }

  @Test
  fun aNewGridIsBlankAndHasNoScrollback() {
    val grid = TerminalGrid(columns = 5, totalRows = 6, screenRows = 3)
    assertEquals(0, grid.activeTranscriptRows)
    assertEquals(3, grid.activeRows)
    for (row in 0 until 3) assertTrue("row $row", grid.rowAt(row).isBlank())
  }

  @Test
  fun scrollingPushesTheTopRowIntoTheScrollbackAndBlanksTheBottom() {
    val grid = gridWithRows("one", "two", "three")
    grid.scrollDownOneLine(0, 3, CellStyle.NORMAL)

    assertEquals(1, grid.activeTranscriptRows)
    assertEquals(4, grid.activeRows)
    assertEquals("one", rowText(grid, -1))
    assertEquals("two", rowText(grid, 0))
    assertEquals("three", rowText(grid, 1))
    assertTrue("the revealed row is blank", grid.rowAt(2).isBlank())
  }

  @Test
  fun theScrollbackStopsGrowingAtTheRingsCapacity() {
    val grid = TerminalGrid(columns = 5, totalRows = 6, screenRows = 3)
    repeat(20) { grid.scrollDownOneLine(0, 3, CellStyle.NORMAL) }
    assertEquals("totalRows - screenRows", 3, grid.activeTranscriptRows)
    assertEquals(6, grid.activeRows)
  }

  /** What has scrolled off is still readable, which is what makes a scrollback a scrollback. */
  @Test
  fun theTranscriptReadsTheScrolledOffRowsBeforeTheScreen() {
    val grid = gridWithRows("one", "two", "three")
    grid.scrollDownOneLine(0, 3, CellStyle.NORMAL)
    grid.setChar(0, 2, 'z'.code, plain)
    assertEquals("one\ntwo\nthree\nz", grid.transcriptText)
  }

  @Test
  fun clearingTheTranscriptKeepsTheScreen() {
    val grid = gridWithRows("one", "two", "three")
    grid.scrollDownOneLine(0, 3, CellStyle.NORMAL)
    grid.clearTranscript()
    assertEquals(0, grid.activeTranscriptRows)
    assertEquals("two\nthree", grid.transcriptText)
  }

  /** A scroll region leaves the rows outside it where they are on screen. */
  @Test
  fun scrollingARegionMovesOnlyTheRowsInsideIt() {
    val grid = gridWithRows("one", "two", "three", "four")
    grid.scrollDownOneLine(1, 3, CellStyle.NORMAL)
    assertEquals("one", rowText(grid, 0))
    assertEquals("three", rowText(grid, 1))
    assertTrue("the revealed row is blank", grid.rowAt(2).isBlank())
    assertEquals("four", rowText(grid, 3))
  }

  @Test
  fun copyingABlockUpwardsDoesNotReadWhatItHasAlreadyWritten() {
    val grid = gridWithRows("aaa", "bbb", "ccc")
    grid.blockCopy(0, 1, 3, 2, 0, 0)
    assertEquals("bbb", rowText(grid, 0))
    assertEquals("ccc", rowText(grid, 1))
  }

  @Test
  fun copyingABlockDownwardsDoesNotReadWhatItHasAlreadyWritten() {
    val grid = gridWithRows("aaa", "bbb", "ccc")
    grid.blockCopy(0, 0, 3, 2, 0, 1)
    assertEquals("aaa", rowText(grid, 1))
    assertEquals("bbb", rowText(grid, 2))
  }

  @Test
  fun copyingNoColumnsIsNothingRatherThanAnError() {
    val grid = gridWithRows("aaa", "bbb", "ccc")
    grid.blockCopy(0, 0, 0, 99, 0, 99)
    assertEquals("aaa", rowText(grid, 0))
  }

  /** A wrapped row and its continuation are one logical line, so no newline separates them. */
  @Test
  fun aWrappedRowJoinsWithTheOneBelowIt() {
    val grid = gridWithRows("ABCDE", "FG")
    grid.rowAt(0).lineWrap = true
    assertEquals("ABCDEFG", grid.selectedText(0, 0, 1, 1))
    assertEquals("ABCDE\nFG", grid.selectedText(0, 0, 1, 1, joinBackLines = false))
  }

  /** Without the wrap flag, a row that merely fills its width joins only when asked to. */
  @Test
  fun aFullRowJoinsOnlyUnderJoinFullLines() {
    val grid = gridWithRows("ABCDE", "FG")
    assertEquals("ABCDE\nFG", grid.selectedText(0, 0, 1, 1))
    assertEquals("ABCDEFG", grid.selectedText(0, 0, 1, 1, joinFullLines = true))
  }

  @Test
  fun aSelectionInsideOneRowTakesOnlyTheColumnsAsked() {
    val grid = gridWithRows("ABCDE", "FGHIJ")
    assertEquals("AB", grid.selectedText(0, 0, 1, 0))
    assertEquals("CDE", grid.selectedText(2, 0, 4, 0))
    assertEquals("HIJ", grid.selectedText(2, 1, 4, 1))
  }

  /**
   * A selection landing wholly inside one wide character still yields it - both column ends map
   * to the same char index, and the reference reaches one column further to break the tie.
   */
  @Test
  fun aSelectionOfOneHalfOfAWideCharacterYieldsTheWholeCharacter() {
    val grid = TerminalGrid(columns = 5, totalRows = 3, screenRows = 3)
    grid.setChar(0, 0, 'a'.code, plain)
    grid.setChar(1, 0, WIDE, plain)
    assertEquals(String(Character.toChars(WIDE)), grid.selectedText(1, 0, 1, 0))
    assertEquals(String(Character.toChars(WIDE)), grid.selectedText(2, 0, 2, 0))
  }

  /**
   * A selection *ending* on a wide character's first half drops it, which is surprising enough to
   * pin: columns 0..1 over `a` then a wide character at 1-2 gives `a` alone, because column 2
   * maps to the wide character's own start and the span closes before it.
   */
  @Test
  fun aSelectionEndingOnAWideCharactersFirstHalfDropsIt() {
    val grid = TerminalGrid(columns = 5, totalRows = 3, screenRows = 3)
    grid.setChar(0, 0, 'a'.code, plain)
    grid.setChar(1, 0, WIDE, plain)
    assertEquals("a", grid.selectedText(0, 0, 1, 0))
    assertEquals("a" + String(Character.toChars(WIDE)), grid.selectedText(0, 0, 2, 0))
  }

  @Test
  fun aRowOutsideTheScreenAndItsScrollbackIsRefusedWithItsOwnValue() {
    val grid = TerminalGrid(columns = 5, totalRows = 6, screenRows = 3)
    val failure = runCatching { grid.rowAt(-1) }.exceptionOrNull()
    assertTrue("expected IllegalArgumentException, got $failure", failure is IllegalArgumentException)
    assertTrue("message must name the row: ${failure?.message}", failure?.message?.contains("-1") == true)
  }

  @Test(expected = IllegalArgumentException::class)
  fun aCellOutsideTheScreenIsRefused() {
    TerminalGrid(columns = 5, totalRows = 6, screenRows = 3).setChar(5, 0, 'a'.code, plain)
  }

  @Test(expected = IllegalArgumentException::class)
  fun anInvertedScrollRegionIsRefused() {
    TerminalGrid(columns = 5, totalRows = 6, screenRows = 3).scrollDownOneLine(2, 2, CellStyle.NORMAL)
  }

  @Test(expected = IllegalArgumentException::class)
  fun aBlockCopyReachingPastTheScreenIsRefused() {
    TerminalGrid(columns = 5, totalRows = 6, screenRows = 3).blockCopy(0, 0, 3, 4, 0, 0)
  }

  /**
   * A shrink first tries to absorb blank rows below the cursor rather than filing anything into
   * history - `TerminalBuffer.resize`'s own shrink loop (`:210-218`). Here the whole shift is
   * absorbed, so nothing moves into the scrollback and the cursor's row is unchanged.
   */
  @Test
  fun shrinkingAbsorbsBlankRowsBelowTheCursorInsteadOfFilingHistory() {
    val grid = gridWithRows("hello")
    val cursor = grid.resize(
      newColumns = 5,
      newScreenRows = 2,
      newTotalRows = 8,
      cursorRow = 0,
      cursorColumn = 0,
      style = plain,
    )
    assertEquals(0, grid.activeTranscriptRows)
    assertEquals("hello", rowText(grid, 0))
    assertTrue("the revealed row is blank", grid.rowAt(1).isBlank())
    assertEquals(0, cursor.row)
  }

  /**
   * A shrink the cursor sits inside pushes the rows it cuts off into history rather than
   * absorbing them, even though they are blank - `cursor[1] >= i` breaks the loop before it ever
   * looks at whether a row is blank (`:212`).
   */
  @Test
  fun shrinkingWithTheCursorOnTheCutRowFilesItIntoHistoryRatherThanAbsorbingIt() {
    val grid = gridWithRows("one", "two", "three")
    val cursor = grid.resize(
      newColumns = 5,
      newScreenRows = 2,
      newTotalRows = 8,
      cursorRow = 2,
      cursorColumn = 0,
      style = plain,
    )
    assertEquals(1, grid.activeTranscriptRows)
    assertEquals("one", rowText(grid, -1))
    assertEquals("two", rowText(grid, 0))
    assertEquals("three", rowText(grid, 1))
    assertEquals(1, cursor.row)
  }

  /** A grow pulls rows back out of the transcript before it blanks anything. */
  @Test
  fun growingInsideExistingHistoryPullsRowsBackOntoScreenRatherThanBlankingThem() {
    val grid = gridWithRows("one", "two", "three")
    grid.scrollDownOneLine(0, 3, CellStyle.NORMAL) // "one" -> history; screen: two, three, blank
    val cursor = grid.resize(
      newColumns = 5,
      newScreenRows = 4,
      newTotalRows = 8,
      cursorRow = 1,
      cursorColumn = 0,
      style = plain,
    )
    assertEquals(0, grid.activeTranscriptRows)
    assertEquals("one", rowText(grid, 0))
    assertEquals("two", rowText(grid, 1))
    assertEquals("three", rowText(grid, 2))
    assertTrue("the fourth row is newly revealed and blank", grid.rowAt(3).isBlank())
    assertEquals(2, cursor.row)
  }

  /**
   * A grow past what the transcript can supply blanks the rows it can't pull back, in the style
   * passed to [TerminalGrid.resize] rather than whatever the row held before it scrolled off.
   */
  @Test
  fun growingPastTheTranscriptBlanksWhatItCannotSupplyInTheCurrentStyle() {
    val grid = TerminalGrid(columns = 5, totalRows = 8, screenRows = 2)
    val current = CellStyle.encode(foreColor = 9, backColor = 0, effect = 0)
    val cursor = grid.resize(
      newColumns = 5,
      newScreenRows = 5,
      newTotalRows = 8,
      cursorRow = 0,
      cursorColumn = 0,
      style = current,
    )
    assertEquals(0, grid.activeTranscriptRows)
    for (row in 0 until 5) assertTrue("row $row", grid.rowAt(row).isBlank())
    assertEquals(current, grid.styleAt(4, 0))
    assertEquals(0, cursor.row)
  }

  /**
   * A shrink that carries the cursor's row above the new screen resets it to `0` rather than
   * leaving it negative - `TerminalBuffer.resize`'s own common tail (`:352-353`), shared by both
   * its paths. A cursor already above the shrunk screen (row 0, screen down to 1 row) is exactly
   * that case: nothing below it can absorb the shift, so it lands at `-1` before the reset.
   */
  @Test
  fun aCursorTheShiftCarriesAboveTheScreenIsResetToZeroRatherThanLeftNegative() {
    val grid = gridWithRows("one", "two")
    val cursor = grid.resize(
      newColumns = 5,
      newScreenRows = 1,
      newTotalRows = 8,
      cursorRow = 0,
      cursorColumn = 0,
      style = plain,
    )
    assertEquals(0, cursor.row)
  }

  /**
   * A ring built alt-screen-shaped - `totalRows == screenRows`, so `RowRing.scrollDownOneLine`
   * never grows a scrollback into it in the first place - stays at zero scrollback through a
   * shrink too, the invariant `ScreenBuffers`' alternate grid depends on.
   */
  @Test
  fun aRingShapedLikeTheAlternateScreenNeverGainsScrollbackFromAShrink() {
    val grid = TerminalGrid(columns = 5, totalRows = 4, screenRows = 4)
    grid.resize(newColumns = 5, newScreenRows = 2, newTotalRows = 2, cursorRow = 3, cursorColumn = 0, style = plain)
    assertEquals(0, grid.activeTranscriptRows)
  }

  /**
   * P4h-2: growing the column count over plain content re-wraps nothing, because everything
   * already fit - `TerminalBuffer.resize`'s reflow branch (`:233-350`) still walks it character by
   * character, but the wider screen never trips the wrap guard.
   */
  @Test
  fun growingColumnsOverPlainTextKeepsTheContentAndTriggersNoWrap() {
    val grid = gridWithRows("ab")
    val cursor = grid.resize(
      newColumns = 8,
      newScreenRows = 3,
      newTotalRows = 8,
      cursorRow = 0,
      cursorColumn = 2,
      style = plain,
    )
    assertEquals(8, grid.columns)
    assertEquals("ab", rowText(grid, 0))
    assertFalse("nothing here should have wrapped", grid.rowAt(0).lineWrap)
    assertEquals(GridCursor(0, 2), cursor)
  }

  /**
   * P4h-2: shrinking the column count wraps a row that no longer fits across as many output rows
   * as it needs, marking the row it left with `lineWrap` the way a shell's own overflow would.
   */
  @Test
  fun shrinkingColumnsWrapsALongRowAcrossTwoOutputRows() {
    val grid = gridWithRows("ABCDE")
    val cursor = grid.resize(
      newColumns = 3,
      newScreenRows = 4,
      newTotalRows = 8,
      cursorRow = 0,
      cursorColumn = 4,
      style = plain,
    )
    assertEquals(3, grid.columns)
    assertTrue("the first output row wraps into the second", grid.rowAt(0).lineWrap)
    assertEquals("ABC", rowText(grid, 0))
    assertEquals("DE", rowText(grid, 1))
    assertEquals(GridCursor(1, 1), cursor)
  }

  /**
   * P4h-2: a row already wrapped rejoins its continuation before either is re-wrapped at the new
   * width, rather than the two being re-wrapped independently.
   */
  @Test
  fun aWrappedRowRejoinsItsContinuationAndReWrapsTogetherAtTheNewWidth() {
    val grid = gridWithRows("ABCDE", "FG")
    grid.rowAt(0).lineWrap = true
    val cursor = grid.resize(
      newColumns = 3,
      newScreenRows = 4,
      newTotalRows = 8,
      cursorRow = 1,
      cursorColumn = 1,
      style = plain,
    )
    assertEquals("ABC", rowText(grid, 0))
    assertEquals("DEF", rowText(grid, 1))
    assertEquals("G", rowText(grid, 2))
    assertEquals(GridCursor(2, 0), cursor)
  }

  /**
   * P4h-2: an unwrapped row's trailing blanks are not copied into the replay, so a short row does
   * not force a wrap it never needed - `TerminalBuffer.resize`'s own `lastNonSpaceIndex` scan.
   */
  @Test
  fun trailingBlanksAreTrimmedOnAnUnwrappedRowRatherThanCopied() {
    val grid = gridWithRows("AB")
    val cursor = grid.resize(
      newColumns = 3,
      newScreenRows = 3,
      newTotalRows = 8,
      cursorRow = 1,
      cursorColumn = 0,
      style = plain,
    )
    assertEquals("AB", rowText(grid, 0))
    assertFalse("the trimmed row itself never wraps", grid.rowAt(0).lineWrap)
    assertEquals(GridCursor(1, 0), cursor)
  }

  /**
   * P4h-2: a ring shaped like the alternate screen (`totalRows == screenRows`) fails the fast
   * path's own `newScreenRows <= totalRows` guard on a grow even though its columns never move,
   * so it reflows too - the correction P4h's own row in `docs/PLAN.md` names.
   */
  @Test
  fun aRingShapedLikeTheAlternateScreenReflowsOnGrowEvenWithColumnsUnchanged() {
    val grid = TerminalGrid(columns = 5, totalRows = 3, screenRows = 3)
    grid.setChar(0, 0, 'a'.code, plain)
    val cursor = grid.resize(
      newColumns = 5,
      newScreenRows = 5,
      newTotalRows = 5,
      cursorRow = 0,
      cursorColumn = 0,
      style = plain,
    )
    assertEquals(5, grid.screenRows)
    assertEquals("a", rowText(grid, 0))
    assertEquals(GridCursor(0, 0), cursor)
  }

  /**
   * P4j-3d-1: the viewport's own arithmetic - Termux's `TerminalView.doScroll`'s own clamp
   * (`Math.min(0, Math.max(-activeTranscriptRows, mTopRow + delta))`), reused here rather than
   * ported to `ui/terminal/`'s gesture code, since it needs nothing but [TerminalGrid]'s own
   * [TerminalGrid.activeTranscriptRows].
   */
  @Test
  fun clampedTopRowStopsAtTheLiveScreen() {
    val grid = gridWithRows("one", "two", "three")
    grid.scrollDownOneLine(0, 3, CellStyle.NORMAL) // one row of scrollback now exists
    assertEquals(0, grid.clampedTopRow(current = 0, deltaRows = 5))
  }

  @Test
  fun clampedTopRowStopsAtTheOldestScrollbackRow() {
    val grid = gridWithRows("one", "two", "three")
    grid.scrollDownOneLine(0, 3, CellStyle.NORMAL) // activeTranscriptRows == 1
    assertEquals(-1, grid.clampedTopRow(current = 0, deltaRows = -5))
  }

  @Test
  fun clampedTopRowLandsExactlyInsideTheRange() {
    val grid = gridWithRows("one", "two", "three")
    repeat(3) { grid.scrollDownOneLine(0, 3, CellStyle.NORMAL) } // activeTranscriptRows == 3
    assertEquals(-2, grid.clampedTopRow(current = 0, deltaRows = -2))
    assertEquals(-1, grid.clampedTopRow(current = -2, deltaRows = 1))
  }

  /**
   * Termux's own `onScreenUpdated` re-clamps `mTopRow` defensively before trusting it
   * (`if (mTopRow < -rowsInHistory) mTopRow = -rowsInHistory`) - a `current` already out of
   * range (e.g. `activeTranscriptRows` having shrunk under it across a resize) must not compound
   * with `deltaRows` but land back in range in the one call.
   */
  @Test
  fun clampedTopRowRecoversFromAnAlreadyOutOfRangeCurrentValue() {
    val grid = gridWithRows("one", "two", "three") // activeTranscriptRows == 0
    assertEquals(0, grid.clampedTopRow(current = -50, deltaRows = 0))
  }

  private fun gridWithRows(vararg rows: String): TerminalGrid {
    val grid = TerminalGrid(columns = 5, totalRows = 8, screenRows = maxOf(rows.size, 3))
    for ((row, content) in rows.withIndex()) {
      for ((column, character) in content.withIndex()) grid.setChar(column, row, character.code, plain)
    }
    return grid
  }

  private fun rowText(grid: TerminalGrid, row: Int): String =
    grid.selectedText(0, row, grid.columns - 1, row, joinBackLines = false)

  private companion object {
    /** One java char, display width two. */
    const val WIDE = 0x679C
  }
}
