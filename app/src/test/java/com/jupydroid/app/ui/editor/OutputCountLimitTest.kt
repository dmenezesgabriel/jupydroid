package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutputCountLimitTest {
  private fun prints(count: Int): List<CellOutput> = List(count) { CellOutput.Stream(StreamName.STDOUT, "out $it\n") }

  private fun List<CellOutput>.withEchoAt(at: Int): List<CellOutput> =
    toMutableList().also { it[at] = CellOutput.Stream(StreamName.STDIN, "Name? Ada\n") }

  @Test
  fun outputsWithinTheLimitAreDrawnWhole() {
    assertNull(drawnOutputCountOf(prints(OUTPUT_COUNT_LIMIT)))
    assertNull(drawnOutputCountOf(emptyList()))
  }

  @Test
  fun outputsPastTheLimitDrawTheFirstFifty() {
    assertEquals(OUTPUT_COUNT_LIMIT, drawnOutputCountOf(prints(OUTPUT_COUNT_LIMIT + 1)))
    assertEquals(OUTPUT_COUNT_LIMIT, drawnOutputCountOf(prints(RUNAWAY)))
  }

  /** An answer to `input()` past the limit is still drawn, as JupyterLab raises its cap to reach it. */
  @Test
  fun aStdinEchoPastTheLimitIsDrawn() {
    assertEquals(ECHO_AT + 1, drawnOutputCountOf(prints(RUNAWAY).withEchoAt(ECHO_AT)))
    assertNull(drawnOutputCountOf(prints(RUNAWAY).withEchoAt(RUNAWAY - 1)))
  }

  @Test
  fun aStdinEchoWithinTheLimitLeavesTheLimit() {
    assertEquals(OUTPUT_COUNT_LIMIT, drawnOutputCountOf(prints(RUNAWAY).withEchoAt(0)))
  }

  private companion object {
    const val RUNAWAY = 300
    const val ECHO_AT = 120
  }
}
