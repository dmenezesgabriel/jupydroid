package com.jupydroid.app.ui.editor

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * The one door a [NotebookChordIntent] comes through - what [EditorContent]'s
 * `onPreviewKeyEvent` asks of the ViewModel once [notebookChord] has named the command.
 *
 * An extension rather than a member, for [cellAction]'s reason: [EditorViewModel] is at
 * detekt's `TooManyFunctions` cap, and the dispatch needs only [kernel]'s public surface.
 *
 * Every run intent is a `runSelected` plus zero or one document moves, which is why the dispatch
 * is thin: [NotebookChordIntent.RunSelected] is the marked run alone, and the other two are the
 * same run with [EditorUiState.withRunAdvanced] / [EditorUiState.withRunInserted] folded in -
 * `actions.tsx:814-856`, `:864-903`.
 *
 * [input] is the cell's text field, for the two commands that read or write it:
 * `split-cell-at-cursor` needs the caret ([FocusedCellInput.focusedEditor]) and the history
 * recalls read it as their placeholder and write the recall into it.
 */
internal fun EditorViewModel.notebookKey(intent: NotebookChordIntent, input: FocusedCellInput) {
  when (intent) {
    NotebookChordIntent.RunSelected -> kernel.run(RunRange.MARKED)
    NotebookChordIntent.RunAndAdvance -> kernel.chords.runAndAdvance()
    NotebookChordIntent.RunAndInsert -> kernel.chords.runAndInsert()
    NotebookChordIntent.EnterEditMode -> selection.enterEditMode()
    NotebookChordIntent.SelectAbove -> selection.moveCursor(CursorDirection.UP)
    NotebookChordIntent.SelectBelow -> selection.moveCursor(CursorDirection.DOWN)
    NotebookChordIntent.SelectHeadingAboveOrCollapse -> edits.selectHeadingAboveOrCollapse()
    NotebookChordIntent.SelectHeadingBelowOrExpand -> edits.selectHeadingBelowOrExpand()
    is NotebookChordIntent.ExtendMarked -> selection.extendMarkedByKey(intent.reach)
    // `docmanager:save-as`'s dialog is the shell's - the screen dispatches it from the
    // `onNotebookChord` lambda; `enter-command-mode` is the frame-focus move the dispatch
    // site makes where the FocusRequester lives. Neither is a state transform.
    NotebookChordIntent.SaveDocumentAs, NotebookChordIntent.EnterCommandMode -> Unit
    else -> notebookEditKey(intent, input)
  }
}

/** The document-state arms of [notebookKey] - edits, undo, the settings write and history. */
private fun EditorViewModel.notebookEditKey(intent: NotebookChordIntent, input: FocusedCellInput) {
  when (intent) {
    is NotebookChordIntent.ApplyCellAction -> applyCellAction(intent.action, input)
    NotebookChordIntent.UndoCellAction -> edits.undo()
    NotebookChordIntent.RedoCellAction -> edits.redo()
    NotebookChordIntent.SelectAllCells -> selection.selectAllByKey()
    is NotebookChordIntent.SetAllHeadingsCollapsed -> edits.setAllHeadingsCollapsed(intent.collapsed)
    NotebookChordIntent.ToggleAllCellLineNumbers -> toggleLineNumbers()
    NotebookChordIntent.SaveDocument -> viewModelScope.launch { edits.save.flush() }
    NotebookChordIntent.RecallPreviousHistoryEntry -> recallHistory(input, previous = true)
    NotebookChordIntent.RecallNextHistoryEntry -> recallHistory(input, previous = false)
    else -> Unit
  }
}

/**
 * What the letter chords ask for is exactly what the cell menu asks for - the head cell's
 * `cellAction` (`actions.tsx`'s `selectFirst` is why every letter acts on the head).
 */
private fun EditorViewModel.applyCellAction(action: CellAction, input: FocusedCellInput) {
  uiState.value.selectedCellId?.let { head ->
    edits.applyTo(head, action, input.focusedEditor ?: CellEditorText.NONE)
  }
}

/**
 * `viewmenu:line-numbering` is a settings write, not a document move - the same repository the
 * menu's "Line numbers" row writes, toggled on the setting's own value.
 */
private fun EditorViewModel.toggleLineNumbers() {
  viewModelScope.launch {
    editorSettings.update(settings.value.copy(lineNumbersShown = !settings.value.lineNumbersShown))
  }
}

/**
 * `notebook:access-{previous,next}-history-entry` - [NotebookHistory]'s walk over the field's
 * own copy. The `accessKernelHistory` setting gates the *command* the way it gates the
 * symbol-row keys that share this walk - the binding is always resolved upstream, and the
 * command stands down; a noop claim leaves `Alt`+arrows to do nothing, which is what the
 * setting asks for.
 */
private fun EditorViewModel.recallHistory(input: FocusedCellInput, previous: Boolean) {
  if (!settings.value.accessKernelHistory) return
  val typed = input.focusedText ?: return
  val recall = if (previous) kernel.history::recallPrevious else kernel.history::recallNext
  recall(typed, input::replaceAll)
}
