package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A document changed at the ranges a search found - the half of replace that P2x-1e took off the
 * port and P2x-5 gives a home of its own.
 *
 * The reference builds one `ResourceTextEdit` per match and applies them sorted by where they start
 * (`replaceService.ts:196-203`), which is what [withEditsApplied] is; whether the document still
 * holds those matches at all is `_validateBeforePrepare`'s version check
 * (`bulkTextEdits.ts:213-216`), which is what [editsAgainst] answers with null.
 */
class TextEditTest {
  private val plain = SearchQuery("cat")

  private fun matches(text: String, query: SearchQuery = plain): List<MatchedLine> =
    matchedLinesIn(text, (query.compile() as CompiledQuery.Matching).regex)

  @Test
  fun anEditReplacesItsRangeAndKeepsWhatIsAroundIt() {
    assertEquals("print(dog)", "print(cat)".withEditsApplied(listOf(TextEdit(6..8, "dog"))))
  }

  /** The text *between* two edits survives, which is the whole reason this is a fold and not a map. */
  @Test
  fun severalEditsKeepTheTextBetweenThem() {
    val edits = listOf(TextEdit(0..2, "dog"), TextEdit(8..10, "dog"))
    assertEquals("dog and dog", "cat and cat".withEditsApplied(edits))
  }

  /** `modelEdits.sort(compareRangesUsingStarts)` - the caller does not have to hand them in order. */
  @Test
  fun editsAreAppliedInPositionOrderHoweverTheyArrive() {
    val edits = listOf(TextEdit(8..10, "dog"), TextEdit(0..2, "dog"))
    assertEquals("dog and dog", "cat and cat".withEditsApplied(edits))
  }

  /** A replacement of a different length moves everything after it, which the fold must not lose. */
  @Test
  fun aLongerReplacementDoesNotDisturbTheEditAfterIt() {
    val edits = listOf(TextEdit(0..2, "elephant"), TextEdit(8..10, "dog"))
    assertEquals("elephant and dog", "cat and cat".withEditsApplied(edits))
  }

  @Test
  fun noEditsLeavesTheTextExactlyAsItWas() {
    assertEquals("cat and cat", "cat and cat".withEditsApplied(emptyList()))
  }

  @Test
  fun everyMatchBecomesOneEditCarryingItsReplacement() {
    val text = "import os\nprint(cat)\n"
    val edits = matches(text).editsAgainst(text, "dog", plain, preserveCase = false)
    assertEquals(listOf(TextEdit(16..18, "dog")), edits)
  }

  /** Offsets are the **file's**, not the line's - `MatchedLine.rangeIn` is the arithmetic. */
  @Test
  fun aMatchOnALaterLineIsAnEditAtTheFileOffset() {
    val text = "cat\ncat"
    assertEquals(
      listOf(TextEdit(0..2, "dog"), TextEdit(4..6, "dog")),
      matches(text).editsAgainst(text, "dog", plain, preserveCase = false),
    )
  }

  /**
   * The version check, adapted: the workspace is shared with the kernel, so a cell can rewrite a
   * file between the walk and the replace. The reference refuses the whole operation
   * (`bulkTextEdits.ts:213-216`); here that is null.
   */
  @Test
  fun aFileThatLostTheLineTheMatchWasOnRefusesEveryEdit() {
    val found = matches("import os\nprint(cat)\n")
    assertNull(found.editsAgainst("import os\n", "dog", plain, preserveCase = false))
  }

  /**
   * **The line being long enough is not enough**, and this is the case `MatchedLine.rangeIn` alone
   * cannot answer: a line still there and still that long, now saying something else, would have
   * had the *wrong characters* replaced.
   */
  @Test
  fun aLineThatStillFitsButNoLongerSaysWhatWasFoundRefusesEveryEdit() {
    val found = matches("print(cat)")
    assertNull(found.editsAgainst("print(cow)", "dog", plain, preserveCase = false))
  }

  /** One stale match refuses the lot, because the reference refuses the whole bulk edit. */
  @Test
  fun oneStaleMatchRefusesTheOtherEditsToo() {
    val found = matches("cat\ncat")
    assertNull(found.editsAgainst("cat\ncow", "dog", plain, preserveCase = false))
  }

  @Test
  fun aFileWithNothingFoundInItHasNoEdits() {
    assertEquals(emptyList<TextEdit>(), emptyList<MatchedLine>().editsAgainst("x", "d", plain, false))
  }

  /** [replacementFor]'s two steps reach here whole - the substitution first, then the case. */
  @Test
  fun anEditCarriesTheSubstitutionAndThePreservedCase() {
    val query = SearchQuery("c(a)t", useRegex = true)
    val edits = matches("CAT", query).editsAgainst("CAT", "[\$1]", query, preserveCase = true)
    assertEquals(listOf(TextEdit(0..2, "[A]")), edits)
  }

  /**
   * The inverse of one edit sits **where the replacement landed**, carrying what was there before.
   *
   * `TextModel._applyUndo` builds exactly this from each recorded change - `{ range: newPosition..
   * newEnd, text: change.oldText }` (`textModel.ts:1441-1450`).
   */
  @Test
  fun anEditInvertsToWhereItsReplacementLandedCarryingWhatWasThere() {
    val edits = listOf(TextEdit(6..8, "dog"))

    assertEquals(listOf(TextEdit(6..8, "cat")), edits.invertedAgainst("print(cat)"))
  }

  /**
   * A replacement of a different length moves every inverse after it, which is the whole arithmetic:
   * the inverse is expressed against the document the edits **produced**, not the one they were
   * built from.
   */
  @Test
  fun aLongerReplacementShiftsTheInverseOfEveryEditAfterIt() {
    val edits = listOf(TextEdit(0..2, "elephant"), TextEdit(8..10, "dog"))

    assertEquals(
      listOf(TextEdit(0..7, "cat"), TextEdit(13..15, "cat")),
      edits.invertedAgainst("cat and cat"),
    )
  }

  /** Sorted here too, for [withEditsApplied]'s reason - a caller must not have to know it matters. */
  @Test
  fun editsInvertInPositionOrderHoweverTheyArrive() {
    val edits = listOf(TextEdit(8..10, "dog"), TextEdit(0..2, "elephant"))

    assertEquals(
      listOf(TextEdit(0..7, "cat"), TextEdit(13..15, "cat")),
      edits.invertedAgainst("cat and cat"),
    )
  }

  /** Replacing with nothing is a deletion, and its inverse is an insertion at the point it removed. */
  @Test
  fun aDeletionInvertsToAnInsertionWhereItRemovedTheText() {
    val edits = listOf(TextEdit(0..3, ""))

    val inverse = edits.invertedAgainst("cat and cat")

    assertEquals(listOf(TextEdit(0..-1, "cat ")), inverse)
    assertEquals("cat and cat", "and cat".withEditsApplied(inverse))
  }

  /**
   * The property the whole undo rests on, stated once: apply, invert, apply, and the document is
   * exactly what it was.
   */
  @Test
  fun applyingTheInverseOfAnEditPutsTheDocumentBackAsItWas() {
    val before = "cat and cat and cat"
    val edits = listOf(TextEdit(0..2, "elephant"), TextEdit(8..10, ""), TextEdit(16..18, "ox"))

    val after = before.withEditsApplied(edits)

    assertEquals(before, after.withEditsApplied(edits.invertedAgainst(before)))
  }

  @Test
  fun nothingReplacedInvertsToNothingToPutBack() {
    assertEquals(emptyList<TextEdit>(), emptyList<TextEdit>().invertedAgainst("cat"))
  }
}
