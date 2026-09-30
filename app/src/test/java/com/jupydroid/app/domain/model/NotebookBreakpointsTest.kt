package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Breakpoints kept for the life of the app rather than one notebook's `EditorViewModel` - the
 * question this asks: does a breakpoint survive the ViewModel that set it being torn
 * down and rebuilt for the same notebook.
 */
class NotebookBreakpointsTest {
  @Test
  fun aCellStartsWithNoBreakpoints() {
    assertEquals(emptyList<Breakpoint>(), NotebookBreakpoints().forCell("cell-1"))
  }

  @Test
  fun togglingAnUnmarkedLineAddsIt() {
    val breakpoints = NotebookBreakpoints()

    val result = breakpoints.toggle("cell-1", line = 3)

    assertEquals(listOf(Breakpoint(line = 3)), result)
    assertEquals(listOf(Breakpoint(line = 3)), breakpoints.forCell("cell-1"))
  }

  @Test
  fun togglingAnAlreadyMarkedLineRemovesIt() {
    val breakpoints = NotebookBreakpoints()
    breakpoints.toggle("cell-1", line = 3)

    val result = breakpoints.toggle("cell-1", line = 3)

    assertEquals(emptyList<Breakpoint>(), result)
    assertEquals(emptyList<Breakpoint>(), breakpoints.forCell("cell-1"))
  }

  @Test
  fun eachCellsBreakpointsAreIndependent() {
    val breakpoints = NotebookBreakpoints()

    breakpoints.toggle("cell-1", line = 1)
    breakpoints.toggle("cell-2", line = 5)

    assertEquals(listOf(Breakpoint(line = 1)), breakpoints.forCell("cell-1"))
    assertEquals(listOf(Breakpoint(line = 5)), breakpoints.forCell("cell-2"))
  }

  @Test
  fun aCellCanCarryMoreThanOneBreakpoint() {
    val breakpoints = NotebookBreakpoints()

    breakpoints.toggle("cell-1", line = 1)
    val result = breakpoints.toggle("cell-1", line = 2)

    assertEquals(listOf(Breakpoint(line = 1), Breakpoint(line = 2)), result)
  }

  /** Surviving a rebuilt `EditorViewModel` is exactly this: the same instance, read again. */
  @Test
  fun anInstanceSharedAcrossTwoReadersSeesTheSameState() {
    val breakpoints = NotebookBreakpoints()
    breakpoints.toggle("cell-1", line = 7)

    val readBySomeoneElse = breakpoints

    assertEquals(listOf(Breakpoint(line = 7)), readBySomeoneElse.forCell("cell-1"))
  }

  /** What a `setBreakpoints` reply does: overwrites a cell's list with the kernel's own answer. */
  @Test
  fun replaceOverwritesACellsWholeList() {
    val breakpoints = NotebookBreakpoints()
    breakpoints.toggle("cell-1", line = 3)

    breakpoints.replace("cell-1", listOf(Breakpoint(line = 3, verified = true, codeId = "/tmp/1.py")))

    assertEquals(
      listOf(Breakpoint(line = 3, verified = true, codeId = "/tmp/1.py")),
      breakpoints.forCell("cell-1"),
    )
  }

  @Test
  fun replaceLeavesOtherCellsAlone() {
    val breakpoints = NotebookBreakpoints()
    breakpoints.toggle("cell-1", line = 1)
    breakpoints.toggle("cell-2", line = 5)

    breakpoints.replace("cell-1", emptyList())

    assertEquals(emptyList<Breakpoint>(), breakpoints.forCell("cell-1"))
    assertEquals(listOf(Breakpoint(line = 5)), breakpoints.forCell("cell-2"))
  }

  /**
   * A kernel's own debugpy keeps a breakpoint until it is told otherwise, so
   * disarming the last line on a cell that was ever armed must still be reported here - it is what
   * tells [NotebookDebugger][com.jupydroid.app.ui.editor.NotebookDebugger] a clearing
   * `setBreakpoints` is still owed, even once [forCell] itself has gone back to empty.
   */
  @Test
  fun everArmedIsFalseForACellNeverToggled() {
    assertFalse(NotebookBreakpoints().everArmed("cell-1"))
  }

  @Test
  fun everArmedStaysTrueAfterTheLastBreakpointIsRemoved() {
    val breakpoints = NotebookBreakpoints()
    breakpoints.toggle("cell-1", line = 3)

    breakpoints.toggle("cell-1", line = 3)

    assertTrue(breakpoints.everArmed("cell-1"))
    assertEquals(emptyList<Breakpoint>(), breakpoints.forCell("cell-1"))
  }

  @Test
  fun everArmedIsIndependentPerCell() {
    val breakpoints = NotebookBreakpoints()

    breakpoints.toggle("cell-1", line = 1)

    assertTrue(breakpoints.everArmed("cell-1"))
    assertFalse(breakpoints.everArmed("cell-2"))
  }

  /** A condition attaches to one line without disturbing the cell's others. */
  @Test
  fun setConditionAttachesAConditionToTheNamedLine() {
    val breakpoints = NotebookBreakpoints()
    breakpoints.toggle("cell-1", line = 1)
    breakpoints.toggle("cell-1", line = 2)

    val result = breakpoints.setCondition("cell-1", line = 2, condition = "x > 10")

    assertEquals(listOf(Breakpoint(line = 1), Breakpoint(line = 2, condition = "x > 10")), result)
    assertEquals(
      listOf(Breakpoint(line = 1), Breakpoint(line = 2, condition = "x > 10")),
      breakpoints.forCell("cell-1"),
    )
  }

  @Test
  fun setConditionOfNullClearsAnExistingCondition() {
    val breakpoints = NotebookBreakpoints()
    breakpoints.toggle("cell-1", line = 2)
    breakpoints.setCondition("cell-1", line = 2, condition = "x > 10")

    val result = breakpoints.setCondition("cell-1", line = 2, condition = null)

    assertEquals(listOf(Breakpoint(line = 2)), result)
  }

  @Test
  fun setConditionOnALineWithNoBreakpointChangesNothing() {
    val breakpoints = NotebookBreakpoints()
    breakpoints.toggle("cell-1", line = 1)

    val result = breakpoints.setCondition("cell-1", line = 9, condition = "x > 10")

    assertEquals(listOf(Breakpoint(line = 1)), result)
  }
}
