package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cell from outside the notebook - a chat's code block, an agent's `add-cell` - put at an index and
 * made active: jupyterlab-ai-commands@2743d32 `src/notebook-commands.ts` `registerAddCellCommand`
 * (`insertCell(insertIndex, …)`, then `activeCellIndex = insertIndex`), and VS Code's
 * `NotebookEdit.insertCells(index, …)`.
 */
class CellInsertedAtTest {
  @Test
  fun theCellLandsAtTheIndexWithItsTypeAndSource() = runTest {
    val state = MutableStateFlow(stateOf("a", "b"))

    val newId = editsOf(state).insertCellAt(1, CellKind.MARKDOWN, "# Title")

    val inserted = state.value.cells[1]
    assertEquals(newId, inserted.id)
    assertEquals(CellKind.MARKDOWN, inserted.kind)
    assertEquals("# Title", inserted.source)
    assertTrue(state.value.dirty)
  }

  /** Both ends are in range: 0 goes above the first cell, the cell count below the last. */
  @Test
  fun indexZeroAndTheCellCountAreTheTwoEnds() = runTest {
    val state = MutableStateFlow(stateOf("a", "b"))
    val edits = editsOf(state)

    val first = edits.insertCellAt(0, CellKind.CODE, "print(42)")
    val last = edits.insertCellAt(3, CellKind.RAW, "raw")

    assertEquals(listOf(first, "a", "b", last), state.value.cells.map { it.id })
  }

  @Test
  fun theNewCellBecomesTheOnlyActiveOne() = runTest {
    val state = MutableStateFlow(stateOf("a", "b").copy(selectedCellId = "b", anchorCellId = "a"))

    val newId = editsOf(state).insertCellAt(2, CellKind.CODE, "print(42)")

    assertEquals(newId, state.value.selectedCellId)
    assertEquals(null, state.value.anchorCellId)
  }

  @Test
  fun oneUndoTakesItBack() = runTest {
    val state = MutableStateFlow(stateOf("a", "b"))
    val edits = editsOf(state)

    edits.insertCellAt(0, CellKind.CODE, "print(42)")
    edits.undo()

    assertEquals(listOf("a", "b"), state.value.cells.map { it.id })
  }

  @Test
  fun anIndexPastTheEndIsRefusedWithTheRangeAndChangesNothing() = runTest {
    val before = stateOf("a", "b")
    val state = MutableStateFlow(before)

    val refused = assertThrows(IllegalArgumentException::class.java) {
      editsOf(state).insertCellAt(3, CellKind.CODE, "print(42)")
    }

    assertEquals("cell index 3 is outside 0..2", refused.message)
    assertEquals(before, state.value)
  }

  @Test
  fun aNegativeIndexIsRefused() = runTest {
    val refused = assertThrows(IllegalArgumentException::class.java) {
      editsOf(MutableStateFlow(stateOf("a"))).insertCellAt(-1, CellKind.CODE, "print(42)")
    }

    assertEquals("cell index -1 is outside 0..1", refused.message)
  }

  private fun TestScope.editsOf(state: MutableStateFlow<EditorUiState>) = NotebookCellEdits(
    state,
    DocumentAutosave(TestScope(testScheduler), MutableStateFlow(EditorSettings()), { false }) {},
  )

  private fun stateOf(vararg ids: String) = EditorUiState(
    cells = ids.map { Cell(it, CellKind.CODE, "source of $it") },
    loaded = true,
  )
}
