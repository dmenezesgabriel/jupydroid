package com.jupydroid.extension.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A chat's Insert is one case of the open notebook's insert: a code cell at the index below the active
 * one, as jupyter-chat's `insertBelow` is JupyterLab's `insertBelow` followed by `setSource`
 * (jupyter-chat@2c224bc `packages/jupyter-chat/src/active-cell-manager.ts`).
 */
class NotebookCellsTest {
  @Test
  fun insertBelowPutsACodeCellAtTheIndexAfterTheActiveOne() {
    val notebook = RecordedInsertsNotebookCells(listOf("a", "b", "c"), activeCellId = "b")

    notebook.insertBelow("print(6*7)")

    assertEquals(listOf(RecordedInsert(2, NotebookCellType.CODE, "print(6*7)")), notebook.inserts)
  }

  @Test
  fun insertBelowDoesNothingWithNoActiveCell() {
    val notebook = RecordedInsertsNotebookCells(listOf("a"), activeCellId = null)

    notebook.insertBelow("print(6*7)")

    assertEquals(emptyList<RecordedInsert>(), notebook.inserts)
  }

  private data class RecordedInsert(val index: Int, val type: NotebookCellType, val source: String)

  /** A notebook of code cells that records each insert rather than making it. */
  private class RecordedInsertsNotebookCells(ids: List<String>, override val activeCellId: String?) : NotebookCells {
    val inserts = mutableListOf<RecordedInsert>()
    override val available: Boolean get() = activeCellId != null
    override val path: String = "untitled.ipynb"
    override val cells: List<NotebookCellSummary> = ids.map { NotebookCellSummary(it, NotebookCellType.CODE) }

    override fun insert(index: Int, type: NotebookCellType, source: String): String {
      inserts += RecordedInsert(index, type, source)
      return "new"
    }
  }
}
