package com.jupydroid.app.terminal

import org.junit.Test

/**
 * Both methods of `terminal-emulator/src/test/java/com/termux/terminal/HistoryTest.java`
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]): what a row-only resize does to the
 * scrollback, and that DECSTBM's own history filing is undisturbed by having one at all.
 */
class HistoryTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  /**
   * `testHistory`. Nine three-character groups fill a 3x3 screen and scroll six rows into
   * history. Shrinking by one row files the row the shrink cuts off; growing back pulls it out.
   */
  @Test
  fun aRowOnlyShrinkFilesTheCutRowIntoHistoryAndAGrowPullsItBackOut() {
    val fixture = terminal(3, 3).enterString("111222333444555666777888999")
    fixture.assertCursorAt(2, 2).assertLinesAre("777", "888", "999")
    fixture.assertHistoryStartsWith("666", "555")

    fixture.resize(3, 2)
    fixture.assertHistoryStartsWith("777", "666", "555")

    fixture.resize(3, 3)
    fixture.assertHistoryStartsWith("666", "555")
  }

  /**
   * `testHistoryWithScrollRegion`. DECSTBM moves the cursor home, and only the margin's own rows
   * scroll into history - the rows outside it stay on screen untouched.
   */
  @Test
  fun scrollingInsideAMarginFilesOnlyTheMarginsRowsIntoHistory() {
    val fixture = terminal(3, 4).enterString("111222333444")
    fixture.assertLinesAre("111", "222", "333", "444")
    fixture.enterString("$ESC[2;3r")
    fixture.assertCursorAt(0, 0)
    fixture.enterString("\nCDEFGH").assertLinesAre("111", "CDE", "FGH", "444")
    fixture.enterString("IJK").assertLinesAre("111", "FGH", "IJK", "444").assertHistoryStartsWith("CDE")
    fixture.enterString("LMN").assertLinesAre("111", "IJK", "LMN", "444").assertHistoryStartsWith("FGH", "CDE")
  }

  private companion object {
    const val ESC = "\u001B"
  }
}
