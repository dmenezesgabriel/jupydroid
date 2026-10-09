package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a kernel's stream messages land in a cell's outputs - JupyterLab's `OutputAreaModel._add`
 * and `Private.processText` (jupyterlab@7f0e690 packages/outputarea/src/model.ts), whose cases
 * these are.
 */
class StreamFoldTest {
  private fun stdout(text: String) = CellOutput.Stream(StreamName.STDOUT, text)

  private fun stderr(text: String) = CellOutput.Stream(StreamName.STDERR, text)

  private fun List<CellOutput>.folding(vararg chunks: CellOutput): List<CellOutput> =
    chunks.fold(this) { outputs, chunk -> outputs.withOutputFolded(chunk) }

  private fun textOf(vararg chunks: String): String =
    (emptyList<CellOutput>().folding(*chunks.map(::stdout).toTypedArray()).single() as CellOutput.Stream).text

  @Test
  fun twoChunksOnTheSameStreamBecomeOneOutput() {
    assertEquals(listOf(stdout("a\nb\n")), emptyList<CellOutput>().folding(stdout("a\n"), stdout("b\n")))
  }

  @Test
  fun aChunkOnTheOtherStreamStartsItsOwnOutput() {
    assertEquals(
      listOf(stdout("a\n"), stderr("b\n")),
      emptyList<CellOutput>().folding(stdout("a\n"), stderr("b\n")),
    )
  }

  @Test
  fun anOutputThatIsNotAStreamEndsTheRun() {
    val figure = CellOutput.DisplayData(emptyMap())

    assertEquals(
      listOf(stdout("a\n"), figure, stdout("b\n")),
      emptyList<CellOutput>().folding(stdout("a\n"), figure, stdout("b\n")),
    )
  }

  /** An `input()` answer's echo sits between the prints around it rather than inside them. */
  @Test
  fun aStdinEchoDoesNotJoinTheStdoutBeforeIt() {
    val echo = CellOutput.Stream(StreamName.STDIN, "Name? Ada\n")

    assertEquals(listOf(stdout("Name? "), echo), emptyList<CellOutput>().folding(stdout("Name? "), echo))
  }

  @Test
  fun aCarriageReturnRewritesTheLineItReturnsTo() {
    assertEquals("50%", textOf("\r0%", "\r25%", "\r50%"))
  }

  @Test
  fun aCarriageReturnStopsAtTheLineFeedBeforeIt() {
    assertEquals("kept\n9", textOf("kept\n1", "\r9"))
  }

  /** The cursor outlives the chunk: text after a lone `\r` overwrites, it does not append. */
  @Test
  fun theCursorCarriesAcrossChunks() {
    assertEquals("xyzdef", textOf("abcdef\r", "xy", "z"))
  }

  @Test
  fun aCarriageReturnLineFeedIsANewLine() {
    assertEquals("a\nb", textOf("a\r\nb"))
  }

  @Test
  fun aBackspaceDeletesTheCharacterBeforeIt() {
    assertEquals("ab", textOf("abc\b"))
  }

  @Test
  fun aBackspaceNeverCrossesALineFeed() {
    assertEquals("a\nb", textOf("a\n\bb"))
  }

  /** A saved notebook carries no cursor, so a reopened stream takes the next chunk at its end. */
  @Test
  fun aStreamWithNoCursorOfItsOwnContinuesAtItsEnd() {
    assertEquals(listOf(stdout("old\nnew\n")), listOf(stdout("old\n")).folding(stdout("new\n")))
  }

  /**
   * A runaway print folds in time linear in what it printed. Each `\n` and each run of text used
   * to rebuild the whole stream, so 160k lines took 15 s on a desktop JVM and froze the editor
   * for minutes on SM-X216B. The bound is a wide margin over the linear fold, not a benchmark.
   */
  @Test(timeout = RUNAWAY_FOLD_BOUND_MS)
  fun aRunawayPrintFoldsInLinearTime() {
    val chunks = (0 until RUNAWAY_LINES).chunked(LINES_PER_FLUSH).map { lines -> lines.joinToString("") { "$it\n" } }

    assertEquals(RUNAWAY_LINES, textOf(*chunks.toTypedArray()).count { it == '\n' })
  }

  private companion object {
    const val RUNAWAY_LINES = 200_000

    /** Lines per message. The cost under test is per token, so this only sets how many messages there are. */
    const val LINES_PER_FLUSH = 2_000
    const val RUNAWAY_FOLD_BOUND_MS = 3_000L
  }
}
