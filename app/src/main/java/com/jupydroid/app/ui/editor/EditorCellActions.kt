package com.jupydroid.app.ui.editor

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * The one door a [CellAction] comes through - the route the menu, the sheet and the
 * accessibility context menu all take as [EditorContent]'s `onCellAction`.
 *
 * An extension rather than a member - [EditorViewModel] is at detekt's `TooManyFunctions`
 * cap (the same reason `TerminalGrid.clampedTopRow` is an extension), and everything the
 * ask needs is already a public surface: [kernel]'s asks and [edits]' landing.
 *
 * [CellAction.GoToDefinition] is an ask, not an edit: it is answered by the language server
 * and lands as a caret jump, so it never reaches [NotebookCellEdits.applyTo]. Everything
 * else is a cell edit and goes there unchanged.
 */
fun EditorViewModel.cellAction(cellId: String, action: CellAction, editor: CellEditorText) {
  if (action != CellAction.GoToDefinition) return edits.applyTo(cellId, action, editor)
  viewModelScope.launch { goToDefinition(cellId, editor) }
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
