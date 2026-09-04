package com.jupydroid.app.terminal

import org.junit.Test

/**
 * What a shell gets back when it asks: device attributes, status reports, the DEC mode query and
 * the window operations that report a size.
 *
 * `TerminalTest.testDeviceStatusReport` is transcribed whole; `testReportTerminalSize` and
 * `ControlSequenceIntroducerTest.testReportPixelSize` are transcribed **as far as their first
 * resize**, which is P4h's (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]). Everything
 * else had its expectations read off `RefScreen`, which prints what the emulator wrote back.
 *
 * **This is the first direction `terminal/` writes in.** P4f-1 split [TerminalNotices] off the
 * reference's single `TerminalOutput` so that this half could arrive with its own interface, and
 * [TerminalReplies] is it.
 */
class TerminalReportingTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  /** `testDeviceStatusReport`: DSR answers "terminal OK" and CPR answers where the cursor is. */
  @Test
  fun statusReportsAnswerHealthAndCursorPosition() {
    val fixture = terminal(5, 5)
    fixture.assertReplyTo("$ESC[5n", "$ESC[0n")
    fixture.assertReplyTo("$ESC[6n", "$ESC[1;1R")
    fixture.enterString("AB")
    fixture.assertReplyTo("$ESC[6n", "$ESC[1;3R")
    fixture.enterString("\r\n")
    fixture.assertReplyTo("$ESC[6n", "$ESC[2;1R")
  }

  /**
   * `testReportTerminalSize`, whole: the fixed 5x5 case, then every width and height from 3 to
   * 11 - P4h-3's own `resize` closes the loop this used to defer.
   */
  @Test
  fun theTextAreaIsReportedInCharacters() {
    terminal(5, 5).assertReplyTo("$ESC[19t", "$ESC[9;5;5t")

    val fixture = terminal(5, 5)
    fixture.assertReplyTo("$ESC[18t", "$ESC[8;5;5t")
    for (width in 3..11) {
      for (height in 3..11) {
        fixture.resize(width, height)
        fixture.assertReplyTo("$ESC[18t", "$ESC[8;$height;${width}t")
      }
    }
  }

  /**
   * `testReportPixelSize`, whole: the reference's own default cell of 13 by 15, its `resize(23, 33)`
   * leg, and its third - a cell-pixel-only resize, `mTerminal.resize(columns, rows, 8, 18)` with
   * columns/rows unchanged - which is P4i-2b's own entry point (`EmulatorFixture.resize`'s new
   * optional pair). The reports are `rows * cellHeight` by `columns * cellWidth`, in that order.
   */
  @Test
  fun theWindowAndCellAreReportedInPixels() {
    val fixture = terminal(3, 3)
    fixture.assertReplyTo("$ESC[14t", "$ESC[4;45;39t")
    fixture.assertReplyTo("$ESC[16t", "$ESC[6;15;13t")
    fixture.resize(23, 33)
    fixture.assertReplyTo("$ESC[14t", "$ESC[4;495;299t")
    fixture.assertReplyTo("$ESC[16t", "$ESC[6;15;13t")
    fixture.resize(23, 33, cellWidthPixels = 8, cellHeightPixels = 18)
    fixture.assertReplyTo("$ESC[14t", "$ESC[4;594;184t")
    fixture.assertReplyTo("$ESC[16t", "$ESC[6;18;8t")
  }

  /** The window state and position are fixed answers; a parameter nothing claims is silent. */
  @Test
  fun theFixedWindowReportsAnswerAndAnUnknownOneIsSilent() {
    terminal(5, 5).assertReplyTo("$ESC[11t", "$ESC[1t")
    terminal(5, 5).assertReplyTo("$ESC[13t", "$ESC[3;0;0t")
    terminal(5, 5).assertReplyTo("$ESC[99t", "")
  }

  /**
   * DA1 answers only for parameter `0` or an absent one - `CSI 1 c` is silent - and DA2 carries
   * xterm's release number, which `tmux` and `vim` feature-check.
   */
  @Test
  fun deviceAttributesAnswerForTheDefaultParameterOnly() {
    terminal(5, 5).assertReplyTo("$ESC[c", "$ESC[?64;1;2;6;9;15;18;21;22c")
    terminal(5, 5).assertReplyTo("$ESC[0c", "$ESC[?64;1;2;6;9;15;18;21;22c")
    terminal(5, 5).assertReplyTo("$ESC[1c", "")
    terminal(5, 5).assertReplyTo("$ESC[>c", "$ESC[>41;320;0c")
  }

  /** DECXCPR is CPR with a page number, and it is the only `CSI ? n` the reference answers. */
  @Test
  fun theExtendedCursorReportCarriesAPageNumber() {
    terminal(5, 5).assertReplyTo("$ESC[?6n", "$ESC[?1;1;1R")
    terminal(5, 5).assertReplyTo("$ESC[?9n", "")
  }

  /**
   * DECRQM: `1` set, `2` reset, and **`0` for a mode this terminal has no state for** - the
   * reference answers rather than staying silent.
   *
   * **The `1049` arm reads which buffer is live**, since none of `47`/`1047`/`1049` has a mode flag
   * - so this query could not be answered before P4f-4b built the second screen.
   */
  @Test
  fun theModeQueryAnswersFromTheFlagOrFromWhichBufferIsLive() {
    terminal(5, 5).assertReplyTo("$ESC[?7\$p", "$ESC[?7;1\$y")
    terminal(5, 5).assertReplyTo("$ESC[?25\$p", "$ESC[?25;1\$y")
    terminal(5, 5).assertReplyTo("$ESC[?9999\$p", "$ESC[?9999;0\$y")

    terminal(5, 5).assertReplyTo("$ESC[?47\$p", "$ESC[?47;2\$y")
    terminal(5, 5).assertReplyTo("$ESC[?1049\$p", "$ESC[?1049;2\$y")
    terminal(5, 5).assertReplyTo("$ESC[?1049h$ESC[?1049\$p", "$ESC[?1049;1\$y")
  }

  private companion object {
    const val ESC = "\u001B"
  }
}
