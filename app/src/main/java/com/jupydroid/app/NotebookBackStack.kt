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
 * A *different* notebook replaces the one open rather than stacking on it. That was unreachable
 * on a phone - the file surface is not on screen while an editor is - and so never wrong; two
 * panes make it reachable, and a stacked `[Explorer, Editor(a), Editor(b)]` would hand
 * [TwoPaneSceneStrategy] the last two entries and put a notebook where the surface belongs.
 * Replacing is also what disposes the outgoing entry, so `EditorViewModel.onCleared` runs and
 * closes its kernel socket.
 *
 * ```
 * backStack.openNotebook("6f0b…")   // pushes Editor("6f0b…")
 * backStack.openNotebook("6f0b…")   // already on top: nothing happens
 * backStack.openNotebook("a91c…")   // swaps the open notebook, one entry deep either way
 * ```
 */
fun MutableList<NavKey>.openNotebook(notebookId: String) {
  chooseDetailThatFitsTheSurface { Editor(notebookId) }
}

/** Opens a plain file as the detail - [openNotebook] for the file editor's document kind. */
fun MutableList<NavKey>.openFile(filePath: String) {
  chooseDetailThatFitsTheSurface { FileEditor(filePath) }
}

/**
 * One document, revealed or replaced - never two.
 *
 * Both document kinds swap each other, because the explorer opens both and a detail it cannot
 * swap would stack `[Explorer, FileEditor(a), Editor(b)]`, whose last two entries
 * [TwoPaneSceneStrategy] refuses to pair - one stray tap would collapse the two panes.
 */
private fun MutableList<NavKey>.chooseDetailThatFitsTheSurface(opened: () -> NavKey) {
  val document = opened()
  if (lastOrNull() == document) return
  if (lastOrNull().isDocument()) removeAt(lastIndex)
  add(document)
}

/**
 * Special-casing which kinds are documents is only true until the next document opens; when it
 * does, add it here - the rule is "anything a surface opens replaces the current detail".
 */
private fun Any?.isDocument(): Boolean = this is Editor || this is FileEditor
