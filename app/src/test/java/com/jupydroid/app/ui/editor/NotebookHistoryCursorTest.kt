package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `packages/notebook/src/history.ts`, transcribed rule by rule.
 *
 * Every test here names the line it comes from, because none of these rules is obvious and three
 * of them are counter-intuitive: what is being typed is *removed* from the list and then put back
 * at its end, a repeat is dropped only when it is contiguous, and a longer tail moves the cursor
 * by an amount that depends on what arrived in the middle.
 */
class NotebookHistoryCursorTest {
  @Test
  fun atRest_theCursorIsOnWhatIsBeingTyped() {
    val cursor = NotebookHistoryCursor(listOf("x = 1", "x + 1"), placeholder = "half typ")

    assertEquals("half typ", cursor.current)
  }

  /** `--this._cursor` from a cursor parked on the placeholder (`history.ts:168`, `:181`). */
  @Test
  fun back_givesTheMostRecentEntry() {
    val cursor = NotebookHistoryCursor(listOf("x = 1", "x + 1"), placeholder = "")

    assertTrue(cursor.back())
    assertEquals("x + 1", cursor.current)
  }

  @Test
  fun back_walksTheWholeTailOldestLast() {
    val cursor = NotebookHistoryCursor(listOf("first", "second", "third"), placeholder = "")

    val walked = generateSequence { if (cursor.back()) cursor.current else null }.toList()

    assertEquals(listOf("third", "second", "first"), walked)
  }

  /** `filterStr !== current` (`history.ts:363`): the line already in the cell is not offered back. */
  @Test
  fun back_skipsAnEntryEqualToWhatIsAlreadyInTheCell() {
    val cursor = NotebookHistoryCursor(listOf("x = 1", "x + 1"), placeholder = "x + 1")

    assertTrue(cursor.back())
    assertEquals("x = 1", cursor.current)
  }

  /** `current !== last` (`history.ts:363`) - a run of the same input counts once. */
  @Test
  fun back_collapsesContiguousDuplicates() {
    val cursor = NotebookHistoryCursor(listOf("run()", "run()", "run()"), placeholder = "")

    assertTrue(cursor.back())
    assertFalse("three contiguous copies are one entry", cursor.back())
  }

  /** `last` moves only when something is kept, so a repeat with something between it survives. */
  @Test
  fun back_keepsARepeatThatIsNotContiguous() {
    val cursor = NotebookHistoryCursor(listOf("run()", "check()", "run()"), placeholder = "")

    val walked = generateSequence { if (cursor.back()) cursor.current else null }.toList()

    assertEquals(listOf("run()", "check()", "run()"), walked)
  }

  /** `Math.max(0, this._cursor)` (`history.ts:185`) once no longer tail is to be had. */
  @Test
  fun back_staysOnTheOldestEntryItKnowsAndSaysItIsStuck() {
    val cursor = NotebookHistoryCursor(listOf("only"), placeholder = "")
    cursor.back()

    assertFalse("nothing older is known, and the caller is the one who can fetch more", cursor.back())
    assertEquals("only", cursor.current)
  }

  @Test
  fun forward_returnsTheWayItCame() {
    val cursor = NotebookHistoryCursor(listOf("first", "second"), placeholder = "")
    cursor.back()
    cursor.back()

    cursor.forward()

    assertEquals("second", cursor.current)
  }

  /**
   * `this._filtered.push(filterStr)` (`history.ts:367`) is the whole reason the placeholder is in
   * the list at all: walking forward off the end puts back what was being typed.
   */
  @Test
  fun forward_pastTheEndRestoresWhatWasBeingTyped() {
    val cursor = NotebookHistoryCursor(listOf("x = 1"), placeholder = "half typ")
    cursor.back()

    cursor.forward()
    cursor.forward()

    assertEquals("half typ", cursor.current)
  }

  /** `fetchBatch` (`history.ts:247-273`): a longer tail lands one entry older than where it stuck. */
  @Test
  fun grownTo_landsOnTheEntryBeforeTheOldestOneItHad() {
    val cursor = NotebookHistoryCursor(listOf("b"), placeholder = "p")
    cursor.back()

    assertTrue(cursor.grownTo(listOf("a", "b")))
    assertEquals("a", cursor.current)
  }

  /**
   * `cursorOffset` (`history.ts:253-264`), and it does real work: the kernel ran more cells between
   * the two asks, so the entries this cursor had are no longer at the end of the list. Counting
   * only the length difference would land back on `b`, which has already been walked past.
   */
  @Test
  fun grownTo_skipsPastEntriesThatArrivedAfterTheOnesItAlreadyHad() {
    val cursor = NotebookHistoryCursor(listOf("b"), placeholder = "p")
    cursor.back()

    assertTrue(cursor.grownTo(listOf("a", "b", "c")))
    assertEquals("a", cursor.current)
  }

  /** `_history.length > oldHistory.length` (`history.ts:269`) - the caller's cue to stop asking. */
  @Test
  fun grownTo_reportsWhenALongerTailHeldNothingOlder() {
    val cursor = NotebookHistoryCursor(listOf("b"), placeholder = "p")
    cursor.back()

    assertFalse(cursor.grownTo(listOf("b")))
    assertEquals("the cursor must stay on something real", "b", cursor.current)
  }

  /** A kernel that has run nothing: the placeholder is the only entry, and back does nothing. */
  @Test
  fun back_doesNothingForAKernelThatHasRunNothing() {
    val cursor = NotebookHistoryCursor(emptyList(), placeholder = "typed")

    assertFalse(cursor.back())
    assertEquals("typed", cursor.current)
  }

  // --- where the caret lands (`history.ts:225-230`) ---

  @Test
  fun endOfFirstLine_isTheEndOfAOneLineEntry() {
    assertEquals(5, endOfFirstLine("x + 1"))
  }

  /** `content.indexOf('\n')`: a multi-line entry leaves the caret at the end of its *first* line. */
  @Test
  fun endOfFirstLine_isTheFirstNewlineOfAMultiLineEntry() {
    assertEquals(8, endOfFirstLine("def f():\n    pass"))
  }

  @Test
  fun endOfFirstLine_isZeroForEmptyText() {
    assertEquals(0, endOfFirstLine(""))
  }
}
