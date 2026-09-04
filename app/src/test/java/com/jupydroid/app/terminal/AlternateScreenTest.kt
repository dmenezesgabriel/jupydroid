package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The second screen buffer: `CSI ? 47`/`1047`/`1049` switch to it and back, and `CSI ? 1048` saves
 * and restores the cursor without switching at all.
 *
 * Transcribed from the alternate-screen halves of `CursorAndScreenTest.testCursorSaveRestoreLocation`
 * and `testCursorSaveRestoreTextStyle`, `TerminalTest.testScrollDownInAltBuffer` and
 * `ControlSequenceIntroducerTest.testCsi3J`'s third case - which `ScreenEditingTest` deferred here
 * by name (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]).
 *
 * The seven the reference covers only incidentally had their **expectations read off the reference
 * itself** (`evidence/p4-terminal/emulator-differential.md`, "The oracle the expectations came
 * from"): that `47` and `1047` do what `1049` does, that `1048` does not switch, that a second
 * entry changes nothing, that a saved cursor belongs to the buffer it was saved in, that the main
 * screen's scrollback survives a session in the alternate one, and that a reset inside the
 * alternate screen stays there and forgets *both* saved cursors.
 *
 * **The last two exist because the differential cannot reach them**: `ESC c` is excluded from its
 * alphabet as a declared divergence, so three mutations survive it, and each is pinned here and run
 * against its own mutation to watch it fail.
 */
class AlternateScreenTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  private fun cursorOf(fixture: EmulatorFixture): Pair<Int, Int> =
    fixture.emulator.screen.cursor.let { it.row to it.column }

  private fun styleAt(fixture: EmulatorFixture, row: Int, column: Int): CellStyle =
    fixture.emulator.screen.grid.styleAt(row, column)

  /**
   * `CursorAndScreenTest.testCursorSaveRestoreLocation`, third case. Entering saves the cursor and
   * blanks the alternate screen; leaving puts the cursor back where the entry found it.
   */
  @Test
  fun enteringSavesTheCursorAndLeavingPutsItBack() {
    terminal(4, 2)
      .enterString("t$ESC[?1049h$ESC[Hest\r\nme")
      .assertLinesAre("est ", "me  ")
      .enterString("$ESC[?1049lry")
      .assertLinesAre("try ", "    ")
  }

  /**
   * A **second** entry, after a leave, blanks whatever the *first* session left behind - `blockClear`
   * runs on every entry, not only the first, so old content never resurfaces. Nothing in this suite
   * pinned that before: `enteringSavesTheCursorAndLeavingPutsItBack` above never leaves and
   * re-enters, and `aSecondEntryChangesNothing` below is a **repeated** entry with no leave between,
   * which is the one case `useAlternateScreen` deliberately skips the blank for. Found as a mutation
   * survivor while widening P4h-3b's differential - confirmed to predate P4h-3 entirely, so this is
   * `docs/DECISIONS.md`'s "A resize followed by a buffer switch..." entry's own sibling gap, not a
   * behaviour change - `evidence/p4-terminal/a-resize-then-a-buffer-switch-reaches-p4h-4s-gap.md`.
   */
  @Test
  fun reenteringTheAlternateScreenBlanksContentAnEarlierSessionLeft() {
    val fixture = terminal(4, 2).enterString("$ESC[?1049hABCD")
    fixture.assertLinesAre("ABCD", "    ")
    fixture.enterString("$ESC[?1049l").enterString("$ESC[?1049h")
    fixture.assertLinesAre("    ", "    ")
  }

  /** `CursorAndScreenTest.testCursorSaveRestoreTextStyle`, third case: the style travels with it. */
  @Test
  fun enteringSavesTheStyleAndLeavingPutsItBack() {
    val fixture = terminal(4, 2).enterString("$ESC[31;42;4m..$ESC[?1049h$ESC[H$ESC[36;47;24m.")
    assertEquals(6, styleAt(fixture, 0, 0).foreColor)
    assertEquals(7, styleAt(fixture, 0, 0).backColor)
    assertEquals(0, styleAt(fixture, 0, 0).effect)

    fixture.enterString("$ESC[?1049l..")
    assertEquals(1, styleAt(fixture, 0, 3).foreColor)
    assertEquals(2, styleAt(fixture, 0, 3).backColor)
    assertEquals(CellStyle.UNDERLINE, styleAt(fixture, 0, 3).effect)
  }

  /**
   * `TerminalTest.testScrollDownInAltBuffer`. The alternate screen is built with no room for a
   * transcript, so what scrolls off it is gone rather than filed.
   */
  @Test
  fun theAlternateScreenScrollsWithoutKeepingWhatLeavesIt() {
    val fixture = terminal(3, 3).enterString("$ESC[?1049h")
    fixture.enterString("$ESC[38;5;111m1\r\n$ESC[38;5;112m2\r\n$ESC[38;5;113m3\r\n")
    fixture.enterString("$ESC[38;5;114m4\r\n$ESC[38;5;115m5").assertLinesAre("3  ", "4  ", "5  ")
    assertEquals(113, styleAt(fixture, 0, 0).foreColor)
    assertEquals(114, styleAt(fixture, 1, 0).foreColor)
    assertEquals(115, styleAt(fixture, 2, 0).foreColor)
    assertEquals(0, fixture.emulator.screen.grid.activeTranscriptRows)
  }

  /** And the main screen's scrollback survives a whole session in the alternate one. */
  @Test
  fun scrollingTheAlternateScreenLeavesTheMainScrollbackAlone() {
    val fixture = terminal(3, 2).enterString("a\r\nb\r\nc\r\nd$ESC[?1049hP\r\nQ\r\nR\r\nS$ESC[?1049l")
    fixture.assertLinesAre("c  ", "d  ")
    assertEquals("a\nb\nc\nd", fixture.emulator.screen.grid.transcriptText)
  }

  /** `47` and `1047` are the same arm as `1049` upstream, so all three behave identically. */
  @Test
  fun theOlderCodesSwitchTheSameBuffer() {
    terminal(4, 2).enterString("t$ESC[?47h$ESC[Hest\r\nme$ESC[?47lry").assertLinesAre("try ", "    ")
    terminal(4, 2).enterString("t$ESC[?1047h$ESC[Hest\r\nme$ESC[?1047lry").assertLinesAre("try ", "    ")
  }

  /** `1048` is DECSC and DECRC alone: no switch, so what was typed in between stays. */
  @Test
  fun tenFortyEightSavesTheCursorWithoutSwitchingBuffer() {
    val fixture = terminal(4, 2).enterString("t$ESC[?1048h$ESC[Hest\r\nme$ESC[?1048lry")
    fixture.assertLinesAre("ery ", "me  ")
    assertEquals(0 to 3, cursorOf(fixture))
  }

  /**
   * A switch to the buffer already live is dropped before the save and the blanking, so a second
   * entry neither re-saves the cursor nor wipes what the first one drew.
   */
  @Test
  fun aSecondEntryChangesNothing() {
    val fixture = terminal(4, 2).enterString("t$ESC[?1049h$ESC[HAB$ESC[?1049h")
    fixture.assertLinesAre("AB  ", "    ")
    assertEquals(0 to 2, cursorOf(fixture))
  }

  /**
   * **There is one saved cursor per buffer.** A DECSC inside the alternate screen must not be what
   * a DECRC back on the main screen finds - here the alternate save is at row 1 column 3, and the
   * restore that follows the exit lands at the origin, where the main screen's own save left it.
   */
  @Test
  fun theSavedCursorBelongsToTheBufferItWasSavedIn() {
    val inAlternate = "${ESC}7$ESC[?1049h$ESC[2;3HX${ESC}7"
    val fixture = terminal(6, 2).enterString("$inAlternate$ESC[H$ESC[?1049lY${ESC}8Z")
    fixture.assertLinesAre("Z     ", "      ")
    assertEquals(0 to 1, cursorOf(fixture))
  }

  /**
   * **The transcript belongs to the main screen, whichever buffer is live.** `CSI 3 J` from inside
   * the alternate screen clears the main one's scrollback, because upstream that arm names
   * `mMainBuffer` rather than the current pointer. This is `testCsi3J`'s third case.
   */
  @Test
  fun clearingTheScrollbackFromTheAlternateScreenClearsTheMainOne() {
    val fixture = terminal(3, 2).enterString("w\r\nx\r\ny\r\nz$ESC[?1049h$ESC[3J$ESC[?1049l")
    assertEquals("y\nz", fixture.emulator.screen.grid.transcriptText)
  }

  /**
   * RIS inside the alternate screen **stays there** - the reference's `reset()` clears both saved
   * cursors and leaves the buffer pointer where it is - and its scrollback clear, like `3 J`'s,
   * reaches the main screen.
   */
  @Test
  fun aResetInsideTheAlternateScreenStaysThereAndClearsTheMainScrollback() {
    val fixture = terminal(4, 2).enterString("ab\r\ncd$ESC[?1049hZ${ESC}cW")
    fixture.assertLinesAre("W   ", "    ")
    fixture.enterString("$ESC[?1049l").assertLinesAre("ab  ", "cd  ")
    assertEquals(0 to 0, cursorOf(fixture))

    val cleared = terminal(3, 2).enterString("a\r\nb\r\nc\r\nd$ESC[?1049h${ESC}c$ESC[?1049l")
    assertEquals("c\nd", cleared.emulator.screen.grid.transcriptText)
  }

  /**
   * And it forgets **both** saved cursors, not just the live buffer's. A DECSC inside the
   * alternate screen followed by a RIS and a DECRC lands at the origin; without the RIS the same
   * input lands back on row 1 column 2, which is the difference this pins.
   *
   * **The differential cannot reach this**, because `ESC c` is excluded from its alphabet as a
   * declared divergence - so it is closed the way P4f-3c closed its survivors: expectations read
   * off `RefScreen`, and the test run against its own mutation to watch it fail.
   */
  @Test
  fun aResetForgetsTheAlternateScreensSavedCursorAsWell() {
    val fixture = terminal(4, 3).enterString("$ESC[?1049h$ESC[2;3H${ESC}7${ESC}c${ESC}8X")
    fixture.assertLinesAre("X   ", "    ", "    ")
    assertEquals(0 to 1, cursorOf(fixture))

    val kept = terminal(4, 3).enterString("$ESC[?1049h$ESC[2;3H${ESC}7$ESC[H${ESC}8X")
    kept.assertLinesAre("    ", "  X ", "    ")
  }

  private companion object {
    const val ESC = "\u001B"
  }
}
