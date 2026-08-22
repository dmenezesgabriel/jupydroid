package com.jupydroid.app.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which cell the caret is in - a rule with two readers and therefore one home.
 *
 * The stripe narrows for it ([cellStripe]) and the editor's border turns brand blue for it
 * ([cellEditorBox]); the reference hangs both off the same selector,
 * `.jp-Notebook.jp-mod-editMode .jp-Cell.jp-mod-active`
 * (`packages/notebook/style/base.css:108-110`, `:160-164`).
 *
 * Separate from [CellModeTest], which is about the *notebook's* mode transitions in
 * [EditorUiState]. This is the one-cell question those transitions are read through.
 */
class CellBeingEditedTest {
  @Test
  fun theSelectedCellIsTheOneBeingEditedWhileTheNotebookIsInEditMode() {
    assertTrue(cellIsBeingEdited(selected = true, mode = CellMode.EDIT))
  }

  /** Edit mode is the *notebook's*, so every other cell is merely on screen while one is typed in. */
  @Test
  fun noOtherCellIsBeingEditedWhileOneIs() {
    assertFalse(cellIsBeingEdited(selected = false, mode = CellMode.EDIT))
  }

  /** COMMAND is the resting state: a cell you have selected is not a cell you are typing into. */
  @Test
  fun theSelectedCellIsNotBeingEditedInCommandMode() {
    assertFalse(cellIsBeingEdited(selected = true, mode = CellMode.COMMAND))
  }
}
