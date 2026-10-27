package com.jupydroid.app

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.ui.editor.EditorUiState
import com.jupydroid.extension.api.NotebookCellSummary
import com.jupydroid.extension.api.NotebookCellType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the open notebook hands its cells to a contributed panel - [OpenDebugStack]'s seam, crossed
 * so a chat's Insert, and an agent's `add-cell`, can reach the notebook in the other pane.
 */
class OpenNotebookCellsTest {
  private val inserts = mutableListOf<Triple<Int, CellKind, String>>()
  private var state = EditorUiState(
    notebookId = "work/untitled.ipynb",
    cells = listOf(Cell("a", CellKind.CODE, "x = 1"), Cell("b", CellKind.MARKDOWN, "# Notes")),
    selectedCellId = "b",
  )

  @Test
  fun nothingIsOpenUntilANotebookPublishes() {
    val open = OpenNotebookCells()

    assertFalse(open.available)
    assertNull(open.path)
    assertEquals(emptyList<NotebookCellSummary>(), open.cells)
    assertNull(open.activeCellId)
    assertNull(open.insert(0, NotebookCellType.CODE, "print(42)"))
  }

  @Test
  fun thePublishedNotebookIsReadAsItIsWhenAsked() {
    val open = publishedOpen()
    state = state.copy(cells = state.cells + Cell("c", CellKind.RAW, ""), selectedCellId = "c")

    assertEquals("work/untitled.ipynb", open.path)
    val expected = listOf(
      NotebookCellSummary("a", NotebookCellType.CODE),
      NotebookCellSummary("b", NotebookCellType.MARKDOWN),
      NotebookCellSummary("c", NotebookCellType.RAW),
    )
    assertEquals(expected, open.cells)
    assertEquals("c", open.activeCellId)
  }

  @Test
  fun anInsertReachesTheNotebookThatPublishedIt() {
    val open = publishedOpen()

    val newId = open.insert(1, NotebookCellType.MARKDOWN, "# Title")

    assertEquals("new", newId)
    assertEquals(listOf(Triple(1, CellKind.MARKDOWN, "# Title")), inserts)
  }

  /** The chat's Insert, through the interface's own `insertBelow`: below the active cell, as code. */
  @Test
  fun insertBelowGoesAfterTheActiveCell() {
    publishedOpen().insertBelow("print(6*7)")

    assertEquals(listOf(Triple(2, CellKind.CODE, "print(6*7)")), inserts)
  }

  @Test
  fun aNotebookWithNoActiveCellIsNotAvailable() {
    val open = OpenNotebookCells()

    open.publish(hasActiveCell = false, current = { state }, insert = ::recordInsert)

    assertFalse(open.available)
  }

  @Test
  fun closingTheNotebookLeavesNothingToInsertInto() {
    val open = publishedOpen()

    open.clear()

    assertFalse(open.available)
    assertNull(open.path)
    assertNull(open.insert(0, NotebookCellType.CODE, "print(42)"))
    assertEquals(emptyList<Triple<Int, CellKind, String>>(), inserts)
  }

  private fun publishedOpen(): OpenNotebookCells {
    val open = OpenNotebookCells()
    open.publish(hasActiveCell = true, current = { state }, insert = ::recordInsert)
    assertTrue(open.available)
    return open
  }

  private fun recordInsert(index: Int, kind: CellKind, source: String): String {
    inserts += Triple(index, kind, source)
    return "new"
  }
}
