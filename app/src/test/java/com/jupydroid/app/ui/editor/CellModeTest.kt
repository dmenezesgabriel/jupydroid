package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
   * What `notebook:enter-edit-mode` asks of the state: upstream's `NotebookActions.enterEditMode`
   * sets the mode and focuses the editor (`packages/notebook/src/actions.tsx`), but here the ask
   * is for the caret, and the mode arrives with the focus event the field reports - focus cannot
   * land in a mode that already claims it.
   */
  @Test
  fun askingToEditACodeCell_requestsTheCaretWithoutChangingTheMode() {
    val asked = notebook.withCellSelected("c1").withEditRequested()

    assertEquals("c1", asked.cellTakingCaret)
    assertEquals("the ask places the caret; its focus event is what edits", CellMode.COMMAND, asked.mode)
  }

  @Test
  fun askingToEditAMarkdownCell_opensItsEditorAndRequestsTheCaret() {
    val asked = notebook.withCellSelected("c2").withEditRequested()

    assertTrue(asked.markdownBeingEdited.contains("c2"))
    assertEquals("c2", asked.markdownTakingCaret)
    assertNull("a markdown cell's caret lives in its editor, not the code field", asked.cellTakingCaret)
  }

  /**
   * `activeCell!.inputHidden = false` in `setMode` again: entering edit mode on a collapsed cell
   * expands the source the caret is about to land in.
   */
  @Test
  fun askingToEditACollapsedCell_expandsItsSource() {
    val collapsed = notebook.copy(
      cells = notebook.cells.map { if (it.id == "c1") it.copy(sourceHidden = true) else it },
    )

    val asked = collapsed.withCellSelected("c1").withEditRequested()

    assertFalse(asked.cells.first { it.id == "c1" }.sourceHidden)
    assertEquals("c1", asked.cellTakingCaret)
  }

  @Test
  fun askingToEditWithNothingSelected_changesNothing() {
    assertEquals(notebook, notebook.withEditRequested())
  }

  /**
   * Focus on a cell's frame is upstream's `_evtFocusIn`: it selects the cell, and the DOM node
   * being focused is never the editor, so the mode is command
   * (`packages/notebook/src/widget.ts:3541-3563` - `activeCellIndex` first, `setMode` second).
   */
  @Test
  fun activatingACellFromEdit_returnsToCommandKeepingTheSelection() {
    val back = notebook.withCellSelected("c1").withMode(CellMode.EDIT).withCellActivated("c1")

    assertEquals(CellMode.COMMAND, back.mode)
    assertEquals("c1", back.selectedCellId)
  }

  @Test
  fun activatingADifferentCell_movesTheSelectionInCommandMode() {
    val moved = notebook.withCellSelected("c1").withCellActivated("c2")

    assertEquals("c2", moved.selectedCellId)
    assertEquals(CellMode.COMMAND, moved.mode)
  }

  @Test
  fun activatingACellThatIsNotThere_changesNothing() {
    val editing = notebook.withCellSelected("c1").withMode(CellMode.EDIT)

    assertEquals(editing, editing.withCellActivated("gone"))
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
