package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The control sequences that erase and edit: ED and EL, ECH, ICH and DCH, IL and DL, SU and SD,
 * and REP.
 *
 * `CursorAndScreenTest`'s `testDeleteLinesKeepsStyles` / `testDeleteCharacters` /
 * `testClearingOfAutowrap`, `ControlSequenceIntroducerTest`'s `testCsiS` / `testCsiT` / `testCsiX` /
 * `testRepeat` / `testCsi3J`, and `TerminalTest.testBackgroundColorErase` are transcribed here
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]). `testCsi3J`'s third case needs the
 * alternate buffer and goes with **P4f-4**.
 *
 * The parameters upstream never exercises - `ED 1`, `ED 2`, `EL 1`, `EL 2` and ICH's cap - had
 * their **expectations read off the reference itself**
 * ("The oracle the expectations came from").
 */
class ScreenEditingTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  private fun styleAt(fixture: EmulatorFixture, row: Int, column: Int): CellStyle =
    fixture.emulator.screen.grid.styleAt(row, column)

  /** `CSI Pn X`, from `ControlSequenceIntroducerTest.testCsiX`. */
  @Test
  fun eraseCharactersBlanksInPlaceWithoutMovingWhatFollows() {
    val backUp = "\b\b\b\b\b"
    terminal(13, 2).enterString("abcdefghijkl$backUp$ESC[X").assertLineIs(0, "abcdefg ijkl ")
    terminal(13, 2).enterString("abcdefghijkl$backUp$ESC[1X").assertLineIs(0, "abcdefg ijkl ")
    terminal(13, 2).enterString("abcdefghijkl$backUp$ESC[2X").assertLineIs(0, "abcdefg  jkl ")
    terminal(13, 2).enterString("abcdefghijkl$backUp$ESC[20X").assertLineIs(0, "abcdefg      ")
  }

  /** `ControlSequenceIntroducerTest.testCsiS` - the OS X Terminal.app reading of SU. */
  @Test
  fun scrollUpMovesTheScreenIntoTheTranscript() {
    terminal(3, 4).enterString("1\r\n2\r\n3\r\nhi$ESC[2Sy").assertLinesAre("3  ", "hi ", "   ", "  y")
    terminal(3, 4).enterString("1\r\n2\r\n3\r\nhi$ESC[Sy").assertLinesAre("2  ", "3  ", "hi ", "  y")
  }

  /** `ControlSequenceIntroducerTest.testCsiT`. */
  @Test
  fun scrollDownPushesTheScreenAwayFromTheTopMargin() {
    terminal(4, 6)
      .enterString("1\r\n2\r\n3\r\nhi$ESC[2Tyo\r\nA\r\nB")
      .assertLinesAre("    ", "    ", "1   ", "2 yo", "A   ", "Bi  ")
    terminal(4, 6)
      .enterString("1\r\n2\r\n3\r\nhi$ESC[Tyo\r\nA\r\nB")
      .assertLinesAre("    ", "1   ", "2   ", "3 yo", "Ai  ", "B   ")
  }

  /** `ControlSequenceIntroducerTest.testRepeat`. */
  @Test
  fun repeatDrawsTheLastCodePointEmittedAndNotTheOneUnderTheCursor() {
    terminal(3, 2).enterString("a$ESC[b").assertLineIs(0, "aa ")
    terminal(3, 2).enterString("a$ESC[2b").assertLineIs(0, "aaa")
    terminal(3, 2).enterString("$ESC[b").assertLineIs(0, "   ")
    terminal(5, 2).enterString("abcde$ESC[2G$ESC[2b\n").assertLineIs(0, "aeede")
  }

  /** `ControlSequenceIntroducerTest.testCsi3J`; its alternate-buffer case is P4f-4's. */
  @Test
  fun eraseInDisplayThreeForgetsTheScrollbackAndKeepsTheScreen() {
    val scrolled = terminal(3, 2).enterString("a\r\nb\r\nc\r\nd")
    assertEquals("a\nb\nc\nd", scrolled.emulator.screen.grid.transcriptText)
    scrolled.enterString("$ESC[3J")
    assertEquals("c\nd", scrolled.emulator.screen.grid.transcriptText)

    val wrapped = terminal(3, 2).enterString("Lorem_ipsum")
    assertEquals("Lorem_ipsum", wrapped.emulator.screen.grid.transcriptText)
    wrapped.enterString("$ESC[3J")
    assertEquals("ipsum", wrapped.emulator.screen.grid.transcriptText)
  }

  /** `ED 0` is the default; `1` erases back to the start and `2` the whole screen. */
  @Test
  fun eraseInDisplayTakesTheHalfItIsAskedFor() {
    terminal(3, 3).enterString("abc\r\ndef\r\nghi$ESC[2;2H$ESC[1J").assertLinesAre("   ", "  f", "ghi")
    terminal(3, 3).enterString("abc\r\ndef$ESC[2;2H$ESC[2J").assertLinesAre("   ", "   ", "   ")
  }

  /** The same three parameters for one line. */
  @Test
  fun eraseInLineTakesTheHalfItIsAskedFor() {
    terminal(5, 2).enterString("abcde$ESC[3G$ESC[1K").assertLineIs(0, "   de")
    terminal(5, 2).enterString("abcde$ESC[3G$ESC[2K").assertLineIs(0, "     ")
  }

  /** ICH opens blanks and pushes the rest of the row right, dropping what passes the last column. */
  @Test
  fun insertCharactersPushesTheRestOfTheRowRight() {
    terminal(5, 2).enterString("nice$ESC[2G$ESC[2@").assertLineIs(0, "n  ic")
    terminal(5, 2).enterString("nice$ESC[G$ESC[99@").assertLineIs(0, "     ")
  }

  /** `CursorAndScreenTest.testDeleteCharacters`, including the wide and combining cases. */
  @Test
  fun deleteCharactersPullsTheRestOfTheRowLeft() {
    terminal(5, 2).enterString("nice$ESC[G$ESC[P").assertLineIs(0, "ice  ")
    terminal(5, 2).enterString("nice$ESC[G$ESC[2P").assertLineIs(0, "ce   ")
    terminal(5, 2).enterString("nice$ESC[2G$ESC[2P").assertLineIs(0, "ne   ")
    terminal(5, 2).enterString("nice$ESC[G$ESC[99P").assertLineIs(0, "     ")
    terminal(5, 2).enterString("n${COMBINING_CIRCUMFLEX}ice$ESC[G$ESC[2P").assertLineIs(0, "ce   ")
    terminal(5, 2).enterString("n${COMBINING_CIRCUMFLEX}ice$ESC[G$ESC[P").assertLineIs(0, "ice  ")
    terminal(5, 2)
      .enterString("n${COMBINING_CIRCUMFLEX}ice$ESC[2G$ESC[2P")
      .assertLineIs(0, "n\u0302e   ")
  }

  /** Deleting either half of a wide character replaces the other with a blank. */
  @Test
  fun deletingHalfAWideCharacterBlanksTheOtherHalf() {
    terminal(5, 2).enterString("枝ce$ESC[G$ESC[P").assertLineIs(0, " ce  ")
    terminal(5, 2).enterString("枝ce$ESC[2G$ESC[P").assertLineIs(0, " ce  ")
    terminal(5, 2).enterString("枝ce$ESC[2G$ESC[2P").assertLineIs(0, " e   ")
    terminal(5, 2).enterString("枝ce$ESC[G$ESC[2P").assertLineIs(0, "ce   ")
    terminal(5, 2).enterString("a枝ce$ESC[G$ESC[P").assertLineIs(0, "枝ce ")
  }

  /**
   * `CursorAndScreenTest.testClearingOfAutowrap`, adapted from hterm: each of these six sequences
   * modifies the screen and **clears the pending wrap**, so the hyphen after it fixes the row
   * instead of wrapping onto the next one.
   */
  @Test
  fun theEditsClearThePendingWrapSoTheNextCharacterFixesTheRow() {
    terminal(15, 6)
      .enterString("-----  1  ----X$ESC[K-")
      .enterString("-----  2  ----X$ESC[J-")
      .enterString("-----  3  ----X$ESC[@-")
      .enterString("-----  4  ----X$ESC[P-")
      .enterString("-----  5  ----X$ESC[X-")
      .enterString("XXXXXXXXXXXXXXX$ESC[M-")
      .assertLinesAre(
        "-----  1  -----",
        "-----  2  -----",
        "-----  3  -----",
        "-----  4  -----",
        "-----  5  -----",
        "              -",
      )
  }

  /**
   * A 5x5 grid whose every cell carries its column as a foreground and its row as a background,
   * so that a row moving is visible in the colours and not only in the letters.
   */
  private fun colouredGrid(): EmulatorFixture {
    val fixture = terminal(5, 5)
    for (row in 0 until 5) {
      for (column in 0 until 5) {
        fixture.enterString("$ESC[38;5;${column}m$ESC[48;5;${row}m${'A' + column + row * 5}")
      }
    }
    return fixture.assertLinesAre("ABCDE", "FGHIJ", "KLMNO", "PQRST", "UVWXY")
  }

  private fun assertStyleAt(fixture: EmulatorFixture, row: Int, column: Int, fore: Int, back: Int) {
    assertEquals(fore, styleAt(fixture, row, column).foreColor)
    assertEquals(back, styleAt(fixture, row, column).backColor)
  }

  /** `CursorAndScreenTest.testDeleteLinesKeepsStyles`: a row's colours move with the row. */
  @Test
  fun deletingLinesCarriesTheStylesOfTheRowsThatMoveUp() {
    val fixture = colouredGrid().enterString("$ESC[H$ESC[2M")
    fixture.assertLinesAre("KLMNO", "PQRST", "UVWXY", "     ", "     ")
    for (row in 0 until 3) {
      for (column in 0 until 5) assertStyleAt(fixture, row, column, fore = column, back = row + 2)
    }
  }

  /** The rows IL opens are blanked to the style in force, not to the one they displaced. */
  @Test
  fun insertingLinesPaintsTheRowsItOpensWithTheStyleInForce() {
    val fixture = colouredGrid().enterString("$ESC[H$ESC[2M")
    fixture.enterString("$ESC[38;5;98m$ESC[48;5;99m$ESC[B$ESC[2L")
    fixture.assertLinesAre("KLMNO", "     ", "     ", "PQRST", "UVWXY")
    for (row in 0 until 5) {
      val opened = row == 1 || row == 2
      val back = when {
        opened -> 99
        row == 0 -> 2
        else -> row
      }
      for (column in 0 until 5) {
        assertStyleAt(fixture, row, column, fore = if (opened) 98 else column, back = back)
      }
    }
  }

  /** `TerminalTest.testBackgroundColorErase`: what an erase blanks a cell *to* is the current style. */
  @Test
  fun anEraseBlanksToTheColoursInForceRatherThanToTheDefaults() {
    val fixture = terminal(3, 3).enterString("$ESC[38;5;119m$ESC[48;5;129m$ESC[2J")
    for (row in 0 until 3) {
      for (column in 0 until 3) {
        assertEquals(119, styleAt(fixture, row, column).foreColor)
        assertEquals(129, styleAt(fixture, row, column).backColor)
      }
    }

    fixture.enterString("$ESC[48;5;139m$ESC[2L")
    for (row in 0 until 3) {
      val opened = row == 0 || row == 1
      for (column in 0 until 3) assertEquals(if (opened) 139 else 129, styleAt(fixture, row, column).backColor)
    }

    val erased = terminal(3, 3).enterString("$ESC[48;5;129m$ESC[2X")
    assertEquals(129, styleAt(erased, 0, 0).backColor)
    assertEquals(129, styleAt(erased, 0, 1).backColor)
    assertEquals(CellStyle.COLOR_INDEX_BACKGROUND, styleAt(erased, 0, 2).backColor)
  }

  /**
   * IL is the one edit that **leaves the pending wrap set**, so the character after it wraps onto
   * the row below instead of fixing the row - where DL and ICH, whose shape is otherwise
   * identical, clear it. `CursorAndScreenTest.testClearingOfAutowrap` covers only the six that
   * clear, and the differential cannot see this either: it needs a filled row, then IL, then a
   * printable character, in that order, which its random alphabet did not draw in 12,000 steps
   * ("the two the script did not reach").
   */
  @Test
  fun insertLinesLeavesThePendingWrapWhereDeleteLinesAndInsertCharactersClearIt() {
    terminal(5, 3).enterString("abcde$ESC[L-").assertLinesAre("     ", "-bcde", "     ")
    terminal(5, 3).enterString("abcde$ESC[M-").assertLinesAre("    -", "     ", "     ")
    terminal(5, 3).enterString("abcde$ESC[@-").assertLinesAre("abcd-", "     ", "     ")
  }

  /**
   * REP stores the code point **before** the graphic set substituted for it, and draws it through
   * the set in force when the repeat runs - so a switch back to ASCII between the character and
   * the repeat draws the letter, not the glyph. Expectations read off the reference
   * (`harness/RefScreen.java`: `ESC ( 0 a ESC ( B ESC [ b` gives `\u2592a`).
   */
  @Test
  fun repeatDrawsThroughTheGraphicSetInForceWhenItRunsRatherThanTheGlyphItDrew() {
    terminal(5, 2).enterString("$ESC(0a$ESC(B$ESC[b").assertLineIs(0, "\u2592a   ")
    terminal(5, 2).enterString("$ESC(0a$ESC[b").assertLineIs(0, "\u2592\u2592   ")
    terminal(5, 2).enterString("$ESC(0a$ESC(B$ESC[2b").assertLineIs(0, "\u2592aa  ")
  }

  /**
   * **ED and EL clear the pending wrap only for a parameter they understood.** Upstream the clear
   * is the line after the inner `switch` and the unknown arm `return`s before reaching it, so
   * `CSI 9 J` leaves the wrap standing where `CSI 0 J` takes it down. Expectations read off the
   * reference (`harness/RefScreen.java`).
   *
   * P4f-3c left this to the differential, which detected it then and **stopped detecting it at
   * P4f-4a** - not because anything broke, but because a widened alphabet reshuffles the random
   * stream and this conjunction stopped coming up. A test does not drift that way.
   */
  @Test
  fun anEraseWithAParameterItDoesNotUnderstandLeavesThePendingWrapStanding() {
    terminal(5, 3).enterString("abcde$ESC[9J-").assertLinesAre("abcde", "-    ", "     ")
    terminal(5, 3).enterString("abcde$ESC[0J-").assertLinesAre("abcd-", "     ", "     ")
    terminal(5, 3).enterString("abcde$ESC[9K-").assertLinesAre("abcde", "-    ", "     ")
    terminal(5, 3).enterString("abcde$ESC[0K-").assertLinesAre("abcd-", "     ", "     ")
  }

  private companion object {
    const val ESC = "\u001B"
    const val COMBINING_CIRCUMFLEX = "\u0302"
  }
}
