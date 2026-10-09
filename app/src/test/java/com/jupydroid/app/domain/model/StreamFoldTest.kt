package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory

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
   *
   * Bounded in this thread's **CPU** time, not wall time: a wall-clock bound measured the host -
   * the same fold took 0.36 s idle and 3.7 s in a build sharing the machine, and failed a gate
   * that had nothing wrong with it.
   */
  @Test
  fun aRunawayPrintFoldsInLinearTime() {
    val chunks = (0 until RUNAWAY_LINES).chunked(LINES_PER_FLUSH).map { lines -> lines.joinToString("") { "$it\n" } }
    val threads = ManagementFactory.getThreadMXBean()

    val before = threads.currentThreadCpuTime
    val text = textOf(*chunks.toTypedArray())
    val spentMs = (threads.currentThreadCpuTime - before) / NANOS_PER_MS

    assertEquals(RUNAWAY_LINES, text.count { it == '\n' })
    assertTrue(
      "folding took $spentMs ms of CPU; expected under $RUNAWAY_FOLD_BOUND_MS",
      spentMs < RUNAWAY_FOLD_BOUND_MS,
    )
  }

  private companion object {
    const val RUNAWAY_LINES = 200_000

    /** Lines per message. The cost under test is per token, so this only sets how many messages there are. */
    const val LINES_PER_FLUSH = 2_000
    const val RUNAWAY_FOLD_BOUND_MS = 3_000L
    const val NANOS_PER_MS = 1_000_000L
  }
}
