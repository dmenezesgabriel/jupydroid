package com.jupydroid.extension.api

/**
 * The open notebook's active cell, handed to an extension so code it shows can be put into the
 * notebook - jupyter-chat's `IActiveCellManager` (jupyter-chat@2c224bc
 * `packages/jupyter-chat/src/active-cell-manager.ts`), cut to the one insert both it and VS Code
 * offer (vscode@3f3380c `src/vs/workbench/contrib/chat/browser/actions/codeBlockOperations.ts`).
 *
 * ```
 * TextButton(onClick = { host.activeCell.insertBelow(code) }, enabled = host.activeCell.available) { Text("Insert") }
 * ```
 */
interface NotebookActiveCell {
  /** Whether a notebook is open with an active cell, read as Compose state so a button follows it. */
  val available: Boolean

  /** Puts [code] in a new code cell below the active one, which then becomes active; nothing while not [available]. */
  fun insertBelow(code: String)
}
