package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.KernelInputRequest
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.KernelEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The kernel-event projection itself - [EditorUiState.after] is a pure function, so each rule it
 * holds is exercised here rather than through a ViewModel.
 */
class EditorUiStateTest {
  private fun code(id: String) = Cell(id, CellKind.CODE, "print(1)")

  private fun printedState() = EditorUiState(
    cells = listOf(code("a")),
    selectedCellId = "a",
    loaded = true,
    outputs = mapOf("a" to listOf(CellOutput.Stream(StreamName.STDOUT, "old\n"))),
  )

  /**
   * `clear_output` is an instruction, not an output: `model.clear()` removes what the cell
   * printed (`packages/outputarea/src/model.ts:301-311`). Appending it meant a
   * `print; clear_output()` kept its output on screen - and persisted it as `{}`.
   */
  @Test
  fun aClearOutputDropsWhatTheCellPrinted() {
    val cleared = printedState().after(KernelEvent.Output("a", CellOutput.ClearOutput()))

    assertNull(cleared.outputs["a"])
  }

  /**
   * `wait: true` defers the clear to the next output, the flag `clearNext` carries in the
   * reference's model - an animation frame swaps without a blank beat in between.
   */
  @Test
  fun aClearOutputWithWaitKeepsTheOutputsUntilTheNextOneArrives() {
    val marked = printedState().after(KernelEvent.Output("a", CellOutput.ClearOutput(wait = true)))
    assertEquals(
      listOf(CellOutput.Stream(StreamName.STDOUT, "old\n")),
      marked.outputs["a"],
    )

    val replaced = marked.after(KernelEvent.Output("a", CellOutput.Stream(StreamName.STDOUT, "new\n")))
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "new\n")), replaced.outputs["a"])
  }

  /** The deferred clear is spent by the output it cleared for - it does not clear twice. */
  @Test
  fun aClearOutputWithWaitIsSpentByTheNextOutput() {
    val marked = printedState().after(KernelEvent.Output("a", CellOutput.ClearOutput(wait = true)))
    val first = marked.after(KernelEvent.Output("a", CellOutput.Stream(StreamName.STDOUT, "new\n")))
    val second = first.after(KernelEvent.Output("a", CellOutput.Stream(StreamName.STDOUT, "more\n")))

    assertEquals(
      listOf(
        CellOutput.Stream(StreamName.STDOUT, "new\n"),
        CellOutput.Stream(StreamName.STDOUT, "more\n"),
      ),
      second.outputs["a"],
    )
  }

  /** A prompt is answered from the output area, so the event itself changes no state the cell keeps. */
  @Test
  fun anInputRequestLeavesTheStateAsItWas() {
    val asked = KernelEvent.InputRequested(
      "a",
      KernelInputRequest(prompt = "Name?", password = false, requestHeader = "{}"),
    )

    assertEquals(printedState(), printedState().after(asked))
  }
}
