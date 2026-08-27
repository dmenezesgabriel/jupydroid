package com.jupydroid.app.ui.shell

import com.jupydroid.app.domain.model.MatchedLine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `MatchImpl.preview()` and the `lcut` under it, transcribed - the row a result draws.
 *
 * **The gate for a transcription is equality**, which is `PathGlobParityTest`'s rule: what is
 * pinned here is the reference's own arithmetic, not a shape that looks about right.
 */
class MatchPreviewTest {
  private fun previewOf(text: String, range: IntRange) = previewOf(MatchedLine(line = 1, text = text, range = range))

  /** A short line is its own preview - `if (trimmed.length < n) return trimmed`. */
  @Test
  fun aShortLineIsShownWhole() {
    assertEquals(MatchPreview("print(", "cat", ")"), previewOf("print(cat)", 6..8))
  }

  /**
   * **Leading indentation goes**, which is `text.trimStart()` and the first thing `lcut` does - a
   * result row indented by its own source would waste the width the code needs.
   */
  @Test
  fun leadingIndentationIsTrimmedAway() {
    assertEquals(MatchPreview("print(", "cat", ")"), previewOf("        print(cat)", 14..16))
  }

  /**
   * **A long line is cut at a word boundary, keeping the context nearest the match.**
   *
   * The whole of `lcut`, held against the reference's own output rather than against a shape that
   * looked right - which is `PathGlobParityTest`'s rule, and it earned its place here immediately:
   * this test first asserted `"…the lazy dog and then finds a "`, on the reading that `lcut` keeps
   * *at least* 26 characters. It keeps the last boundary whose tail is at least 26, which is one
   * word further in. The table below is `strings.ts:771-794` run under node.
   */
  @Test
  fun everyCutIsTheOneTheReferenceMakes() {
    val expected = mapOf(
      "print(" to "print(",
      "        print(" to "print(",
      "the quick brown fox jumps over the lazy dog and then finds a " to "…lazy dog and then finds a ",
      "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" to "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "" to "",
      "  " to "",
      "x = 1; y = 2; z = 3; total = x + y + z; result = " to "…total = x + y + z; result = ",
      "self._model.headings.filter(h => h.level < 3).map(h => h.text).join(" to "…< 3).map(h => h.text).join(",
      "...................................................." to "....................................................",
      "one_two_three_four_five_six_seven_eight_nine_ten_" to "one_two_three_four_five_six_seven_eight_nine_ten_",
      "\tif (someCondition && otherCondition) { doSomethingRatherLong(" to "…) { doSomethingRatherLong(",
      "import numpy as np; import pandas as pd; frame = pd.read_csv(" to "…as pd; frame = pd.read_csv(",
      // **Every boundary is visited, including the one next door.** A line of one-character words
      // puts two `\b` at adjacent indices, and the reference's `re.lastIndex += 1` is what steps
      // onto the second of them. Found by mutation: stepping by two survived every case above, and
      // this is the case that fails it - the cut lands one word later without it.
      "x = a + b * c - d / e + f * g - h + i + j + k + l + " to "…* g - h + i + j + k + l + ",
    )

    assertEquals(expected, expected.keys.associateWith { lcut(it, 26, "…") })
  }

  /** A cut that lands between words, seen once as a whole row rather than as a `lcut` argument. */
  @Test
  fun theCutRowIsTheContextNearestTheMatchAndThenTheMatch() {
    val line = "the quick brown fox jumps over the lazy dog and then finds a cat here"
    val preview = previewOf(line, line.indexOf("cat")..line.indexOf("cat") + 2)

    assertEquals(MatchPreview("…lazy dog and then finds a ", "cat", " here"), preview)
  }

  /**
   * `MAX_PREVIEW_CHARS = 250`, spent before-first then match then after
   * (`searchTreeModel/match.ts:70-73`), so a minified file cannot hand a row a megabyte to lay out.
   */
  @Test
  fun aPreviewIsBoundedAtTheReferencesTwoHundredAndFiftyCharacters() {
    val line = "x ".repeat(200) + "cat" + "y".repeat(400)
    val preview = previewOf(line, line.indexOf("cat")..line.indexOf("cat") + 2)

    assertEquals(MAX_PREVIEW_CHARS, preview.before.length + preview.match.length + preview.after.length)
  }

  /** The match itself is what the cap spends on first after the context, and it can be cut too. */
  @Test
  fun aMatchLongerThanTheBudgetIsCutRatherThanTheRowGrowing() {
    val line = "z".repeat(400)
    val preview = previewOf(line, 0..399)

    assertEquals("", preview.before)
    assertEquals(MAX_PREVIEW_CHARS, preview.match.length)
    assertEquals("", preview.after)
  }
}
