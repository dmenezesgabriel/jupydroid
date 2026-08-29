package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [RowText] has no counterpart upstream - it is the half of `TerminalRow.java` that is about the
 * char array rather than the cell, split out so neither half exceeds this project's limits. These
 * tests drive that seam directly; `TerminalRowTest` drives it through the row.
 */
class RowTextTest {
  private lateinit var packed: RowText

  @Before
  fun setUp() {
    packed = RowText(COLUMNS)
    packed.blank()
  }

  /** Write a code point at [index] the way [TerminalRow] does, without going through a row. */
  private fun put(index: Int, codePoint: Int) {
    val oldCount = charCountAt(packed.chars, index)
    val end = packed.splice(index, oldCount, Character.charCount(codePoint))
    Character.toChars(codePoint, packed.chars, index)
    if (CharacterWidth.of(codePoint) == 2) packed.dropCodePointAt(end)
  }

  @Test
  fun aBlankedRowIsOneSpacePerColumn() {
    assertEquals(COLUMNS, packed.used)
    assertTrue(packed.isBlank())
    for (column in 0..COLUMNS) assertEquals(column, packed.findStartOfColumn(column))
  }

  @Test
  fun anythingButASpaceMakesTheRowNotBlank() {
    put(3, 'x'.code)
    assertFalse(packed.isBlank())
  }

  @Test
  fun theColumnPastTheLastOneIsTheEndOfTheText() {
    assertEquals(packed.used, packed.findStartOfColumn(COLUMNS))
    put(0, WIDE)
    assertEquals(packed.used, packed.findStartOfColumn(COLUMNS))
  }

  @Test
  fun aSurrogatePairShiftsEveryColumnAfterItByOneJavaChar() {
    put(0, G_CLEF)
    assertEquals(COLUMNS + 1, packed.used)
    assertEquals(0, packed.findStartOfColumn(0))
    assertEquals(2, packed.findStartOfColumn(1))
    assertEquals(3, packed.findStartOfColumn(2))
  }

  @Test
  fun aWideCharacterCoversTwoColumnsFromOneStart() {
    put(0, WIDE)
    assertEquals(0, packed.findStartOfColumn(0))
    assertEquals(0, packed.findStartOfColumn(1))
    assertEquals(1, packed.findStartOfColumn(2))
    assertTrue(packed.startsWideCharacterAt(0))
    assertFalse(packed.startsWideCharacterAt(1))
    assertFalse(packed.startsWideCharacterAt(2))
  }

  @Test
  fun aCombiningMarkBelongsToTheColumnBeforeItAndTheNextColumnSkipsIt() {
    packed.splice(1, 1, 2)
    packed.chars[1] = 'e'
    packed.chars[2] = DIAERESIS.toChar()
    assertEquals(1, packed.findStartOfColumn(1))
    assertEquals(3, packed.findStartOfColumn(2))
  }

  @Test
  fun splicingWiderShiftsTheTailRightAndSplicingNarrowerPullsItBack() {
    packed.chars[5] = 'Z'
    // splice returns the column's new end: columnStart + newCharsUsed.
    assertEquals(4, packed.splice(1, 1, 3))
    assertEquals(COLUMNS + 2, packed.used)
    assertEquals('Z', packed.chars[7])

    assertEquals(2, packed.splice(1, 3, 1))
    assertEquals(COLUMNS, packed.used)
    assertEquals('Z', packed.chars[5])
  }

  /** Growing past the spare capacity has to copy both sides of the splice, not only the tail. */
  @Test
  fun aSpliceBeyondTheSpareCapacityGrowsTheArrayAndKeepsBothSides() {
    packed.chars[0] = 'A'
    packed.chars[COLUMNS - 1] = 'Z'
    val capacity = packed.chars.size
    repeat(capacity) { packed.splice(1, 0, 1) }
    assertTrue("the array must have grown past $capacity", packed.chars.size > capacity)
    assertEquals('A', packed.chars[0])
    assertEquals('Z', packed.chars[packed.used - 1])
  }

  @Test
  fun insertingASpaceOpensOneCharAndDroppingACodePointClosesIt() {
    packed.chars[2] = 'Z'
    packed.insertSpaceAt(2)
    assertEquals(COLUMNS + 1, packed.used)
    assertEquals(' ', packed.chars[2])
    assertEquals('Z', packed.chars[3])

    packed.dropCodePointAt(2)
    assertEquals(COLUMNS, packed.used)
    assertEquals('Z', packed.chars[2])
  }

  /** A surrogate pair is one code point, so dropping it takes both of its java chars. */
  @Test
  fun droppingACodePointTakesBothHalvesOfASurrogatePair() {
    put(0, G_CLEF)
    val usedWithThePair = packed.used
    packed.dropCodePointAt(0)
    assertEquals("both java chars of the pair go, not one", usedWithThePair - 2, packed.used)
    assertEquals(' ', packed.chars[0])
  }

  @Test
  fun truncatingEndsTheRowWhereItIsAsked() {
    packed.truncateTo(COLUMNS - 1)
    assertEquals(COLUMNS - 1, packed.used)
    assertEquals(COLUMNS - 1, packed.findStartOfColumn(COLUMNS))
  }

  @Test
  fun charsUsedForAColumnIsWhatSeparatesItFromTheNextAndTheLastRunsToTheEnd() {
    put(0, G_CLEF)
    assertEquals(2, packed.charsUsedForColumn(0, 0, 1))
    assertEquals(1, packed.charsUsedForColumn(1, 2, 1))
    val lastStart = packed.findStartOfColumn(COLUMNS - 1)
    assertEquals(packed.used - lastStart, packed.charsUsedForColumn(COLUMNS - 1, lastStart, 1))
  }

  private companion object {
    const val COLUMNS = 10

    /** One java char, display width two. */
    const val WIDE = 0x679C

    /** MUSICAL SYMBOL G CLEF: a surrogate pair of display width one. */
    const val G_CLEF = 0x1D306

    const val DIAERESIS = 0x0308
  }
}
