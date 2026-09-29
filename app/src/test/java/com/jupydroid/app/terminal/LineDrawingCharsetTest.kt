package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The DEC Special Graphics set, and the three positions where this repository's two references
 * disagree - each settled against `vttest`'s own character-set page rather than by preference
 * (`jediterm@377b76e6 core/tests/resources/testData/vttest/Test3_Characters/1`).
 */
class LineDrawingCharsetTest {
  private val charset = LineDrawingCharset()

  private fun inGraphics(text: String): String {
    charset.designateG0(useLineDrawing = true)
    return text.map { charset.substitute(it.code).toChar() }.joinToString("")
  }

  @Test
  fun aSetThatIsNotDesignatedSubstitutesNothing() {
    assertEquals("qx_`", "qx_`".map { charset.substitute(it.code).toChar() }.joinToString(""))
  }

  /**
   * The whole set in one assertion, `_` through `~`, taken from the conformance page's own row
   * for "Character set 0 (Special graphics and line drawing)".
   */
  @Test
  fun theWholeSetMatchesTheConformancePage() {
    assertEquals(
      " ◆▒␉␌␍␊°±␤␋┘┐┌└┼⎺⎻─⎼⎽├┤┴┬│≤≥π≠£·",
      inGraphics("_`abcdefghijklmnopqrstuvwxyz{|}~"),
    )
  }

  /**
   * **`d` and `h` are the reference's defect and JediTerm's fix.** Termux maps them to literal
   * `'\r'` and `'\n'` (`TerminalEmulator.java:2380,2393`), which are C0 controls that
   * [CharacterWidth] scores **0** - so they would reach a cell as combining marks on the column
   * before them, and `CharacterWidth.kt` states that nothing here ever draws a control character.
   * The conformance page puts ␍ and ␤ at those positions. Termux's `testLineDrawing` only ever
   * drives `q`, which is why its own suite cannot see this.
   */
  @Test
  fun theTabAndNewlineCellsArePicturesRatherThanControlCharacters() {
    assertEquals("␍␤", inGraphics("dh"))
    for (pictured in inGraphics("bcdehi")) {
      assertEquals("$pictured is one column wide", 1, CharacterWidth.of(pictured.code))
    }
  }

  /**
   * **The blank is at `_`, which is where the two references differ.** JediTerm's table is this
   * one rotated by one position: it maps 0x60..0x7F and parks the blank on DEL
   * (`CharacterSet.java:61-71`, indexed `ch - 32`), which no writer ever receives. The set is
   * defined over 0x5F..0x7E, so the blank belongs on `_`.
   */
  @Test
  fun theBlankIsAtUnderscoreAndNotAtDelete() {
    assertEquals(' '.code, charset.also { it.designateG0(true) }.substitute('_'.code))
    assertEquals(DELETE, charset.substitute(DELETE))
  }

  /**
   * **A digit is outside the set and stays itself**, where Termux alone maps `0` to a solid block.
   * The set replaces 0x5F..0x7E; 0x30 is not in it, JediTerm maps nothing there, and the
   * conformance page prints `0123456789` unchanged on its graphics row.
   */
  @Test
  fun digitsAreOutsideTheSet() {
    assertEquals("0123456789", inGraphics("0123456789"))
  }

  @Test
  fun shiftOutInvokesG1AndShiftInGoesBackToG0() {
    charset.designateG1(useLineDrawing = true)
    assertEquals('q'.code, charset.substitute('q'.code))
    charset.mapToGL(1)
    assertEquals('─'.code, charset.substitute('q'.code))
    charset.mapToGL(0)
    assertEquals('q'.code, charset.substitute('q'.code))
  }

  @Test
  fun resetForgetsBothDesignationsAndGoesBackToG0() {
    charset.designateG0(useLineDrawing = true)
    charset.designateG1(useLineDrawing = true)
    charset.mapToGL(1)
    charset.reset()
    assertEquals('q'.code, charset.substitute('q'.code))
  }

  private companion object {
    const val DELETE = 0x7F
  }
}
