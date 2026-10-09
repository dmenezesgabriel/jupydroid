package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Text that colours itself with `ESC [ … m`, split into what it says and how each stretch is drawn.
 *
 * The cases are the ones both notebook references take a position on: `ansiSpan` in
 * jupyterlab@7f0e690 `packages/rendermime/src/renderers.ts` and `handleANSIOutput` in
 * vscode@3f3380c6 `extensions/notebook-renderers/src/ansi.ts`.
 */
class SgrTextTest {
  private val esc = "\u001b"

  private fun style(fore: Int, effect: Int = 0): CellStyle =
    CellStyle.encode(fore, CellStyle.COLOR_INDEX_BACKGROUND, effect)

  @Test
  fun parse_leavesTextWithNoEscapesAsOneUnstyledString() {
    assertEquals(SgrText("plain\ntext", emptyList()), SgrText.parse("plain\ntext"))
  }

  @Test
  fun parse_givesTheTextBetweenAColourAndItsResetThatColour() {
    val parsed = SgrText.parse("a $esc[31mred$esc[0m b")

    assertEquals("a red b", parsed.text)
    assertEquals(listOf(SgrRun(2, 5, style(1))), parsed.runs)
  }

  @Test
  fun parse_combinesBoldWithAColourInOneSequence() {
    val parsed = SgrText.parse("$esc[1;32mok")

    assertEquals(listOf(SgrRun(0, 2, style(2, CellStyle.BOLD))), parsed.runs)
  }

  /** Two sequences in a row that land on the same style are one stretch, not two. */
  @Test
  fun parse_mergesAdjacentStretchesOfTheSameStyle() {
    val parsed = SgrText.parse("$esc[31mab$esc[31mcd")

    assertEquals(listOf(SgrRun(0, 4, style(1))), parsed.runs)
  }

  @Test
  fun parse_readsTheExtendedColourForms() {
    val indexed = SgrText.parse("$esc[38;5;208mo").runs.single().style
    val truecolour = SgrText.parse("$esc[38;2;1;2;3mt").runs.single().style

    assertEquals(208, indexed.foreColor)
    assertEquals(0xFF010203.toInt(), truecolour.foreColor)
  }

  /** A cursor or erase sequence has nothing to act on in an output; both references drop it. */
  @Test
  fun parse_dropsEveryControlSequenceThatIsNotAColour() {
    val parsed = SgrText.parse("$esc[2Kdone$esc[1A")

    assertEquals(SgrText("done", emptyList()), parsed)
  }

  /** A private marker makes `m` a different command - xterm's `CSI > Pp m` sets key modifiers. */
  @Test
  fun parse_dropsAPrivateSequenceEvenWhenItEndsInM() {
    assertEquals(SgrText("x", emptyList()), SgrText.parse("$esc[>4;1mx"))
  }

  /**
   * A sequence cut off by the end of the text is kept as written, as `handleANSIOutput` keeps it:
   * a stream chunk can end mid-escape, and swallowing it would lose characters if it was not one.
   */
  @Test
  fun parse_keepsAnUnfinishedSequenceAsText() {
    assertEquals(SgrText("a$esc[3", emptyList()), SgrText.parse("a$esc[3"))
  }

  /** Neither reference reads `[31m` without its escape byte as a colour, so neither does this. */
  @Test
  fun parse_leavesBracketCodesWithoutAnEscapeAlone() {
    assertEquals(SgrText("[31mred", emptyList()), SgrText.parse("[31mred"))
  }

  @Test
  fun trimEnd_clipsTheStretchesToWhatIsLeft() {
    val trimmed = SgrText.parse("$esc[31mred  \n$esc[0m").trimEnd()

    assertEquals(SgrText("red", listOf(SgrRun(0, 3, style(1)))), trimmed)
  }

  @Test
  fun trimEnd_dropsAStretchThatWasOnlyTrailingSpace() {
    val trimmed = SgrText.parse("ok$esc[41m  ").trimEnd()

    assertEquals(SgrText("ok", emptyList()), trimmed)
  }
}
