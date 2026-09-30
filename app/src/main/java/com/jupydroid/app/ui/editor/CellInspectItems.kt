package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind

/**
 * The four session-ask rows - definition, symbol info, rename and format. Code cells only, because
 * the virtual document maps nothing else: a markdown or raw cell's ask could never resolve, so any
 * row there would be the lie [clipboardItems] refuses to print.
 *
 * "Go to definition" leads for the reason VS Code's editor context menu leads with its own:
 * `editor.action.goToDefinition` heads the navigation group there (`editorContextMenu.ts`'s
 * `navigation` rank), before the peek rows and the hover. A jump is the stronger promise of
 * the asks - the hover says what the symbol is, this one takes you to it. "Rename symbol" then
 * leads "Format cell", which is the reference's own order within its
 * `1_modification` group: `RenameAction` at 1.1, the format command at 1.3
 * (`src/vs/editor/contrib/rename/browser/rename.ts`).
 *
 * Right under the selection group, for the same reason it led there: the rows ask about the
 * code where every row below them edits the notebook, and an ask a phone menu buries under
 * fifteen editing rows is an ask nobody finds (a flow's first run had the menu scroll
 * it off-screen entirely).
 */
internal fun inspectingItems(cell: Cell, inspection: Boolean): List<Pair<String, CellAction>> =
  if (inspection && cell.kind == CellKind.CODE) {
    listOf(
      GO_TO_DEFINITION to CellAction.GoToDefinition,
      SYMBOL_INFO to CellAction.SymbolInfo,
      RENAME_SYMBOL to CellAction.RenameSymbol,
      FORMAT_CELL to CellAction.FormatCell,
    )
  } else {
    emptyList()
  }

/**
 * `editor.action.goToDefinition`, in this app's sentence case. The jump lands
 * the caret where the name is defined; the ask that fails says so beside the notebook.
 */
internal const val GO_TO_DEFINITION = "Go to definition"

internal const val SYMBOL_INFO = "Symbol info"

/**
 * `editor.action.rename`, "Rename Symbol" in the reference. The ellipsis is
 * the platform's own mark for a row that asks again rather than acting - the dialog it opens
 * wants the new name, the way [CHANGE_TO_HEADING]'s picker wants a level.
 */
internal const val RENAME_SYMBOL = "Rename symbol…"

/**
 * `notebook.formatCell`, labelled "Format Cell" there
 * (`notebookCommands.ts`), in this app's sentence case. One form whatever the count: the
 * reference's command formats the *active* cell only, so a marked run changes nothing here.
 */
internal const val FORMAT_CELL = "Format cell"
