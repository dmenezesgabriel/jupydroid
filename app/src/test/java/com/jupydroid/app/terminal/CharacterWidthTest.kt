package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transcribed from `terminal-emulator/src/test/java/com/termux/terminal/WcWidthTest.java`
 * (termux-app `3df69d1d`, Apache 2.0 - see [CharacterWidth]), plus a table-integrity test of
 * this transcription's own making.
 */
class CharacterWidthTest {
  private fun assertWidthIs(expected: Int, codePoint: Int) {
    assertEquals("width of U+${codePoint.toString(16).uppercase()}", expected, CharacterWidth.of(codePoint))
  }

  @Test
  fun printableAsciiIsOneColumn() {
    for (codePoint in 0x20..0x7E) assertWidthIs(1, codePoint)
  }

  @Test
  fun someLatinAndSymbolsAreOneColumn() {
    assertWidthIs(1, 'å'.code)
    assertWidthIs(1, 'ä'.code)
    assertWidthIs(1, 'ö'.code)
    assertWidthIs(1, 0x23F2)
  }

  @Test
  fun eastAsianAndFullwidthAreTwoColumns() {
    assertWidthIs(2, 'Ａ'.code)
    assertWidthIs(2, 'Ｂ'.code)
    assertWidthIs(2, 'Ｃ'.code)
    assertWidthIs(2, '中'.code)
    assertWidthIs(2, '文'.code)

    assertWidthIs(2, 0x679C)
    assertWidthIs(2, 0x679D)

    assertWidthIs(2, 0x2070E)
    assertWidthIs(2, 0x20731)

    assertWidthIs(1, 0x1F781)
  }

  @Test
  fun someSupplementaryPlaneCodePointsAreOneColumn() {
    assertWidthIs(1, 0x1D11E)
    assertWidthIs(1, 0x1D11F)
  }

  @Test
  fun combiningMarksAreZeroColumns() {
    assertWidthIs(0, 0x0302)
    assertWidthIs(0, 0x0308)
    assertWidthIs(0, 0xFE0F)
  }

  /**
   * The word joiner separates words in scripts without explicit spacing: it produces no space and
   * only forbids a line break at its position (https://en.wikipedia.org/wiki/Word_joiner).
   */
  @Test
  fun theWordJoinerIsZeroColumns() {
    assertWidthIs(0, 0x2060)
  }

  /**
   * Markus Kuhn, on why wcwidth(SOFT HYPHEN) stays 1: "Existing implementation practice in
   * terminals is that the SOFT HYPHEN is a spacing graphical character, and the purpose of my
   * wcwidth() was to predict the advancement of the cursor position after a string is sent to a
   * terminal. VT100-style terminals do not hyphenate."
   * (http://osdir.com/ml/internationalization.linux/2003-05/msg00006.html)
   */
  @Test
  fun theSoftHyphenIsOneColumn() {
    assertWidthIs(1, 0x00AD)
  }

  @Test
  fun aHangulJamoExtendedVowelIsOneColumn() {
    assertWidthIs(1, 0x11A3)
  }

  @Test
  fun emojiAreTwoColumns() {
    assertWidthIs(2, 0x1F428) // KOALA.
    assertWidthIs(2, 0x231A) // WATCH.
    assertWidthIs(2, 0x1F643) // UPSIDE-DOWN FACE (Unicode 8).
  }

  /**
   * Not in `WcWidthTest.java`, and it covers the one departure from `wcwidth(3)` that
   * [CharacterWidth] documents: C0 and C1 return 0 rather than -1. Nothing upstream pins it.
   */
  @Test
  fun c0AndC1ControlCharactersAreZeroColumns() {
    for (codePoint in 0x00..0x1F) assertWidthIs(0, codePoint)
    for (codePoint in 0x7F..0x9F) assertWidthIs(0, codePoint)
  }

  /** [CharacterWidth.at] has no upstream test of its own; `TerminalRow` is what exercises it. */
  @Test
  fun theWidthAtAnIndexReadsASurrogatePairAsOneCodePoint() {
    val koala = "x\uD83D\uDC28".toCharArray() // 'x' then U+1F428, two java chars.
    assertEquals(1, CharacterWidth.at(koala, 0))
    assertEquals(2, CharacterWidth.at(koala, 1))
  }

  @Test
  fun zeroWidthCountInCountsCombiningCharactersAndStepsOverSurrogatePairs() {
    val chars = "e\u0308\u0302\uD83D\uDC28".toCharArray() // 'e', two combining marks, a koala.
    assertEquals(2, CharacterWidth.zeroWidthCountIn(chars, 0, chars.size))
    assertEquals(1, CharacterWidth.zeroWidthCountIn(chars, 2, chars.size))
    assertEquals(0, CharacterWidth.zeroWidthCountIn(chars, 3, chars.size))
  }

  /** Out of range counts nothing rather than throwing - the cap in `TerminalRow` relies on it. */
  @Test
  fun zeroWidthCountInIsEmptyOutsideTheArray() {
    val chars = "ab".toCharArray()
    assertEquals(0, CharacterWidth.zeroWidthCountIn(chars, -1, 2))
    assertEquals(0, CharacterWidth.zeroWidthCountIn(chars, 2, 5))
  }

  /**
   * Nine spot checks cannot catch a slip in 495 packed ranges, and the binary search in
   * [CharacterWidth] is only correct on a sorted, disjoint table. This is what checks the
   * transcription itself.
   */
  @Test
  fun bothTablesAreWellFormedPairsInAscendingDisjointOrder() {
    assertTableIsWellFormed("ZERO_WIDTH", CharacterWidthRanges.ZERO_WIDTH)
    assertTableIsWellFormed("WIDE_EASTASIAN", CharacterWidthRanges.WIDE_EASTASIAN)
  }

  private fun assertTableIsWellFormed(name: String, table: IntArray) {
    assertEquals("$name holds (low, high) pairs", 0, table.size % 2)
    assertTrue("$name is not empty", table.isNotEmpty())
    for (pair in table.indices step 2) {
      val low = table[pair]
      val high = table[pair + 1]
      assertTrue("$name[$pair]: low=$low > high=$high", low <= high)
      assertTrue("$name[$pair]: low=$low is not a code point", low in 0..Character.MAX_CODE_POINT)
      assertTrue("$name[$pair]: high=$high is not a code point", high in 0..Character.MAX_CODE_POINT)
      if (pair > 0) {
        val previousHigh = table[pair - 1]
        assertTrue("$name[$pair]: low=$low overlaps or follows out of order after $previousHigh", previousHigh < low)
      }
    }
  }

  /** The upstream range counts, so a truncated paste is a failure rather than a smaller table. */
  @Test
  fun bothTablesHoldTheRangeCountsTheyWereTranscribedFrom() {
    assertEquals("ZERO_WIDTH ranges", 343, CharacterWidthRanges.ZERO_WIDTH.size / 2)
    assertEquals("WIDE_EASTASIAN ranges", 121, CharacterWidthRanges.WIDE_EASTASIAN.size / 2)
  }
}
