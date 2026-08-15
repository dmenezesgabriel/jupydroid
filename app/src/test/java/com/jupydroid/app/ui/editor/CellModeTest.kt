package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The notebook's modal model, which is the seam every later interaction hangs from.
 *
 * Both references build their whole keyboard around it - IntelliJ has
 * `enum NotebookEditorMode { EDIT, COMMAND }` with COMMAND as the default
 * (`NotebookEditorMode.kt:38-41`), and JupyterLab's 56 default bindings are almost all scoped to
 * `.jp-mod-commandMode`. The rules asserted here are read out of JupyterLab's `Notebook.setMode`
 * and `_evtFocusIn` (`packages/notebook/src/widget.ts:1897-1949`, `3541-3563`) rather than
 * invented, because three of them are not what you would guess.
 */
class CellModeTest {
  private val notebook = EditorUiState(
    cells = listOf(
      Cell("c1", CellKind.CODE, "print(1)"),
      Cell("c2", CellKind.MARKDOWN, "# heading"),
    ),
    loaded = true,
  )

  @Test
  fun aNotebookOpensInCommandModeWithNothingSelected() {
    assertEquals(CellMode.COMMAND, notebook.mode)
    assertNull(notebook.selectedCellId)
  }

  @Test
  fun selectingACellLeavesTheModeAlone() {
    val selected = notebook.withCellSelected("c2")

    assertEquals("c2", selected.selectedCellId)
    assertEquals("selecting is not editing", CellMode.COMMAND, selected.mode)
  }

  @Test
  fun selectingACellThatIsNotThereChangesNothing() {
    assertEquals(notebook, notebook.withCellSelected("gone"))
  }

  @Test
  fun editingRequiresASelectedCell() {
    val selected = notebook.withCellSelected("c1").withMode(CellMode.EDIT)

    assertEquals(CellMode.EDIT, selected.mode)
  }

  /**
   * JupyterLab's first line in `setMode`: `if (!activeCell) newValue = 'command'`. Edit mode with
   * nothing to edit is not a state the notebook can be in, so asking for it is not an error - it
   * simply does not happen.
   */
  @Test
  fun editingWithNothingSelectedStaysInCommandMode() {
    val asked = notebook.withMode(CellMode.EDIT)

    assertEquals(CellMode.COMMAND, asked.mode)
    assertNull(asked.selectedCellId)
  }

  /**
   * `activeCell!.inputHidden = false` in `setMode`. A collapsed cell that is being edited has to
   * show its source, or the caret is in something invisible - which is reachable here, because
   * this app can collapse a cell and then be asked to edit it from the keyboard.
   */
  @Test
  fun editingACollapsedCellExpandsItsSource() {
    val collapsed = notebook.copy(
      cells = notebook.cells.map { if (it.id == "c1") it.copy(sourceHidden = true) else it },
    )

    val editing = collapsed.withCellSelected("c1").withMode(CellMode.EDIT)

    assertFalse("editing a collapsed cell must expand it", editing.cells.first { it.id == "c1" }.sourceHidden)
  }

  @Test
  fun leavingEditModeKeepsTheSelection() {
    val back = notebook.withCellSelected("c1").withMode(CellMode.EDIT).withMode(CellMode.COMMAND)

    assertEquals(CellMode.COMMAND, back.mode)
    assertEquals("c1", back.selectedCellId)
  }

  /**
   * The dangling-selection case. JupyterLab clamps `activeCellIndex` to the bounds of the cell
   * list on every change; ours is an id, so it has to be dropped when the cell it names goes.
   * Left alone it would be a selection pointing at nothing, and every later action scoped to
   * "the selected cell" would silently do nothing.
   */
  @Test
  fun deletingTheSelectedCellDropsTheSelection() {
    val selected = notebook.withCellSelected("c2").withMode(CellMode.EDIT)

    val deleted = selected.withCellsDeleted(listOf("c2"), replacementId = "new").withSelectionClamped()

    assertNull(deleted.selectedCellId)
    assertEquals("nothing selected means command mode", CellMode.COMMAND, deleted.mode)
  }

  @Test
  fun clampingLeavesALiveSelectionAlone() {
    val selected = notebook.withCellSelected("c1").withMode(CellMode.EDIT)

    assertEquals(selected, selected.withSelectionClamped())
  }
}
