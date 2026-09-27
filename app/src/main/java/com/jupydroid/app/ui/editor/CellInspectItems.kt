package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind

/**
 * The two inspect rows - `docs/PLAN.md` P5e-3/P5e-5. Code cells only, because the virtual
 * document maps nothing else: a markdown or raw cell's ask could never resolve, so either row
 * there would be the lie [clipboardItems] refuses to print.
 *
 * "Go to definition" leads for the reason VS Code's editor context menu leads with its own:
 * `editor.action.goToDefinition` heads the navigation group there (`editorContextMenu.ts`'s
 * `navigation` rank), before the peek rows and the hover. A jump is the stronger promise of
 * the two asks - the hover says what the symbol is, this one takes you to it.
 *
 * Right under the selection group, for the same reason it led there: both rows ask about the
 * code where every row below them edits the notebook, and an ask a phone menu buries under
 * fifteen editing rows is an ask nobody finds (the P5e-3 flow's first run: the menu scrolled
 * it off-screen entirely).
 */
internal fun inspectingItems(cell: Cell, inspection: Boolean): List<Pair<String, CellAction>> =
  if (inspection && cell.kind == CellKind.CODE) {
    listOf(GO_TO_DEFINITION to CellAction.GoToDefinition, SYMBOL_INFO to CellAction.SymbolInfo)
  } else {
    emptyList()
  }

/**
 * P5e-5's row - `editor.action.goToDefinition`, in this app's sentence case. The jump lands
 * the caret where the name is defined; the ask that fails says so beside the notebook.
 */
internal const val GO_TO_DEFINITION = "Go to definition"

internal const val SYMBOL_INFO = "Symbol info"
