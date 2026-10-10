package com.jupydroid.app.ui.console

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.mimeBundleOf
import com.jupydroid.app.ui.editor.ExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * How one kernel output lands in the Console's transcript: an `update_display_data` redraws every
 * entry that showed its id and appends nothing, as it does across a notebook's cells (ADR-0085), and
 * a `clear_output` empties its own entry, now or at the entry's next output, as it does a cell.
 */
class ConsoleOutputArrivalTest {
  private fun frame(text: String, id: String? = "d1") =
    CellOutput.DisplayData(mimeBundleOf("text/plain" to text), displayId = id)

  private fun entry(id: String, vararg outputs: CellOutput) =
    ConsoleEntry(id, "source of $id", outputs.toList(), ExecutionStatus.STOPPED_OK)

  private val shown = listOf(entry("e1", frame("'frame 0'")), entry("e2"))

  @Test
  fun anUpdateFromALaterEntryRedrawsTheEntryThatShowedIt() {
    val updated = shown.afterOutput("e2", CellOutput.DisplayUpdate(frame("'frame 3'")))

    assertEquals(listOf(frame("'frame 3'")), updated[0].outputs)
    assertEquals(emptyList<CellOutput>(), updated[1].outputs)
  }

  /** JupyterLab's `_onIOPub`: a `display_data` whose id is already shown updates it, then is appended. */
  @Test
  fun aRepeatedDisplayUpdatesTheEarlierOneAndIsAppended() {
    val updated = shown.afterOutput("e2", frame("'frame 1'"))

    assertEquals(listOf(frame("'frame 1'")), updated[0].outputs)
    assertEquals(listOf(frame("'frame 1'")), updated[1].outputs)
  }

  /** An entry the update misses keeps its identity, for Compose. */
  @Test
  fun anEntryWithNoTargetIsKeptAsItIs() {
    val printed = entry("e0", CellOutput.Stream(StreamName.STDOUT, "hi\n"))

    val updated = (listOf(printed) + shown).afterOutput("e2", CellOutput.DisplayUpdate(frame("'frame 3'")))

    assertSame(printed, updated[0])
  }

  @Test
  fun anUpdateToAnIdNobodyShowsChangesNothing() {
    val updated = shown.afterOutput("e2", CellOutput.DisplayUpdate(frame("'other'", id = "d2")))

    assertEquals(shown, updated)
  }

  @Test
  fun aStreamJoinsItsOwnEntryOnly() {
    val updated = shown.afterOutput("e2", CellOutput.Stream(StreamName.STDOUT, "a\n"))

    assertSame(shown[0], updated[0])
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "a\n")), updated[1].outputs)
  }

  private fun printed(text: String) = CellOutput.Stream(StreamName.STDOUT, text)

  private val bothPrinted = listOf(entry("e1", printed("one\n")), entry("e2", printed("old\n")))

  /** A console entry is drawn through the same output model a cell is (`OutputAreaModel.clear`). */
  @Test
  fun anImmediateClearEmptiesItsOwnEntryOnly() {
    val cleared = bothPrinted.afterOutput("e2", CellOutput.ClearOutput())

    assertSame(bothPrinted[0], cleared[0])
    assertEquals(emptyList<CellOutput>(), cleared[1].outputs)
  }

  @Test
  fun aWaitingClearKeepsTheEntryUntilItsNextOutput() {
    val marked = bothPrinted.afterOutput("e2", CellOutput.ClearOutput(wait = true))

    assertEquals(listOf(printed("old\n")), marked[1].outputs)
  }

  /** The deferred clear is spent by the output it cleared for - it does not clear twice. */
  @Test
  fun aWaitingClearIsSpentByTheNextOutput() {
    val marked = bothPrinted.afterOutput("e2", CellOutput.ClearOutput(wait = true))
    val first = marked.afterOutput("e2", printed("new\n"))
    val second = first.afterOutput("e2", printed("more\n"))

    assertEquals(listOf(printed("new\n")), first[1].outputs)
    assertEquals(listOf(printed("new\nmore\n")), second[1].outputs)
  }

  /** An update sets an output in place; only an output that is *added* spends a `clear(wait)`. */
  @Test
  fun anUpdateLeavesAWaitingClearForTheNextOutput() {
    val marked = shown.afterOutput("e1", CellOutput.ClearOutput(wait = true))
    val updated = marked.afterOutput("e1", CellOutput.DisplayUpdate(frame("'frame 3'")))
    val added = updated.afterOutput("e1", printed("done\n"))

    assertEquals(listOf(frame("'frame 3'")), updated[0].outputs)
    assertEquals(listOf(printed("done\n")), added[0].outputs)
  }
}
