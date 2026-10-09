package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutputLineWindowTest {
  private fun lines(count: Int, trailingNewline: Boolean = true): String =
    (0 until count).joinToString("\n", postfix = if (trailingNewline) "\n" else "") { "line $it" }

  @Test
  fun aTextWithinTheLimitIsDrawnWhole() {
    assertNull(outputLineWindowOf(lines(OUTPUT_LINE_LIMIT)))
    assertNull(outputLineWindowOf(lines(OUTPUT_LINE_LIMIT, trailingNewline = false)))
    assertNull(outputLineWindowOf(""))
  }

  /** A print ends in a line feed, which ends the last line rather than opening one more. */
  @Test
  fun aTrailingLineFeedIsNotALine() {
    assertNull(outputLineWindowOf(lines(OUTPUT_LINE_LIMIT)))
    assertEquals(1, outputLineWindowOf(lines(OUTPUT_LINE_LIMIT) + "one more")?.hiddenLines)
  }

  /** One line can be as long as it likes: the window counts lines, as VS Code's does. */
  @Test
  fun oneLongLineIsNotCut() {
    assertNull(outputLineWindowOf("x".repeat(1_000_000)))
  }

  @Test
  fun aLongTextKeepsItsHeadAndItsTail() {
    val text = lines(1000)
    val window = outputLineWindowOf(text)!!

    val head = text.substring(0, window.headEnd)
    val tail = text.substring(window.tailStart)
    assertEquals((0 until 25).joinToString("\n") { "line $it" }, head)
    assertEquals((995 until 1000).joinToString("\n", postfix = "\n") { "line $it" }, tail)
    assertEquals(970, window.hiddenLines)
  }

  /** Head, hidden and tail account for every line, so nothing is lost and nothing drawn twice. */
  @Test
  fun theHiddenLinesAreExactlyThoseBetweenHeadAndTail() {
    val text = lines(OUTPUT_LINE_LIMIT + 7, trailingNewline = false)
    val window = outputLineWindowOf(text)!!

    val hidden = text.substring(window.headEnd + 1, window.tailStart - 1).split("\n")
    assertEquals(window.hiddenLines, hidden.size)
    assertEquals("line 25", hidden.first())
    assertEquals("line 31", hidden.last())
  }

  /** The cells row's own sentence (`hiddenCellsLabel`), counting lines instead. */
  @Test
  fun theHiddenCountReadsAsASentence() {
    assertEquals("1 line hidden", hiddenLinesLabel(1))
    assertEquals("970 lines hidden", hiddenLinesLabel(970))
  }
}
