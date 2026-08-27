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
}
