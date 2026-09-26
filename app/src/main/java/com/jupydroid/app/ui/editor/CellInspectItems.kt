package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind

/**
 * The one inspect row - `docs/PLAN.md` P5e-3. Code cells only, because the virtual document
 * maps nothing else: a markdown or raw cell's ask could never resolve, so the row would be the
 * lie [clipboardItems] refuses to print.
 *
 * Second, right under the selection group, for the same reason VS Code's editor context menu
 * leads with its info commands: this row asks about the code where every row below it edits
 * the notebook, and an ask a phone menu buries under fifteen editing rows is an ask nobody
 * finds (the P5e-3 flow's first run: the menu scrolled it off-screen entirely).
 */
internal fun inspectingItems(cell: Cell, symbolInfo: Boolean): List<Pair<String, CellAction>> =
  if (symbolInfo && cell.kind == CellKind.CODE) listOf(SYMBOL_INFO to CellAction.SymbolInfo) else emptyList()

internal const val SYMBOL_INFO = "Symbol info"
