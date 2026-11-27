package com.jupydroid.app.ui.editor

import androidx.compose.ui.input.key.Key
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.ui.editor.NotebookChordResolution.Bound
import com.jupydroid.app.ui.editor.NotebookChordResolution.Pending
import com.jupydroid.app.ui.editor.NotebookChordResolution.Unbound
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The command-mode letter rows of `packages/notebook-extension/schema/tracker.json` (the
 * "keyboard shortcuts run the notebook" half), each bound on the focused-descendant selector
 * unless it names `.jp-Cell:focus` or `.jp-MarkdownCell:focus`, plus the edit-mode rows the table
 * carries (`split-cell-at-cursor`, the two history recalls) and the `docmanager` rows bound on
 * `body`, which ours carry on both modes.
 */
class NotebookChordTableTest {
  private val edit = CellMode.EDIT
  private val command = CellMode.COMMAND

  private fun context(mode: CellMode, cellFocused: Boolean = true, headKind: CellKind? = null) =
    NotebookKeyContext(mode, cellFocused, headKind)

  /** `A`/`B` → `notebook:insert-cell-{above,below}` (`tracker.json:473-482`). */
  @Test
  fun aAndB_inCommand_insertACell() {
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.InsertAbove(CellKind.CODE))),
      notebookChord(context(command), NotebookChord(Key.A)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Insert(CellKind.CODE))),
      notebookChord(context(command), NotebookChord(Key.B)),
    )
  }

  /** `Shift A`/`Shift B` → `notebook:insert-heading-{above,below}` (`tracker.json:485-494`). */
  @Test
  fun shiftAAndB_inCommand_insertAHeading() {
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.InsertHeading(SectionReach.ABOVE))),
      notebookChord(context(command), NotebookChord(Key.A, shift = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.InsertHeading(SectionReach.BELOW))),
      notebookChord(context(command), NotebookChord(Key.B, shift = true)),
    )
  }

  /** `D`,`D` → `notebook:delete-cell` (`tracker.json:390-395`) - the one sequence in the set. */
  @Test
  fun dd_inCommand_deletesTheCell() {
    val first = notebookChord(context(command), NotebookChord(Key.D))
    assertEquals(Pending, first)
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Delete)),
      notebookChord(context(command), NotebookChord(Key.D), pending = NotebookChord(Key.D)),
    )
  }

  /** `M`/`Y`/`R` → `notebook:change-cell-to-{markdown,code,raw}` (`tracker.json:397-409`). */
  @Test
  fun mYAndR_inCommand_changeTheCellsKind() {
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.ChangeKind(CellKind.MARKDOWN))),
      notebookChord(context(command), NotebookChord(Key.M)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.ChangeKind(CellKind.CODE))),
      notebookChord(context(command), NotebookChord(Key.Y)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.ChangeKind(CellKind.RAW))),
      notebookChord(context(command), NotebookChord(Key.R)),
    )
  }

  /**
   * `1`..`6` → `notebook:change-cell-to-heading-N` on `.jp-MarkdownCell:focus`
   * (`tracker.json:411-441`): the head must be a markdown cell and its own frame must hold focus.
   */
  @Test
  fun digits_onAFocusedMarkdownCell_setTheHeadingLevel() {
    val onMarkdown = context(command, headKind = CellKind.MARKDOWN)
    listOf(Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4, Key.Five to 5, Key.Six to 6)
      .forEach { (key, level) ->
        assertEquals(
          Bound(NotebookChordIntent.ApplyCellAction(CellAction.ChangeToHeading(level))),
          notebookChord(onMarkdown, NotebookChord(key)),
        )
      }
  }

  /** A digit on a code cell is the field's key upstream - `.jp-MarkdownCell:focus` never met. */
  @Test
  fun digits_onACodeCell_resolveToNothing() {
    assertEquals(Unbound, notebookChord(context(command, headKind = CellKind.CODE), NotebookChord(Key.Three)))
  }

  /** A digit on a `⋮`-focused markdown cell is that control's own key, not the cell's. */
  @Test
  fun digits_withADescendantFocused_resolveToNothing() {
    assertEquals(
      Unbound,
      notebookChord(
        context(command, cellFocused = false, headKind = CellKind.MARKDOWN),
        NotebookChord(Key.Two),
      ),
    )
  }

  /** `C`/`X`/`V` → `notebook:{copy,cut,paste}-cell` (`tracker.json:332-344`). */
  @Test
  fun cXAndV_inCommand_clipboard() {
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Copy)),
      notebookChord(context(command), NotebookChord(Key.C)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Cut)),
      notebookChord(context(command), NotebookChord(Key.X)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Paste)),
      notebookChord(context(command), NotebookChord(Key.V)),
    )
  }

  /** `Z`/`Shift Z` → `notebook:{undo,redo}-cell-action` (`tracker.json:498-507`). */
  @Test
  fun zAndShiftZ_inCommand_undoAndRedo() {
    assertEquals(
      Bound(NotebookChordIntent.UndoCellAction),
      notebookChord(context(command), NotebookChord(Key.Z)),
    )
    assertEquals(
      Bound(NotebookChordIntent.RedoCellAction),
      notebookChord(context(command), NotebookChord(Key.Z, shift = true)),
    )
  }

  /**
   * The three merge bindings: `Shift M` → `notebook:merge-cells`, `Ctrl Backspace` →
   * `merge-cell-above`, `Ctrl Shift M` → `merge-cell-below` (`tracker.json:345-360`).
   * `mergeCells(content, false, …)` for both single-cell answers - direction is ignored for
   * runs, so `Shift M` carries the below it passes when one cell is marked.
   */
  @Test
  fun merges_inCommand_foldCells() {
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Merge(MergeInto.BELOW))),
      notebookChord(context(command), NotebookChord(Key.M, shift = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Merge(MergeInto.ABOVE))),
      notebookChord(context(command), NotebookChord(Key.Backspace, ctrl = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Merge(MergeInto.BELOW))),
      notebookChord(context(command), NotebookChord(Key.M, ctrl = true, shift = true)),
    )
  }

  /** `Ctrl Shift`+`ArrowUp`/`Down` → `notebook:move-cell-{up,down}` (`tracker.json:363-372`). */
  @Test
  fun ctrlShiftArrows_inCommand_moveTheCells() {
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Move(CellMove.UP))),
      notebookChord(context(command), NotebookChord(Key.DirectionUp, ctrl = true, shift = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Move(CellMove.DOWN))),
      notebookChord(context(command), NotebookChord(Key.DirectionDown, ctrl = true, shift = true)),
    )
  }

  /**
   * `Ctrl Shift`+`ArrowLeft`/`Right` → `notebook:collapse-all-headings`/`expand-all-headings`
   * (`tracker.json:375-388`).
   */
  @Test
  fun ctrlShiftLeftAndRight_inCommand_foldEveryHeading() {
    assertEquals(
      Bound(NotebookChordIntent.SetAllHeadingsCollapsed(true)),
      notebookChord(context(command), NotebookChord(Key.DirectionLeft, ctrl = true, shift = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.SetAllHeadingsCollapsed(false)),
      notebookChord(context(command), NotebookChord(Key.DirectionRight, ctrl = true, shift = true)),
    )
  }

  /** `Ctrl A` → `notebook:select-all` (`tracker.json:510-515`). */
  @Test
  fun ctrlA_inCommand_selectsEveryCell() {
    assertEquals(
      Bound(NotebookChordIntent.SelectAllCells),
      notebookChord(context(command), NotebookChord(Key.A, ctrl = true)),
    )
  }

  /** `Shift L` → `viewmenu:line-numbering` (`viewmenu-extension`'s notebook row). */
  @Test
  fun shiftL_inCommand_togglesLineNumbers() {
    assertEquals(
      Bound(NotebookChordIntent.ToggleAllCellLineNumbers),
      notebookChord(context(command), NotebookChord(Key.L, shift = true)),
    )
  }

  /**
   * `docmanager:save`/`save-as` are bound on `body` upstream - `Ctrl S`/`Ctrl Shift S`
   * (`docmanager-extension/schema/plugin.json`) - so they reach the notebook from a focused
   * descendant in either mode. Ours sit on the same two modes rather than the screen root.
   */
  @Test
  fun saveAndSaveAs_fireInBothModes() {
    assertEquals(
      Bound(NotebookChordIntent.SaveDocument),
      notebookChord(context(command), NotebookChord(Key.S, ctrl = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.SaveDocument),
      notebookChord(context(edit), NotebookChord(Key.S, ctrl = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.SaveDocumentAs),
      notebookChord(context(command), NotebookChord(Key.S, ctrl = true, shift = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.SaveDocumentAs),
      notebookChord(context(edit), NotebookChord(Key.S, ctrl = true, shift = true)),
    )
  }

  /**
   * `Ctrl Shift Minus` → `notebook:split-cell-at-cursor`, on the editing selector
   * (`tracker.json:1775-1780`) - the one structural edit-mode row, because the caret it splits
   * at lives in the field.
   */
  @Test
  fun ctrlShiftMinus_inEdit_splitsAtTheCaret() {
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Split)),
      notebookChord(context(edit), NotebookChord(Key.Minus, ctrl = true, shift = true)),
    )
  }

  /**
   * `Alt`+`ArrowUp`/`Down` → `notebook:access-{previous,next}-history-entry`
   * (`tracker.json:1782-1793`) - edit-scoped, since they write into the field being edited.
   * The `accessKernelHistory` setting gates the *command* (as it gates the symbol-row buttons),
   * not the binding.
   */
  @Test
  fun altArrows_inEdit_recallHistory() {
    assertEquals(
      Bound(NotebookChordIntent.RecallPreviousHistoryEntry),
      notebookChord(context(edit), NotebookChord(Key.DirectionUp, alt = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.RecallNextHistoryEntry),
      notebookChord(context(edit), NotebookChord(Key.DirectionDown, alt = true)),
    )
  }

  /** The command-mode letters all stay the field's own keys while a cell is being edited. */
  @Test
  fun theLetterRows_doNotFireInEditMode() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.A)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.B)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.M)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Y)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.R)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.C)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.X)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.V)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Z)))
  }

  /** The shifted and `Ctrl` letters are the field's own keys in edit mode the same way. */
  @Test
  fun theModifiedRows_doNotFireInEditMode() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Z, shift = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.M, shift = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.A, ctrl = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.L, shift = true)))
    assertEquals(
      Unbound,
      notebookChord(context(edit, headKind = CellKind.MARKDOWN), NotebookChord(Key.Two)),
    )
    assertEquals(
      Unbound,
      notebookChord(context(edit), NotebookChord(Key.DirectionLeft, ctrl = true, shift = true)),
    )
  }

  /** `Ctrl Shift`-and-`Alt`-arrows are scoped rows too - none crosses the wrong mode. */
  @Test
  fun theStructuralRows_doNotFireFromTheWrongMode() {
    assertEquals(
      Unbound,
      notebookChord(context(edit), NotebookChord(Key.DirectionUp, ctrl = true, shift = true)),
    )
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Backspace, ctrl = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.M, ctrl = true, shift = true)))
    assertEquals(
      Unbound,
      notebookChord(context(command), NotebookChord(Key.Minus, ctrl = true, shift = true)),
    )
    assertEquals(Unbound, notebookChord(context(command), NotebookChord(Key.DirectionUp, alt = true)))
    assertEquals(Unbound, notebookChord(context(command), NotebookChord(Key.DirectionDown, alt = true)))
  }
}
