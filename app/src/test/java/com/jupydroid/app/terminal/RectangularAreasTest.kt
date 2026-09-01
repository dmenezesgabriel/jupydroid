package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rectangular operations under the `$` intermediate. **P4f-5c is the fill and the two erases**:
 * DECFRA (`CSI Pch;Pt;Pl;Pb;Pr $ x`), DECERA (`CSI Pt;Pl;Pb;Pr $ z`) and DECSERA (`$ {`), which
 * reads the protected bit P4f-5b gave a writer.
 *
 * Transcribed from `RectangularAreasTest.testFillRectangularArea`, `testEraseRectangularArea` and
 * `testSelectiveEraseInRectangle` (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]). The
 * third is the one `SelectiveErasingTest` deferred here by name, it being under `$` rather than `"`.
 * DECCRA joins at P4f-5d and the two attribute operations at P4f-5e.
 *
 * **DECFRA's first parameter is the character**, where the other two start at the top row - which is
 * why the reference walks its arguments with an index rather than by position.
 */
class RectangularAreasTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  private fun backColorAt(fixture: EmulatorFixture, row: Int, column: Int): Int =
    fixture.emulator.screen.grid.styleAt(row, column).backColor

  /** `testFillRectangularArea`. `88` is `X`; every parameter beyond the character is a bound. */
  @Test
  fun fillRectangularAreaWritesOneCharacterOverTheRectangle() {
    terminal(3, 3).enterString("$ESC[88\$x").assertLinesAre("XXX", "XXX", "XXX")
    terminal(3, 3).enterString("$ESC[88;1;1;2;10\$x").assertLinesAre("XXX", "XXX", "   ")
    terminal(3, 3).enterString("$ESC[88;2;1;3;10\$x").assertLinesAre("   ", "XXX", "XXX")
    terminal(3, 3).enterString("$ESC[88;1;1;100;1\$x").assertLinesAre("X  ", "X  ", "X  ")
    terminal(3, 3).enterString("$ESC[88;1;1;100;2\$x").assertLinesAre("XX ", "XX ", "XX ")
    terminal(3, 3).enterString("$ESC[88;100;1;100;2\$x").assertLinesAre("   ", "   ", "   ")
  }

  /** `testEraseRectangularArea`, including the case that leaves the cursor where it found it. */
  @Test
  fun eraseRectangularAreaBlanksTheRectangleAndMovesNothing() {
    terminal(3, 3).enterString("ABCDEFGHI$ESC[\$z").assertLinesAre("   ", "   ", "   ")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[1;1;2;10\$z").assertLinesAre("   ", "   ", "GHI")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[2;1;3;10\$z").assertLinesAre("ABC", "   ", "   ")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[1;1;100;1\$z").assertLinesAre(" BC", " EF", " HI")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[1;1;100;2\$z").assertLinesAre("  C", "  F", "  I")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[100;1;100;2\$z").assertLinesAre("ABC", "DEF", "GHI")
    terminal(3, 3).enterString("A$ESC[\$zBC").assertLinesAre(" BC", "   ", "   ")
  }

  /** `testSelectiveEraseInRectangle`: DECERA's rectangle, sparing what DECSCA protected. */
  @Test
  fun selectiveEraseInRectangleSparesProtectedCells() {
    terminal(3, 3).enterString("ABCDEFGHI$ESC[\${").assertLinesAre("   ", "   ", "   ")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[1;1;2;10\${").assertLinesAre("   ", "   ", "GHI")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[2;1;3;10\${").assertLinesAre("ABC", "   ", "   ")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[1;1;100;1\${").assertLinesAre(" BC", " EF", " HI")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[1;1;100;2\${").assertLinesAre("  C", "  F", "  I")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[100;1;100;2\${").assertLinesAre("ABC", "DEF", "GHI")

    val protected = "ABCD$ESC[1\"qE$ESC[0\"qFGHI"
    terminal(3, 3).enterString("$protected$ESC[\${").assertLinesAre("   ", " E ", "   ")
    terminal(3, 3).enterString("$protected$ESC[1;1;2;10\${").assertLinesAre("   ", " E ", "GHI")
  }

  /**
   * **A fill character outside `32..126` and `160..255` makes the reference drop the whole
   * command**, rather than substituting a space. Expectations off `RefScreen`; upstream only ever
   * passes `88`.
   */
  @Test
  fun aFillCharacterOutsideTheAllowedRangesIsIgnoredWhole() {
    terminal(3, 3).enterString("ABCDEFGHI$ESC[31\$x").assertLinesAre("ABC", "DEF", "GHI")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[127\$x").assertLinesAre("ABC", "DEF", "GHI")
    // 160 *is* in range, and it is a non-breaking space - which `RefScreen` prints as a blank and
    // is not one. Read as hex rather than off the screen dump, which is what caught this.
    val nbsp = "\u00A0\u00A0\u00A0"
    terminal(3, 3).enterString("ABCDEFGHI$ESC[160\$x").assertLinesAre(nbsp, nbsp, nbsp)
  }

  /**
   * **Only the selective erase keeps the cell's own colours.** DECERA and DECFRA repaint with the
   * style in force, which is what `keepVisualAttributes = erase && selective` says upstream.
   * Expectations off `RefScreen`.
   */
  @Test
  fun onlyTheSelectiveEraseKeepsTheColoursItFound() {
    val painted = "$ESC[44mABCDEF$ESC[42m"
    assertEquals(2, backColorAt(terminal(3, 2).enterString("$painted$ESC[\$z"), 0, 0))
    assertEquals(2, backColorAt(terminal(3, 2).enterString("$painted$ESC[88\$x"), 0, 0))
    assertEquals(4, backColorAt(terminal(3, 2).enterString("$painted$ESC[\${"), 0, 0))
  }

  /** A `$` final byte no operation claims is swallowed with its parameters, never drawn. */
  @Test
  fun aFinalByteNothingClaimsIsSwallowed() {
    val fixture = terminal(3, 2).enterString("AB$ESC[1;1;2;2\$wC")
    fixture.assertLinesAre("ABC", "   ")
    assertEquals(0 to 2, fixture.emulator.screen.cursor.let { it.row to it.column })
  }

  private companion object {
    const val ESC = "\u001B"
  }
}
