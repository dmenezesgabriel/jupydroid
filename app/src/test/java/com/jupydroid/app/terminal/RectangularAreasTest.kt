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

  /**
   * `testRectangularCopy` (DECCRA, `$ v`), all seven cases. Eight parameters: the source rectangle,
   * a page it ignores, the destination corner, and another page it ignores.
   *
   * **Its coordinates are zero-based where the fill and erase group's are not** - DECCRA subtracts
   * one from its top and left, and the others carry theirs one-based into the loop. Same arm,
   * two conventions, transcribed rather than regularised.
   */
  @Test
  fun rectangularCopyMovesABlockAndClipsItToTheScreen() {
    val grid = "ABC\r\nDEF\r\nGHI"
    terminal(7, 3).enterString("$grid$ESC[1;1;2;2;1;2;5;1\$v").assertLinesAre("ABC    ", "DEF AB ", "GHI DE ")
    terminal(7, 3).enterString("$grid$ESC[1;1;3;3;1;1;4;1\$v").assertLinesAre("ABCABC ", "DEFDEF ", "GHIGHI ")
    terminal(7, 3).enterString("$grid$ESC[1;1;3;3;1;1;3;1\$v").assertLinesAre("ABABC  ", "DEDEF  ", "GHGHI  ")

    val indented = "   ABC\r\n   DEF\r\n   GHI"
    terminal(7, 3).enterString("$indented$ESC[1;4;3;6;1;1;1;1\$v").assertLinesAre("ABCABC ", "DEFDEF ", "GHIGHI ")
    terminal(7, 3).enterString("$indented$ESC[1;4;3;6;1;1;2;1\$v").assertLinesAre(" ABCBC ", " DEFEF ", " GHIHI ")

    terminal(3, 3).enterString("$grid$ESC[1;1;2;2;1;2;2;1\$v").assertLinesAre("ABC", "DAB", "GDE")
  }

  /** The same, under origin mode with both horizontal margins set - so it needs P4f-5a's DECSLRM. */
  @Test
  fun rectangularCopyReadsItsCoordinatesFromTheMarginsUnderOriginMode() {
    val fixture = terminal(5, 5).enterString("$ESC[?6h$ESC[?69h$ESC[2;4s")
    fixture.enterString("ABCDEFGHIJK").assertLinesAre(" ABC ", " DEF ", " GHI ", " JK  ", "     ")
    fixture.enterString("$ESC[1;1;2;2;1;2;2;1\$v").assertLinesAre(" ABC ", " DAB ", " GDE ", " JK  ", "     ")
  }

  /**
   * **An inverted source rectangle copies nothing**, either way round. The reference holds each
   * far edge at or beyond its near one, so the extent is zero rather than negative.
   *
   * The mutation that removes those clamps is **equivalent**, and is recorded as such rather than
   * left as an open survivor: a negative extent is inert at both levels that consume one -
   * `TerminalGrid.blockCopy` iterates `0 until h`, and `TerminalRow.copyInterval` loops
   * `while (charIndex < end)`. Neither runs when the range is inverted, so no input can tell the
   * two apart. This test pins the *behaviour*; the equivalence argument is in the evidence.
   */
  @Test
  fun anInvertedSourceRectangleCopiesNothing() {
    val grid = "ABC\r\nDEF\r\nGHI"
    terminal(7, 3).enterString("$grid$ESC[3;1;1;3;1;1;5;1\$v").assertLinesAre("ABC    ", "DEF    ", "GHI    ")
    terminal(7, 3).enterString("$grid$ESC[1;3;3;1;1;1;5;1\$v").assertLinesAre("ABC    ", "DEF    ", "GHI    ")
    terminal(7, 3).enterString("$grid$ESC[1;1;3;3;1;1;5;1\$v").assertLinesAre("ABC ABC", "DEF DEF", "GHI GHI")
  }

  /** Every cell's effect bits, row by row - the shape `RectangularAreasTest`'s upstream helper takes. */
  private fun assertEffectsAre(fixture: EmulatorFixture, vararg rows: IntArray) {
    rows.forEachIndexed { row, expected ->
      expected.forEachIndexed { column, effect ->
        assertEquals(
          "effect at $row,$column",
          effect,
          fixture.emulator.screen.grid.styleAt(row, column).effect,
        )
      }
    }
  }

  /**
   * `testChangeAttributesInRectangularArea` (DECCARA, `$ r`). **Without DECSACE the region is a
   * *stream*** - the first row runs to the right margin and only the last stops at the rectangle's
   * right edge - and `CSI 2 * x` turns it into a true rectangle.
   */
  @Test
  fun changeAttributesRunsAsAStreamUntilDecsaceMakesItARectangle() {
    val streamed = terminal(3, 3).enterString("ABCDEFGHI$ESC[1;1;2;2;1\$r")
    streamed.assertLinesAre("ABC", "DEF", "GHI")
    assertEffectsAre(streamed, intArrayOf(B, B, B), intArrayOf(B, B, 0), intArrayOf(0, 0, 0))

    val boxed = terminal(3, 3).enterString("$ESC[2*xABCDEFGHI$ESC[1;1;2;2;1\$r")
    boxed.assertLinesAre("ABC", "DEF", "GHI")
    assertEffectsAre(boxed, intArrayOf(B, B, 0), intArrayOf(B, B, 0), intArrayOf(0, 0, 0))
  }

  /**
   * `testReverseAttributesInRectangularArea` (DECRARA, `$ t`), all four cases - including the two
   * that start with a bit already set, which is what tells a reverse from a set.
   */
  @Test
  fun reverseAttributesFlipsEachBitItIsGiven() {
    val streamed = terminal(3, 3).enterString("ABCDEFGHI$ESC[1;1;2;2;1\$t")
    assertEffectsAre(streamed, intArrayOf(B, B, B), intArrayOf(B, B, 0), intArrayOf(0, 0, 0))

    val boxed = terminal(3, 3).enterString("$ESC[2*xABCDEFGHI$ESC[1;1;2;2;1\$t")
    assertEffectsAre(boxed, intArrayOf(B, B, 0), intArrayOf(B, B, 0), intArrayOf(0, 0, 0))

    val alreadyBold = terminal(3, 3).enterString("$ESC[2*xA$ESC[1mB$ESC[0mCDEFGHI$ESC[1;1;2;2;1\$t")
    assertEffectsAre(alreadyBold, intArrayOf(B, 0, 0), intArrayOf(B, B, 0), intArrayOf(0, 0, 0))

    val mixed = "$ESC[2*x$ESC[4mA$ESC[0m$ESC[1mB$ESC[0mCDEFGHI"
    val both = terminal(3, 3).enterString("$mixed$ESC[1;1;2;2;1;4\$t")
    assertEffectsAre(both, intArrayOf(B, U, 0), intArrayOf(B or U, B or U, 0), intArrayOf(0, 0, 0))
  }

  private companion object {
    const val B = CellStyle.BOLD
    const val U = CellStyle.UNDERLINE
    const val ESC = "\u001B"
  }
}
