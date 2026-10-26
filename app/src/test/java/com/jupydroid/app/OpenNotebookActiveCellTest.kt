package com.jupydroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the open notebook hands its active cell to a contributed panel - [OpenDebugStack]'s seam,
 * crossed so a chat's Insert can reach a cell in the other pane.
 */
class OpenNotebookActiveCellTest {
  @Test
  fun nothingIsAvailableUntilANotebookPublishes() {
    assertFalse(OpenNotebookActiveCell().available)
  }

  @Test
  fun anInsertReachesTheNotebookThatPublishedIt() {
    val inserted = mutableListOf<String>()
    val open = OpenNotebookActiveCell()

    open.publish { code -> inserted += code }
    open.insertBelow("print(6*7)")

    assertTrue(open.available)
    assertEquals(listOf("print(6*7)"), inserted)
  }

  /** A notebook with no active cell publishes nothing to insert below. */
  @Test
  fun aNotebookWithNoActiveCellIsNotAvailable() {
    val open = OpenNotebookActiveCell()
    open.publish {}

    open.publish(null)

    assertFalse(open.available)
  }

  @Test
  fun closingTheNotebookLeavesNothingToInsertInto() {
    val inserted = mutableListOf<String>()
    val open = OpenNotebookActiveCell()
    open.publish { code -> inserted += code }

    open.clear()
    open.insertBelow("print(6*7)")

    assertFalse(open.available)
    assertEquals(emptyList<String>(), inserted)
  }
}
