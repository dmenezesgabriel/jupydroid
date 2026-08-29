package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random

/**
 * Transcribed from `terminal-emulator/src/test/java/com/termux/terminal/TerminalRowTest.java`
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalRow]).
 *
 * This is the oracle for [TerminalRow.setChar] being **decomposed** rather than transcribed
 * line for line: upstream's is 117 lines at a complexity detekt refuses, so it is split along its
 * own comment seams and these tests are what say the split changed no behaviour.
 */
class TerminalRowTest {
  private lateinit var row: TerminalRow

  @Before
  fun setUp() {
    row = TerminalRow(COLUMNS, CellStyle.NORMAL)
  }

  private fun assertRowStartsWith(vararg codePoints: Int) {
    var charIndex = 0
    for ((position, expected) in codePoints.withIndex()) {
      val first = row.text[charIndex++]
      val actual = if (first.isHighSurrogate()) Character.toCodePoint(first, row.text[charIndex++]) else first.code
      assertEquals("code point at position $position", expected, actual)
    }
  }

  private fun assertColumnsStartAt(vararg charIndices: Int) {
    for ((column, expected) in charIndices.withIndex()) {
      assertEquals("start of column $column", expected, row.findStartOfColumn(column))
    }
  }

  /** The properties every other test in this class leans on, asserted rather than assumed. */
  @Test
  fun theCodePointsTheseTestsAreBuiltOnHaveTheWidthsTheyClaim() {
    assertEquals(1, Character.charCount(WIDE_ONE_CHAR_A))
    assertEquals(1, Character.charCount(WIDE_ONE_CHAR_B))
    assertEquals(2, CharacterWidth.of(WIDE_ONE_CHAR_A))
    assertEquals(2, CharacterWidth.of(WIDE_ONE_CHAR_B))

    assertEquals(2, Character.charCount(NARROW_SURROGATE_A))
    assertEquals(2, Character.charCount(NARROW_SURROGATE_B))
    assertEquals(1, CharacterWidth.of(NARROW_SURROGATE_A))
    assertEquals(1, CharacterWidth.of(NARROW_SURROGATE_B))

    assertEquals(2, Character.charCount(WIDE_SURROGATE_A))
    assertEquals(2, Character.charCount(WIDE_SURROGATE_B))
    assertEquals(2, CharacterWidth.of(WIDE_SURROGATE_A))
    assertEquals(2, CharacterWidth.of(WIDE_SURROGATE_B))

    assertEquals(1, Character.charCount(DIAERESIS))
    assertEquals(0, CharacterWidth.of(DIAERESIS))
  }

  @Test
  fun aCombiningMarkIsAddedToItsColumnRatherThanReplacingIt() {
    row.setChar(0, DIAERESIS, CellStyle(0L))
    assertEquals(81, row.spaceUsed)
    row.setChar(0, DIAERESIS, CellStyle(0L))
    assertEquals(82, row.spaceUsed)
    assertRowStartsWith(' '.code, DIAERESIS, DIAERESIS, ' '.code)
  }

  @Test
  fun theFirstColumnStartsAtIndexZeroBeforeAndAfterAWrite() {
    assertEquals(0, row.findStartOfColumn(0))
    row.setChar(0, 'a'.code, CellStyle(0L))
    assertEquals(0, row.findStartOfColumn(0))
  }

  @Test
  fun asciiWritesLandOneCharPerColumn() {
    assertEquals(0, row.findStartOfColumn(0))
    row.setChar(0, 'a'.code, CellStyle(0L))
    assertRowStartsWith('a'.code, ' '.code, ' '.code)
    assertEquals(1, row.findStartOfColumn(1))
    assertEquals(80, row.spaceUsed)
    row.setChar(0, 'b'.code, CellStyle(0L))
    assertEquals(1, row.findStartOfColumn(1))
    assertEquals(2, row.findStartOfColumn(2))
    assertEquals(80, row.spaceUsed)
    assertColumnsStartAt(0, 1, 2, 3)
  }

  /**
   * Upstream drives this with an unseeded `new Random()`, so a failure is not reproducible - the
   * "R" in F.I.R.S.T. The seed is fixed here; the run is otherwise the same thousand writes.
   */
  @Test
  fun aThousandRandomAsciiWritesLeaveTheRowEqualToAPlainCharArray() {
    val alphabet = charArrayOf('a', 'c', 'e', '4', '5', '6', '7', '8')
    val expected = CharArray(COLUMNS) { ' ' }
    val random = Random(FUZZ_SEED)
    repeat(1000) {
      val column = random.nextInt(expected.size)
      val character = alphabet[random.nextInt(alphabet.size)]
      expected[column] = character
      row.setChar(column, character.code, CellStyle(0L))
    }
    for (column in expected.indices) {
      assertEquals("column $column", expected[column], row.text[column])
    }
  }

  @Test
  fun aNarrowSurrogatePairOccupiesOneColumnAndTwoJavaChars() {
    assertEquals(0, row.findStartOfColumn(0))
    assertEquals(80, row.spaceUsed)

    row.setChar(0, NARROW_SURROGATE_A, CellStyle(0L))
    assertEquals(81, row.spaceUsed)
    assertRowStartsWith(NARROW_SURROGATE_A, ' '.code, ' '.code)
    assertColumnsStartAt(0, 2, 3, 4)
  }

  @Test
  fun overwritingANarrowSurrogatePairWithAsciiGivesTheRowItsCharBack() {
    row.setChar(0, NARROW_SURROGATE_A, CellStyle(0L))
    row.setChar(0, 'a'.code, CellStyle(0L))
    assertEquals(80, row.spaceUsed)
    assertRowStartsWith('a'.code, ' '.code, ' '.code)
    assertColumnsStartAt(0, 1, 2, 3)
  }

  @Test
  fun twoNarrowSurrogatePairsSitInTwoColumnsAndFourJavaChars() {
    row.setChar(0, NARROW_SURROGATE_A, CellStyle(0L))
    row.setChar(1, 'a'.code, CellStyle(0L))
    assertRowStartsWith(NARROW_SURROGATE_A, 'a'.code, ' '.code)

    row.setChar(1, NARROW_SURROGATE_B, CellStyle(0L))
    assertRowStartsWith(NARROW_SURROGATE_A, NARROW_SURROGATE_B, ' '.code)
    assertColumnsStartAt(0, 2, 4, 5)
    assertEquals(82, row.spaceUsed)
  }

  @Test
  fun aWideCharacterFillsTwoColumnsAndOverwritingEitherHalfClearsBoth() {
    row.setChar(0, 'a'.code, CellStyle(0L))
    row.setChar(1, WIDE_ONE_CHAR_B, CellStyle(0L))
    assertRowStartsWith('a'.code, WIDE_ONE_CHAR_B, ' '.code)
    assertColumnsStartAt(0, 1, 1, 2)
    row.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    assertRowStartsWith(WIDE_ONE_CHAR_A, ' '.code, ' '.code)
    assertColumnsStartAt(0, 0, 1, 2)

    row.setChar(0, ' '.code, CellStyle(0L))
    assertRowStartsWith(' '.code, ' '.code, ' '.code, ' '.code)
    assertColumnsStartAt(0, 1, 2, 3, 4)
    row.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    row.setChar(2, WIDE_ONE_CHAR_B, CellStyle(0L))
    assertRowStartsWith(WIDE_ONE_CHAR_A, WIDE_ONE_CHAR_B)
    assertColumnsStartAt(0, 0, 1, 1, 2)
    row.setChar(0, 'a'.code, CellStyle(0L))
    assertRowStartsWith('a'.code, ' '.code, WIDE_ONE_CHAR_B, ' '.code)
  }

  /** The same as [aWideCharacterFillsTwoColumnsAndOverwritingEitherHalfClearsBoth], but surrogate pairs. */
  @Test
  fun aWideSurrogatePairFillsTwoColumnsAndOverwritingEitherHalfClearsBoth() {
    row.setChar(0, 'a'.code, CellStyle(0L))
    assertColumnsStartAt(0, 1, 2, 3, 4)

    row.setChar(1, WIDE_SURROGATE_B, CellStyle(0L))
    assertColumnsStartAt(0, 1, 1, 3, 4)
    assertRowStartsWith('a'.code, WIDE_SURROGATE_B, ' '.code)
    row.setChar(0, WIDE_SURROGATE_A, CellStyle(0L))
    assertColumnsStartAt(0, 0, 2, 3, 4)
    assertRowStartsWith(WIDE_SURROGATE_A, ' '.code, ' '.code, ' '.code)

    row.setChar(0, ' '.code, CellStyle(0L))
    assertRowStartsWith(' '.code, ' '.code, ' '.code, ' '.code)
    row.setChar(0, WIDE_SURROGATE_A, CellStyle(0L))
    row.setChar(1, WIDE_SURROGATE_B, CellStyle(0L))
    assertRowStartsWith(' '.code, WIDE_SURROGATE_B, ' '.code)
    row.setChar(0, 'a'.code, CellStyle(0L))
    assertRowStartsWith('a'.code, WIDE_SURROGATE_B, ' '.code)
  }

  @Test
  fun theReplacementCharacterIsWrittenLikeAnyOtherNarrowCharacter() {
    row.setChar(0, UNICODE_REPLACEMENT_CHAR, CellStyle(0L))
    row.setChar(1, 'Y'.code, CellStyle(0L))
    assertRowStartsWith(UNICODE_REPLACEMENT_CHAR, 'Y'.code, ' '.code, ' '.code)
  }

  @Test
  fun aNarrowSurrogatePairIsTwoJavaCharsInOneColumn() {
    assertEquals(2, Character.toChars(G_CLEF).size)
    assertEquals(2, Character.toChars(G_CLEF_OTTAVA).size)

    row.setChar(0, G_CLEF_OTTAVA, CellStyle(0L))
    assertEquals(G_CLEF_OTTAVA, Character.toCodePoint(row.text[0], row.text[1]))
    assertEquals(' ', row.text[2])
    assertEquals(2, row.findStartOfColumn(1))

    row.setChar(0, G_CLEF, CellStyle(0L))
    assertEquals(G_CLEF, Character.toCodePoint(row.text[0], row.text[1]))
    assertEquals(' ', row.text[2])
    assertEquals(2, row.findStartOfColumn(1))
  }

  @Test
  fun overwritingANarrowSurrogatePairUnshiftsEveryColumnAfterIt() {
    row.setChar(0, G_CLEF, CellStyle(0L))
    row.setChar(1, G_CLEF_OTTAVA, CellStyle(0L))
    row.setChar(2, 'a'.code, CellStyle(0L))
    assertEquals(G_CLEF, Character.toCodePoint(row.text[0], row.text[1]))
    assertEquals(G_CLEF_OTTAVA, Character.toCodePoint(row.text[2], row.text[3]))
    assertEquals('a', row.text[4])
    assertColumnsStartAt(0, 2, 4, 5, 6)

    row.setChar(0, ' '.code, CellStyle(0L))
    assertEquals(' ', row.text[0])
    assertEquals(G_CLEF_OTTAVA, Character.toCodePoint(row.text[1], row.text[2]))
    assertEquals('a', row.text[3])
    assertColumnsStartAt(0, 1, 3, 4, 5)
  }

  @Test
  fun aWholeRowOfNarrowSurrogatePairsMapsBackToOneColumnEachAndUnpacksAgain() {
    for (column in 0 until COLUMNS) row.setChar(column, if (column % 2 == 0) G_CLEF else G_CLEF_OTTAVA, CellStyle(0L))
    for (column in 0 until COLUMNS) {
      val at = row.findStartOfColumn(column)
      val expected = if (column % 2 == 0) G_CLEF else G_CLEF_OTTAVA
      assertEquals(expected, Character.toCodePoint(row.text[at], row.text[at + 1]))
    }
    for (column in 0 until COLUMNS) row.setChar(column, if (column % 2 == 0) 'a'.code else 'b'.code, CellStyle(0L))
    for (column in 0 until COLUMNS) {
      assertEquals(column, row.findStartOfColumn(column))
      assertEquals(if (column % 2 == 0) 'a' else 'b', row.text[column])
    }
  }

  @Test
  fun overwritingEitherHalfOfAWideCharacterWithANarrowOneLeavesASpace() {
    // Initial "OO "
    row.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    assertEquals(WIDE_ONE_CHAR_A, row.text[0].code)
    assertEquals(' ', row.text[1])
    assertColumnsStartAt(0, 0, 1)

    // Writing the first column clears the second: "a  "
    row.setChar(0, 'a'.code, CellStyle(0L))
    assertEquals('a', row.text[0])
    assertEquals(' ', row.text[1])
    assertColumnsStartAt(0, 1, 2)

    // Back to "OO "
    row.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    assertEquals(WIDE_ONE_CHAR_A, row.text[0].code)
    assertEquals(' ', row.text[1])
    assertColumnsStartAt(0, 0, 1)

    // Writing the second column clears the first: " a "
    row.setChar(1, 'a'.code, CellStyle(0L))
    assertEquals(' ', row.text[0])
    assertEquals('a', row.text[1])
    assertEquals(' ', row.text[2])
    assertColumnsStartAt(0, 1, 2)
  }

  @Test
  fun writingAWideCharacterOverItselfIsIdempotent() {
    row.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    row.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    assertEquals(WIDE_ONE_CHAR_A, row.text[0].code)
    assertEquals(' ', row.text[1])
    assertColumnsStartAt(0, 0, 1)
  }

  @Test
  fun aWideCharacterInOneJavaCharStillCostsTwoColumns() {
    assertNotEquals(WIDE_ONE_CHAR_A, WIDE_ONE_CHAR_B)

    row.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    assertEquals(WIDE_ONE_CHAR_A, row.text[0].code)
    assertEquals(0, row.findStartOfColumn(1))
    assertEquals(' ', row.text[1])

    row.setChar(0, 'a'.code, CellStyle(0L))
    assertEquals('a', row.text[0])
    assertEquals(' ', row.text[1])
    assertEquals(1, row.findStartOfColumn(1))
  }

  @Test
  fun aColumnWrittenPastAWideCharacterSitsInTheJavaCharAfterIt() {
    row.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    row.setChar(2, 'a'.code, CellStyle(0L))
    assertEquals(WIDE_ONE_CHAR_A, row.text[0].code)
    assertEquals('a', row.text[1])
    assertEquals(1, row.findStartOfColumn(2))

    row.setChar(0, 'c'.code, CellStyle(0L))
    assertEquals('c', row.text[0])
    assertEquals(' ', row.text[1])
    assertEquals('a', row.text[2])
    assertColumnsStartAt(0, 1, 2)
  }

  @Test
  fun overwritingTheSecondHalfOfOneWideCharacterWithAnotherShiftsWhatFollows() {
    row.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    row.setChar(2, WIDE_ONE_CHAR_B, CellStyle(0L))
    row.setChar(4, 'a'.code, CellStyle(0L))
    // "OOAAa    "
    assertColumnsStartAt(0, 0, 1, 1, 2)
    assertEquals(WIDE_ONE_CHAR_A, row.text[0].code)
    assertEquals(WIDE_ONE_CHAR_B, row.text[1].code)
    assertEquals('a', row.text[2])
    assertEquals(' ', row.text[3])

    row.setChar(1, WIDE_ONE_CHAR_B, CellStyle(0L))
    // " AA a    "
    assertEquals(' ', row.text[0])
    assertEquals(WIDE_ONE_CHAR_B, row.text[1].code)
    assertEquals(' ', row.text[2])
    assertEquals('a', row.text[3])
    assertEquals(' ', row.text[4])
    assertColumnsStartAt(0, 1, 1, 2, 3)
  }

  /** https://github.com/jackpal/Android-Terminal-Emulator/issues/145 - this used to crash. */
  @Test
  fun aMixedRunOfUnassignedWideAndControlCodePointsDoesNotCrash() {
    assertEquals(2, Character.charCount(0xC2541))
    assertEquals(2, CharacterWidth.of(0x73EE))
    assertEquals(1, Character.charCount(0x73EE))
    assertEquals(0, CharacterWidth.of(0x009F))
    assertEquals(1, Character.charCount(0x009F))

    val codePoints = intArrayOf(
      0xC2541, 'a'.code, '8'.code, 0x73EE, 0x009F, 0x881F, 0x8324, 0xD4C9, 0xFFFD,
      'B'.code, 0x009B, 0x61C9, 'Z'.code,
    )
    var column = 0
    for (codePoint in codePoints) {
      row.setChar(column, codePoint, CellStyle(0L))
      column += CharacterWidth.of(codePoint)
    }
  }

  /** A base character followed by a combining tilde is two code points in one column. */
  @Test
  fun aCombiningTildeAfterALetterSharesItsColumn() {
    row.setChar(0, 0x006E, CellStyle(0L))
    assertEquals(80, row.spaceUsed)
    row.setChar(0, 0x0303, CellStyle(0L))
    assertEquals(81, row.spaceUsed)
    assertRowStartsWith(0x006E, 0x0303, ' '.code)
  }

  @Test
  fun theLastColumnAcceptsANarrowCharacter() {
    row.setChar(COLUMNS - 2, 'Z'.code, CellStyle(0L))
    row.setChar(COLUMNS - 1, 'a'.code, CellStyle(0L))
    assertEquals('Z', row.text[row.findStartOfColumn(COLUMNS - 2)])
    assertEquals('a', row.text[row.findStartOfColumn(COLUMNS - 1)])
    row.setChar(COLUMNS - 1, 'ö'.code, CellStyle(0L))
    assertEquals('Z', row.text[row.findStartOfColumn(COLUMNS - 2)])
    assertEquals('ö', row.text[row.findStartOfColumn(COLUMNS - 1)])
  }

  /**
   * Not upstream. `setChar` throws for the last column and truncates at the second to last, and
   * nothing in `TerminalRowTest.java` reaches either - the wide-in-last-column case is commented
   * out there. The parser wraps before it can happen, but the row is what states the rule.
   */
  @Test(expected = IllegalArgumentException::class)
  fun aWideCharacterIsRefusedInTheLastColumn() {
    row.setChar(COLUMNS - 1, WIDE_ONE_CHAR_A, CellStyle(0L))
  }

  @Test
  fun aWideCharacterAtTheSecondToLastColumnTruncatesTheRow() {
    row.setChar(COLUMNS - 2, WIDE_ONE_CHAR_A, CellStyle(0L))
    assertEquals(WIDE_ONE_CHAR_A, row.text[row.findStartOfColumn(COLUMNS - 2)].code)
    assertEquals(COLUMNS - 1, row.spaceUsed)
  }

  /** Not upstream: the write is out of range, and the message has to name what was asked for. */
  @Test
  fun aColumnOutsideTheRowIsRefusedWithItsOwnValue() {
    val failure = runCatching { row.setChar(COLUMNS, 'a'.code, CellStyle(0L)) }.exceptionOrNull()
    assertTrue("expected IllegalArgumentException, got $failure", failure is IllegalArgumentException)
    assertTrue("message must name the column: ${failure?.message}", failure?.message?.contains("80") == true)
  }

  /** Not upstream as a test: the cap that stops a stream of combining marks growing a row forever. */
  @Test
  fun aColumnTakesAtMostFifteenCombiningCharacters() {
    repeat(20) { row.setChar(0, DIAERESIS, CellStyle(0L)) }
    assertEquals(COLUMNS + TerminalRow.MAX_COMBINING_CHARACTERS_PER_COLUMN, row.spaceUsed)
  }

  @Test
  fun clearingResetsEveryColumnToSpacesAndTheGivenStyle() {
    row.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    row.setChar(3, DIAERESIS, CellStyle(0L))
    row.clear(CellStyle.NORMAL)
    assertEquals(COLUMNS, row.spaceUsed)
    assertTrue(row.isBlank())
    assertColumnsStartAt(0, 1, 2, 3)
    assertEquals(CellStyle.NORMAL, row.styleAt(0))
  }

  @Test
  fun aRowWithAnythingButSpacesIsNotBlank() {
    assertTrue(row.isBlank())
    row.setChar(5, 'x'.code, CellStyle(0L))
    assertTrue(!row.isBlank())
  }

  @Test
  fun aStyleIsRememberedPerColumn() {
    val bold = CellStyle.encode(2, 3, CellStyle.BOLD)
    row.setChar(1, 'x'.code, bold)
    assertEquals(bold, row.styleAt(1))
    assertEquals(CellStyle.NORMAL, row.styleAt(0))
  }

  @Test
  fun copyingAnIntervalReproducesItsColumnsAtTheDestination() {
    val source = TerminalRow(COLUMNS, CellStyle.NORMAL)
    source.setChar(0, 'a'.code, CellStyle(0L))
    source.setChar(1, WIDE_ONE_CHAR_A, CellStyle(0L))
    source.setChar(3, 'b'.code, CellStyle(0L))

    row.copyInterval(source, 0, 4, 0)
    assertRowStartsWith('a'.code, WIDE_ONE_CHAR_A, 'b'.code)
    assertColumnsStartAt(0, 1, 1, 2, 3)
  }

  /** Copying from the second half of a wide character copies whitespace, not half a glyph. */
  @Test
  fun copyingFromTheSecondHalfOfAWideCharacterYieldsASpace() {
    val source = TerminalRow(COLUMNS, CellStyle.NORMAL)
    source.setChar(0, WIDE_ONE_CHAR_A, CellStyle(0L))
    source.setChar(2, 'b'.code, CellStyle(0L))

    row.copyInterval(source, 1, 3, 0)
    assertRowStartsWith(' '.code, 'b'.code, ' '.code)
  }

  private companion object {
    const val COLUMNS = 80

    /** One java char, display width two. */
    const val WIDE_ONE_CHAR_A = 0x679C
    const val WIDE_ONE_CHAR_B = 0x679D

    /** A surrogate pair, display width two. */
    const val WIDE_SURROGATE_A = 0x2070E
    const val WIDE_SURROGATE_B = 0x20731

    /** MUSICAL SYMBOL G CLEF and G CLEF OTTAVA ALTA: a surrogate pair, display width one. */
    const val NARROW_SURROGATE_A = 0x1D11E
    const val NARROW_SURROGATE_B = 0x1D11F

    /** COMBINING DIAERESIS: one java char, display width zero. */
    const val DIAERESIS = 0x0308

    const val UNICODE_REPLACEMENT_CHAR = 0xFFFD

    /** MUSICAL SYMBOL G CLEF and its OTTAVA ALTA: surrogate pairs of display width one. */
    const val G_CLEF = 0x1D306
    const val G_CLEF_OTTAVA = 0x1D307

    /** Fixed so a failing run can be repeated; upstream leaves this to `new Random()`. */
    const val FUZZ_SEED = 20260829L
  }
}
