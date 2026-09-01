package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Select Graphic Rendition - `ESC [ Pm m` - as a function of the parameters and the style before it.
 *
 * Transcribed from `TerminalTest.testSelectGraphics` and `ControlSequenceIntroducerTest`'s
 * `testUnderlineVariants` and `testManyParameters` (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]). Upstream asserts on the emulator's `mForeColor` / `mBackColor` / `mEffect`
 * fields; here the same values are read back off the [CellStyle] the walk produces, which is the
 * only thing that reaches a cell.
 *
 * **The separators are run twice, as the reference runs them**: `;` opens a parameter and `:` opens
 * a *sub*-parameter, and SGR skips a sub-parameter it did not itself consume.
 */
class GraphicRenditionTest {
  private val fg = CellStyle.COLOR_INDEX_FOREGROUND
  private val bg = CellStyle.COLOR_INDEX_BACKGROUND

  /** One `ESC [ … m` applied to [style]: everything between the introducer and the `m`. */
  private fun sgr(parameters: String, style: CellStyle): CellStyle {
    val read = EscapeParameters()
    read.startSequence()
    parameters.forEach { read.accept(it.code) }
    return GraphicRendition.applied(read, style)
  }

  private fun truecolour(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

  /** One `ESC [ … m`, and the two colours it leaves behind - which is all the reference asserts. */
  private fun sgrLeaves(parameters: String, style: CellStyle, fore: Int, back: Int): CellStyle {
    val next = sgr(parameters, style)
    assertEquals("foreground after '$parameters'", fore, next.foreColor)
    assertEquals("background after '$parameters'", back, next.backColor)
    return next
  }

  @Test
  fun theWholeReferenceTableWithSemicolons() = selectGraphicsRun(';')

  @Test
  fun theWholeReferenceTableWithColons() = selectGraphicsRun(':')

  /** `TerminalTest.selectGraphicsTestRun`, whose four parts each thread the style into the next. */
  private fun selectGraphicsRun(separator: Char) {
    val sep = { text: String -> text.replace(';', separator) }
    var style = indexedColours(CellStyle.NORMAL)
    style = absentParameters(sep, style)
    style = extendedColours(sep, style)
    twentyFourBitColours(sep, style)
  }

  private fun indexedColours(from: CellStyle): CellStyle {
    var style = sgrLeaves("31", from, fore = 1, back = bg)
    style = sgrLeaves("32", style, fore = 2, back = bg)
    style = sgrLeaves("43", style, fore = 2, back = 3)
    // SGR 0 resets both foreground and background colour.
    return sgrLeaves("0", style, fore = fg, back = bg)
  }

  /**
   * A sequence that starts with a separator, or has two in a row, carries an *absent* parameter -
   * and an absent SGR parameter reads as `0`, the reset. A sub-parameter in the same position does
   * not, because SGR skips one it did not consume.
   */
  private fun absentParameters(sep: (String) -> String, from: CellStyle): CellStyle {
    var style = sgrLeaves("", sgr("31", from), fore = fg, back = bg)
    style = sgrLeaves(sep(";"), sgr("31", style), fore = fg, back = bg)
    style = sgrLeaves("0", sgr("31", style), fore = fg, back = bg)
    style = sgrLeaves(sep("0;"), sgr("31", style), fore = fg, back = bg)
    style = sgrLeaves("31;;", style, fore = fg, back = bg)
    style = sgrLeaves("31::", style, fore = 1, back = bg)
    style = sgrLeaves("31;", style, fore = fg, back = bg)
    style = sgrLeaves("31:", style, fore = 1, back = bg)
    style = sgrLeaves("31;;41", style, fore = fg, back = 1)
    return sgrLeaves("0", style, fore = fg, back = bg)
  }

  private fun extendedColours(sep: (String) -> String, from: CellStyle): CellStyle {
    var style = sgrLeaves(sep("38;5;119"), from, fore = 119, back = bg)
    style = sgrLeaves(sep("48;5;129"), style, fore = 119, back = 129)
    // A form that is neither 2 nor 5 sets no colour.
    style = sgrLeaves(sep("48;8;129"), style, fore = 119, back = 129)
    // Both colours in one sequence.
    style = sgrLeaves(sep("38;5;178") + ";" + sep("48;5;179"), style, fore = 178, back = 179)
    // An omitted parameter means zero.
    style = sgrLeaves(sep("38;5;"), style, fore = 0, back = 179)
    return sgrLeaves(sep("48;5;"), style, fore = 0, back = 0)
  }

  private fun twentyFourBitColours(sep: (String) -> String, from: CellStyle) {
    val foreground = truecolour(255, 127, 2)
    val background = truecolour(1, 2, 254)
    var style = sgrLeaves(sep("38;2;255;127;2"), sgr("0", from), foreground, back = bg)
    style = sgrLeaves(sep("48;2;1;2;254"), style, foreground, background)
    val both = sep("38;2;255;127;2") + ";" + sep("48;2;1;2;254")
    style = sgrLeaves(both, sgr("0", style), foreground, background)
    // A channel over 255 sets nothing, and the walk reads on.
    style = sgrLeaves(sep("38;2;300;127;2;48;2;1;300;254"), style, foreground, background)
    // An omitted channel means zero.
    style = sgrLeaves(sep("38;2;255;127;"), style, truecolour(255, 127, 0), background)
    style = sgrLeaves(sep("38;2;123;;77"), style, truecolour(123, 0, 77), background)
    // Extra sub-parameters past the four a truecolour takes are skipped.
    sgrLeaves("0;38:2:255:127:2:48:2:1:2:254", style, foreground, back = bg)
  }

  /**
   * `ControlSequenceIntroducerTest.testUnderlineVariants`: kitty's `4:1`..`4:5` are all mapped to
   * one underline and `4:0` to none, which is what the reference does with them.
   */
  @Test
  fun everyUnderlineVariantIsOneUnderline() {
    for (variant in listOf("", ":1", ":2", ":3", ":4", ":5")) {
      for (stop in listOf("24", "4:0")) {
        var style = sgr("4$variant", CellStyle.NORMAL)
        assertEquals("4$variant", CellStyle.UNDERLINE, style.effect)
        style = sgr("4;1", style)
        assertEquals("4$variant then 4;1", CellStyle.BOLD or CellStyle.UNDERLINE, style.effect)
        style = sgr(stop, style)
        assertEquals("4$variant stopped by $stop", CellStyle.BOLD, style.effect)
      }
    }
  }

  /**
   * `ControlSequenceIntroducerTest.testManyParameters`: past the thirty-second parameter the
   * separator is dropped, so `4:2` then `4:0` becomes one underline that is never turned off.
   */
  @Test
  fun theUnderlineStopPastTheLastParameterNeverArrives() {
    val style = sgr("0;".repeat(30) + "4:2" + "4:0", CellStyle.NORMAL)
    assertEquals(CellStyle.UNDERLINE, style.effect)
  }

  @Test
  fun everyEffectIsSetAndClearedByItsOwnCode() {
    val codes = mapOf(
      1 to CellStyle.BOLD,
      2 to CellStyle.DIM,
      3 to CellStyle.ITALIC,
      5 to CellStyle.BLINK,
      7 to CellStyle.INVERSE,
      8 to CellStyle.INVISIBLE,
      9 to CellStyle.STRIKETHROUGH,
    )
    val clears = mapOf(1 to 22, 2 to 22, 3 to 23, 5 to 25, 7 to 27, 8 to 28, 9 to 29)
    codes.forEach { (code, bit) ->
      val set = sgr("$code", CellStyle.NORMAL)
      assertEquals("SGR $code", bit, set.effect)
      assertEquals("SGR ${clears.getValue(code)}", 0, sgr("${clears.getValue(code)}", set).effect)
    }
  }

  /** `22` is "neither bold nor faint", so it clears both where 23-29 each clear one. */
  @Test
  fun normalIntensityClearsBoldAndDimTogether() {
    val both = sgr("1;2", CellStyle.NORMAL)
    assertEquals(CellStyle.BOLD or CellStyle.DIM, both.effect)
    assertEquals(0, sgr("22", both).effect)
  }

  /** The codes `10` and `11` select an alternate charset on `TERM=linux`; the reference ignores both. */
  @Test
  fun theLinuxCharsetCodesChangeNothing() {
    val bold = sgr("1", CellStyle.NORMAL)
    assertEquals(bold.bits, sgr("10", bold).bits)
    assertEquals(bold.bits, sgr("11", bold).bits)
  }

  /**
   * **This project's own, and the reason `58` is parsed at all.** The reference stores an underline
   * colour in a field nothing ever reads - `getStyle()` encodes three fields and that is not one of
   * them - so there is nowhere here to put it. Dropping the code entirely would be a defect rather
   * than a simplification: `58`'s own arguments would then be read as SGR codes, and `58;5;1` would
   * arrive as blink and bold.
   */
  @Test
  fun anUnderlineColourIsParsedAndDiscarded() {
    assertEquals(CellStyle.NORMAL.bits, sgr("58;5;1", CellStyle.NORMAL).bits)
    assertEquals(CellStyle.NORMAL.bits, sgr("58;2;255;127;2", CellStyle.NORMAL).bits)
    assertEquals(CellStyle.NORMAL.bits, sgr("59", CellStyle.NORMAL).bits)
  }

  /**
   * The quirk behind `48;8;129`, pinned because the differential would otherwise be the only thing
   * that knew: an extended colour whose form is neither `2` nor `5` sets nothing and the walk reads
   * *on*, so the `8` that named the bad form is read again as SGR 8, invisible.
   */
  @Test
  fun anInvalidExtendedColourFormIsReadAgainAsACodeOfItsOwn() {
    assertEquals(CellStyle.INVISIBLE, sgr("48;8;129", CellStyle.NORMAL).effect)
  }

  /**
   * The walk reads three fields out of a [CellStyle] and packs them back into one; `10` is a code
   * it ignores, so a style through it must come out bit-identical. Indexed and 24-bit colours are
   * packed differently, so both are asked.
   */
  @Test
  fun aStyleThroughACodeTheWalkIgnoresComesOutUnchanged() {
    val indexed = sgr("38;5;119;48;5;129;1", CellStyle.NORMAL)
    assertEquals(119, indexed.foreColor)
    assertEquals(129, indexed.backColor)
    assertEquals(indexed.bits, sgr("10", indexed).bits)
    val trueColoured = sgr("38;2;255;127;2;48;2;1;2;254", CellStyle.NORMAL)
    assertEquals(truecolour(255, 127, 2), trueColoured.foreColor)
    assertEquals(truecolour(1, 2, 254), trueColoured.backColor)
    assertEquals(trueColoured.bits, sgr("10", trueColoured).bits)
  }

  /** The bright aixterm codes are the eight standard colours offset by eight. */
  @Test
  fun theBrightCodesAreTheStandardEightOffsetByEight() {
    assertEquals(8, sgr("90", CellStyle.NORMAL).foreColor)
    assertEquals(15, sgr("97", CellStyle.NORMAL).foreColor)
    assertEquals(8, sgr("100", CellStyle.NORMAL).backColor)
    assertEquals(15, sgr("107", CellStyle.NORMAL).backColor)
  }

  @Test
  fun thirtyNineAndFortyNineRestoreTheTerminalsOwnColours() {
    val coloured = sgr("31;41", CellStyle.NORMAL)
    assertEquals(fg, sgr("39", coloured).foreColor)
    assertEquals(1, sgr("39", coloured).backColor)
    assertEquals(bg, sgr("49", coloured).backColor)
  }
}
