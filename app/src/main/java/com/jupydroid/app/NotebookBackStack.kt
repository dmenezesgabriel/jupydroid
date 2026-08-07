package com.jupydroid.app

import androidx.navigation3.runtime.NavKey

/**
 * Opens a notebook, or does nothing when that notebook is already the one open.
 *
 * Reveal-don't-reopen, which is what JupyterLab's `openOrReveal` does: it looks for a widget
 * already showing the path and activates it rather than adding a second
 * (`packages/docmanager/src/manager.ts:481-503`; the opener then guards again with
 * `if (!widget.isAttached)`).
 *
 * Here it is not tidiness. Both nav entry decorators key on `NavEntry.contentKey`, which
 * defaults to `key.toString()`, so two entries for one notebook would share a single
 * `ViewModelStore` *and* a single `SaveableStateHolder` slot - and popping the first would clear
 * state the second still needs. A double tap is enough to cause it, because Recents is still on
 * screen and still hittable while the transition runs.
 *
 * ```
 * backStack.openNotebook("6f0b…")   // pushes Editor("6f0b…")
 * backStack.openNotebook("6f0b…")   // already on top: nothing happens
 * ```
 */
fun MutableList<NavKey>.openNotebook(notebookId: String) {
  val editor = Editor(notebookId)
  if (lastOrNull() == editor) return
  add(editor)
}
