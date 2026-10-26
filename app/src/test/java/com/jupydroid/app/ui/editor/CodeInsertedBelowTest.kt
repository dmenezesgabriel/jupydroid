package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code from outside the notebook - a chat's code block - put in a new code cell below the active
 * one, which then becomes active: JupyterLab's `insertBelow` (jupyterlab@7f0e690
 * `packages/notebook/src/actions.tsx:560-584`) followed by jupyter-chat's `setSource`
 * (jupyter-chat@2c224bc `packages/jupyter-chat/src/active-cell-manager.ts`).
 */
class CodeInsertedBelowTest {
  @Test
  fun theCodeLandsInANewCodeCellBelowTheActiveOne() = runTest {
    val state = MutableStateFlow(stateOf("a", "b").copy(selectedCellId = "a"))

    editsOf(state).insertCodeBelow("print(6*7)")

    val after = state.value
    assertEquals(3, after.cells.size)
    val inserted = after.cells[1]
    assertEquals(CellKind.CODE, inserted.kind)
    assertEquals("print(6*7)", inserted.source)
    assertTrue(after.dirty)
  }

  /** A second insert stacks below the first, as both references make the new cell active. */
  @Test
  fun theNewCellBecomesTheActiveOne() = runTest {
    val state = MutableStateFlow(stateOf("a", "b").copy(selectedCellId = "a"))

    editsOf(state).insertCodeBelow("print(6*7)")

    assertEquals(state.value.cells[1].id, state.value.selectedCellId)
  }

  @Test
  fun oneUndoTakesItBack() = runTest {
    val before = stateOf("a", "b").copy(selectedCellId = "a")
    val state = MutableStateFlow(before)
    val edits = editsOf(state)

    edits.insertCodeBelow("print(6*7)")
    edits.undo()

    assertEquals(listOf("a", "b"), state.value.cells.map { it.id })
  }

  @Test
  fun withNoActiveCellNothingChanges() = runTest {
    val before = stateOf("a", "b")
    val state = MutableStateFlow(before)

    editsOf(state).insertCodeBelow("print(6*7)")

    assertEquals(before, state.value)
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
