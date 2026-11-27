package com.jupydroid.app.ui.editor

import androidx.compose.ui.input.key.Key
import com.jupydroid.app.domain.model.CellKind

/**
 * `packages/notebook-extension/schema/tracker.json`, `Accel` read as `ctrl` since Android has no
 * command key - and `Ctrl` is what a chromebook's search-key-Enter chord produces, which is what
 * Jupyter's own docs call it.
 *
 * The two selectors upstream calls `commandMode` and `editing` are [CellMode]s here, and the
 * three `:focus` classes are [NotebookChordSelector]s. `keys` stays a list because
 * `notebook:delete-cell` is `["D","D"]` - Lumino's sequences, which [notebookChord] resolves
 * through its [NotebookChordResolution.Pending] answer.
 */
internal val NOTEBOOK_BINDINGS = listOf(
  // Mode crossing - `tracker.json:443-471`.
  NotebookBinding(
    "notebook:enter-edit-mode",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Enter)),
    NotebookChordIntent.EnterEditMode,
    NotebookChordSelector.CELL_FOCUS,
  ),
  NotebookBinding(
    "notebook:enter-command-mode",
    CellMode.EDIT,
    listOf(NotebookChord(Key.Escape)),
    NotebookChordIntent.EnterCommandMode,
  ),
  NotebookBinding(
    "notebook:enter-command-mode",
    CellMode.EDIT,
    listOf(NotebookChord(Key.M, ctrl = true)),
    NotebookChordIntent.EnterCommandMode,
  ),
  // Inserts and the letter verbs - `tracker.json:473-515`.
  NotebookBinding(
    "notebook:insert-cell-above",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.A)),
    NotebookChordIntent.ApplyCellAction(CellAction.InsertAbove(CellKind.CODE)),
  ),
  NotebookBinding(
    "notebook:insert-cell-below",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.B)),
    NotebookChordIntent.ApplyCellAction(CellAction.Insert(CellKind.CODE)),
  ),
  NotebookBinding(
    "notebook:insert-heading-above",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.A, shift = true)),
    NotebookChordIntent.ApplyCellAction(CellAction.InsertHeading(SectionReach.ABOVE)),
  ),
  NotebookBinding(
    "notebook:insert-heading-below",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.B, shift = true)),
    NotebookChordIntent.ApplyCellAction(CellAction.InsertHeading(SectionReach.BELOW)),
  ),
  NotebookBinding(
    "notebook:delete-cell",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.D), NotebookChord(Key.D)),
    NotebookChordIntent.ApplyCellAction(CellAction.Delete),
  ),
  NotebookBinding(
    "notebook:change-cell-to-markdown",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.M)),
    NotebookChordIntent.ApplyCellAction(CellAction.ChangeKind(CellKind.MARKDOWN)),
  ),
  NotebookBinding(
    "notebook:change-cell-to-code",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Y)),
    NotebookChordIntent.ApplyCellAction(CellAction.ChangeKind(CellKind.CODE)),
  ),
  NotebookBinding(
    "notebook:change-cell-to-raw",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.R)),
    NotebookChordIntent.ApplyCellAction(CellAction.ChangeKind(CellKind.RAW)),
  ),
  NotebookBinding(
    "notebook:change-cell-to-heading-1",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.One)),
    NotebookChordIntent.ApplyCellAction(CellAction.ChangeToHeading(1)),
    NotebookChordSelector.MARKDOWN_CELL_FOCUS,
  ),
  NotebookBinding(
    "notebook:change-cell-to-heading-2",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Two)),
    NotebookChordIntent.ApplyCellAction(CellAction.ChangeToHeading(2)),
    NotebookChordSelector.MARKDOWN_CELL_FOCUS,
  ),
  NotebookBinding(
    "notebook:change-cell-to-heading-3",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Three)),
    NotebookChordIntent.ApplyCellAction(CellAction.ChangeToHeading(3)),
    NotebookChordSelector.MARKDOWN_CELL_FOCUS,
  ),
  NotebookBinding(
    "notebook:change-cell-to-heading-4",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Four)),
    NotebookChordIntent.ApplyCellAction(CellAction.ChangeToHeading(4)),
    NotebookChordSelector.MARKDOWN_CELL_FOCUS,
  ),
  NotebookBinding(
    "notebook:change-cell-to-heading-5",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Five)),
    NotebookChordIntent.ApplyCellAction(CellAction.ChangeToHeading(5)),
    NotebookChordSelector.MARKDOWN_CELL_FOCUS,
  ),
  NotebookBinding(
    "notebook:change-cell-to-heading-6",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Six)),
    NotebookChordIntent.ApplyCellAction(CellAction.ChangeToHeading(6)),
    NotebookChordSelector.MARKDOWN_CELL_FOCUS,
  ),
  NotebookBinding(
    "notebook:copy-cell",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.C)),
    NotebookChordIntent.ApplyCellAction(CellAction.Copy),
  ),
  NotebookBinding(
    "notebook:cut-cell",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.X)),
    NotebookChordIntent.ApplyCellAction(CellAction.Cut),
  ),
  NotebookBinding(
    "notebook:paste-cell-below",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.V)),
    NotebookChordIntent.ApplyCellAction(CellAction.Paste),
  ),
  NotebookBinding(
    "notebook:undo-cell-action",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Z)),
    NotebookChordIntent.UndoCellAction,
  ),
  NotebookBinding(
    "notebook:redo-cell-action",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Z, shift = true)),
    NotebookChordIntent.RedoCellAction,
  ),
  NotebookBinding(
    "notebook:merge-cells",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.M, shift = true)),
    // `mergeCells(content, false, …)` - below is the direction it passes for one marked cell,
    // and a run ignores the direction either way.
    NotebookChordIntent.ApplyCellAction(CellAction.Merge(MergeInto.BELOW)),
  ),
  NotebookBinding(
    "notebook:merge-cell-above",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Backspace, ctrl = true)),
    NotebookChordIntent.ApplyCellAction(CellAction.Merge(MergeInto.ABOVE)),
  ),
  NotebookBinding(
    "notebook:merge-cell-below",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.M, ctrl = true, shift = true)),
    NotebookChordIntent.ApplyCellAction(CellAction.Merge(MergeInto.BELOW)),
  ),
  NotebookBinding(
    "notebook:move-cell-up",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.DirectionUp, ctrl = true, shift = true)),
    NotebookChordIntent.ApplyCellAction(CellAction.Move(CellMove.UP)),
  ),
  NotebookBinding(
    "notebook:move-cell-down",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.DirectionDown, ctrl = true, shift = true)),
    NotebookChordIntent.ApplyCellAction(CellAction.Move(CellMove.DOWN)),
  ),
  NotebookBinding(
    "notebook:collapse-all-headings",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.DirectionLeft, ctrl = true, shift = true)),
    NotebookChordIntent.SetAllHeadingsCollapsed(true),
  ),
  NotebookBinding(
    "notebook:expand-all-headings",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.DirectionRight, ctrl = true, shift = true)),
    NotebookChordIntent.SetAllHeadingsCollapsed(false),
  ),
  NotebookBinding(
    "notebook:select-all",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.A, ctrl = true)),
    NotebookChordIntent.SelectAllCells,
  ),
  // The view-menu row the notebook carries - `viewmenu-extension`'s line-numbering binding.
  NotebookBinding(
    "viewmenu:line-numbering",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.L, shift = true)),
    NotebookChordIntent.ToggleAllCellLineNumbers,
  ),
  // Cursor and selection moves - `tracker.json:523-586`.
  NotebookBinding(
    "notebook:move-cursor-up",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.DirectionUp)),
    NotebookChordIntent.SelectAbove,
  ),
  NotebookBinding(
    "notebook:move-cursor-up",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.K)),
    NotebookChordIntent.SelectAbove,
  ),
  NotebookBinding(
    "notebook:move-cursor-down",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.DirectionDown)),
    NotebookChordIntent.SelectBelow,
  ),
  NotebookBinding(
    "notebook:move-cursor-down",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.J)),
    NotebookChordIntent.SelectBelow,
  ),
  NotebookBinding(
    "notebook:move-cursor-heading-above-or-collapse",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.DirectionLeft)),
    NotebookChordIntent.SelectHeadingAboveOrCollapse,
  ),
  NotebookBinding(
    "notebook:move-cursor-heading-below-or-expand",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.DirectionRight)),
    NotebookChordIntent.SelectHeadingBelowOrExpand,
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-above",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.DirectionUp, shift = true)),
    NotebookChordIntent.ExtendMarked(SelectionReach.ABOVE),
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-above",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.K, shift = true)),
    NotebookChordIntent.ExtendMarked(SelectionReach.ABOVE),
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-below",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.DirectionDown, shift = true)),
    NotebookChordIntent.ExtendMarked(SelectionReach.BELOW),
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-below",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.J, shift = true)),
    NotebookChordIntent.ExtendMarked(SelectionReach.BELOW),
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-top",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.MoveHome, shift = true)),
    NotebookChordIntent.ExtendMarked(SelectionReach.TOP),
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-bottom",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.MoveEnd, shift = true)),
    NotebookChordIntent.ExtendMarked(SelectionReach.BOTTOM),
  ),
  // The runs - `tracker.json:1754-1793` carries the editing rows; the command-mode `Accel Enter`
  // and `Alt Enter` rows run the focused cell without a caret, which is what ours do.
  NotebookBinding(
    "notebook:run",
    CellMode.EDIT,
    listOf(NotebookChord(Key.Enter, ctrl = true)),
    NotebookChordIntent.RunSelected,
  ),
  NotebookBinding(
    "notebook:run",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Enter, ctrl = true)),
    NotebookChordIntent.RunSelected,
  ),
  NotebookBinding(
    "notebook:run-and-advance",
    CellMode.EDIT,
    listOf(NotebookChord(Key.Enter, shift = true)),
    NotebookChordIntent.RunAndAdvance,
  ),
  NotebookBinding(
    "notebook:run-and-insert",
    CellMode.EDIT,
    listOf(NotebookChord(Key.Enter, alt = true)),
    NotebookChordIntent.RunAndInsert,
  ),
  NotebookBinding(
    "notebook:run-and-insert",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.Enter, alt = true)),
    NotebookChordIntent.RunAndInsert,
  ),
  NotebookBinding(
    "notebook:split-cell-at-cursor",
    CellMode.EDIT,
    listOf(NotebookChord(Key.Minus, ctrl = true, shift = true)),
    NotebookChordIntent.ApplyCellAction(CellAction.Split),
  ),
  NotebookBinding(
    "notebook:access-previous-history-entry",
    CellMode.EDIT,
    listOf(NotebookChord(Key.DirectionUp, alt = true)),
    NotebookChordIntent.RecallPreviousHistoryEntry,
  ),
  NotebookBinding(
    "notebook:access-next-history-entry",
    CellMode.EDIT,
    listOf(NotebookChord(Key.DirectionDown, alt = true)),
    NotebookChordIntent.RecallNextHistoryEntry,
  ),
  // `docmanager:save`/`save-as` on `body` (`docmanager-extension/schema/plugin.json`): upstream
  // fires them wherever focus sits, and ours sit on the notebook's two modes - the only place a
  // notebook's own key events land.
  NotebookBinding(
    "docmanager:save",
    CellMode.EDIT,
    listOf(NotebookChord(Key.S, ctrl = true)),
    NotebookChordIntent.SaveDocument,
  ),
  NotebookBinding(
    "docmanager:save",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.S, ctrl = true)),
    NotebookChordIntent.SaveDocument,
  ),
  NotebookBinding(
    "docmanager:save-as",
    CellMode.EDIT,
    listOf(NotebookChord(Key.S, ctrl = true, shift = true)),
    NotebookChordIntent.SaveDocumentAs,
  ),
  NotebookBinding(
    "docmanager:save-as",
    CellMode.COMMAND,
    listOf(NotebookChord(Key.S, ctrl = true, shift = true)),
    NotebookChordIntent.SaveDocumentAs,
  ),
)

/** One `tracker.json` row: the upstream command id is kept for greppability, not read. */
internal class NotebookBinding(
  @Suppress("unused") val command: String,
  val mode: CellMode,
  val keys: List<NotebookChord>,
  val intent: NotebookChordIntent,
  val selector: NotebookChordSelector = NotebookChordSelector.NOTEBOOK_FOCUS,
)
