package com.jupydroid.app.ui.console

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.ui.editor.ExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Console transcript's own entry, one per submitted input - P6b-2.
 *
 * Reuses [CellOutput]/[ExecutionStatus] exactly as the notebook's own cells do, since
 * `KernelGateway.execute` already produces both; JupyterLab's own `CodeConsole` (`packages/console/src/widget.ts`)
 * has no separate transcript-entry type either - it reuses a full notebook `CodeCell` per entry.
 */
class ConsoleEntryTest {
  @Test
  fun anEntryCarriesItsInputOutputsAndStatus() {
    val outputs = listOf(CellOutput.Stream(StreamName.STDOUT, "2\n"))

    val entry = ConsoleEntry("1 + 1", outputs, ExecutionStatus.STOPPED_OK)

    assertEquals("1 + 1", entry.input)
    assertEquals(outputs, entry.outputs)
    assertEquals(ExecutionStatus.STOPPED_OK, entry.status)
  }

  @Test
  fun twoEntriesWithEqualFieldsAreEqual() {
    val first = ConsoleEntry("2 + 2", emptyList(), ExecutionStatus.RUNNING)
    val second = ConsoleEntry("2 + 2", emptyList(), ExecutionStatus.RUNNING)

    assertEquals(first, second)
  }
}
