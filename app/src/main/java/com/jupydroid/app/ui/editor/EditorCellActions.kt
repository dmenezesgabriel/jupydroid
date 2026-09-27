package com.jupydroid.app.ui.editor

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * The one door a [CellAction] comes through - the route the menu, the sheet and the
 * accessibility context menu all take as [EditorContent]'s `onCellAction`.
 *
 * An extension rather than a member - [EditorViewModel] is at detekt's `TooManyFunctions`
 * cap (the same reason `TerminalGrid.clampedTopRow` is an extension), and everything the
 * asks need is already a public surface: [kernel]'s asks and [edits]' apply.
 *
 * [CellAction.GoToDefinition] and [CellAction.FormatCell] are asks, not [NotebookCellEdits]
 * transforms: each is answered by the language server asynchronously - one lands as a caret
 * jump, the other as a cell rewrite - so neither reaches [NotebookCellEdits.applyTo].
 * Everything else is a cell edit and goes there unchanged.
 */
fun EditorViewModel.cellAction(cellId: String, action: CellAction, editor: CellEditorText) {
  when (action) {
    CellAction.GoToDefinition -> viewModelScope.launch { goToDefinition(cellId, editor) }
    CellAction.FormatCell -> viewModelScope.launch { formatCell(cellId, editor) }
    else -> edits.applyTo(cellId, action, editor)
  }
}

/**
 * `textDocument/definition` at the caret, landed as a jump: the target cell selected and
 * scrolled to, its field taking the caret at the definition's start - the reference's
 * `_openReference` (`goToCommands.ts`). An empty reply - or locations that all point outside
 * the virtual document, pylsp's own `os.path.join` answer - is a miss, counted so the
 * screen can say "No definition found" the way VS Code's `MessageController` does.
 */
private suspend fun EditorViewModel.goToDefinition(cellId: String, editor: CellEditorText) {
  val target = kernel.asks.definitionAt(cellId, editor.source, editor.caret.end)
  if (target != null) edits.landOnDefinition(target) else edits.definitionMissed()
}

/**
 * `textDocument/rangeFormatting` over the cell's whole virtual block, landed as the cell's
 * new source - VS Code's `notebook.formatCell`, which is a whole-document format there too
 * (`formatDocumentWithSelectedProvider` on the cell's own model). A null answer - no
 * session, no provider, an empty reply, an edit that reached past the cell - is silence,
 * the asks' shared rule: a format that could not happen is not a message the user needs.
 * [NotebookCellEdits.cellFormatted]'s own gate covers the last case of a cell that moved
 * while the answer was in flight.
 */
private suspend fun EditorViewModel.formatCell(cellId: String, editor: CellEditorText) {
  val formatted = kernel.asks.formatCell(cellId, editor.source) ?: return
  edits.cellFormatted(cellId, formatted, expectedSource = editor.source)
}
