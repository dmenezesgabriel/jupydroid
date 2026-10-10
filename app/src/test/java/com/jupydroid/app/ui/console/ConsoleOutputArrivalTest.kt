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
 * entry that showed its id and appends nothing, as it does across a notebook's cells (ADR-0085).
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
}
