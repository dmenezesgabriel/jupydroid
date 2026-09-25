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
 * panes make it reachable, and a stacked `[Explorer, Editor(a), Editor(b)]` would leave two
 * entries claiming the detail pane, of which `ListDetailSceneStrategy` draws the **last** - so the
 * one beneath would keep its ViewModel and its kernel alive with nothing on screen.
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
 * Opens a shell as the detail - [openNotebook] for the terminal document kind.
 *
 * Unlike a notebook or a file, a terminal has no path to reveal by: [name] is the server's own
 * session name, minted fresh on every "New Terminal", so [chooseDetailThatFitsTheSurface]'s
 * already-on-top check never matches and a second tap always replaces the first with a new shell.
 */
fun MutableList<NavKey>.openTerminal(name: String) {
  chooseDetailThatFitsTheSurface { Terminal(name) }
}

/**
 * Opens a Console for the notebook already showing - stacked on top of it, not through
 * [chooseDetailThatFitsTheSurface]: the whole point is running against the same kernel while the
 * notebook stays reachable on Back, so this never replaces what it is attached to. Revealed
 * rather than duplicated on a second tap, [Console]'s own de-dupe rule.
 *
 * ```
 * backStack.openConsole("nb-1")   // [Explorer, Editor("nb-1")] -> [Explorer, Editor("nb-1"), Console("nb-1")]
 * ```
 */
fun MutableList<NavKey>.openConsole(notebookId: String) {
  val console = Console(notebookId)
  if (lastOrNull() == console) return
  add(console)
}

/**
 * Opens the Settings screen - [openConsole]'s own shape, not [openNotebook]'s: it stacks on top of
 * whatever is open rather than replacing it, since visiting Settings should not dispose the
 * document beneath it, and a second tap reveals the one already on top rather than duplicating it.
 *
 * ```
 * backStack.openSettings()   // [Explorer, Editor("nb-1")] -> [Explorer, Editor("nb-1"), Settings]
 * ```
 */
fun MutableList<NavKey>.openSettings() {
  if (lastOrNull() == Settings) return
  add(Settings)
}

/**
 * Opens the Launcher - [openSettings]'s own shape for the same reason: reaching it should not
 * dispose the document beneath, and a second tap reveals the one already on top. The reference
 * adds it as a main-area tab beside the open widgets (`launcher:create`).
 *
 * The extra [remove] over `openSettings`'s body is [openNotebook]'s invariant applied to a
 * `data object`: equal keys share one `contentKey`, so a `Launcher` left deeper in the stack -
 * `[Explorer, Launcher, Settings]` - would collide with a second push on one `ViewModelStore`.
 * It is promoted to the top instead.
 *
 * ```
 * backStack.openLauncher()   // [Explorer, Editor("nb-1")] -> [Explorer, Editor("nb-1"), Launcher]
 * ```
 */
fun MutableList<NavKey>.openLauncher() {
  if (lastOrNull() == Launcher) return
  remove(Launcher)
  add(Launcher)
}

/**
 * `shell.isEmpty('main')` - the question the reference's `maybeCreate` asks before adding a
 * launcher. A lone browser is an empty main area; a document, a launcher, or anything stacked in
 * the detail slot is not.
 */
fun List<NavKey>.mainAreaIsEmpty(): Boolean = none { it.isDocument() }

/**
 * One document, revealed or replaced - never two, anywhere in the stack.
 *
 * Both document kinds swap each other, because the explorer opens both and a detail it cannot
 * swap would stack `[Explorer, FileEditor(a), Editor(b)]` - two entries for one pane, of which
 * only the last is ever drawn. [remove] closes the other half of that same guard (P6b-0,
 * `docs/PLAN.md`'s own Open risks): `lastOrNull() == document` alone only ever checked the top, so
 * a document already sitting one level deeper - a notebook still open beneath a terminal opened
 * from it, say - survived a swap of whatever was stacked above it and came back a second time on
 * the next open for that same id, sharing [openNotebook]'s own `contentKey` with the surviving
 * original.
 */
private fun MutableList<NavKey>.chooseDetailThatFitsTheSurface(opened: () -> NavKey) {
  val document = opened()
  if (lastOrNull() == document) return
  remove(document)
  // A loop, not a single check: a Console stacks a second document over its notebook
  // (`openConsole`'s own doc comment), so unwinding to the surface before opening something else
  // has to remove both, not just the one on top - a single `removeAt` left the notebook behind,
  // `[Explorer, Editor("nb-1"), Editor("nb-2")]`, the exact two-documents-one-pane shape P6b-0
  // exists to prevent - see `NotebookBackStackTest`'s own Console cases.
  while (lastOrNull().isDocument()) removeAt(lastIndex)
  add(document)
}

/**
 * Special-casing which kinds are documents is only true until the next document opens; when it
 * does, add it here - the rule is "anything a surface opens replaces the current detail".
 *
 * [Settings] is not a document, but it occupies the same detail slot [Console] does when stacked
 * over one - swept here for the same reason: opening a real document must not leave it behind,
 * orphaned two levels deep, the way a bare `removeAt` once left a notebook behind under an open
 * Console (P6b-0). [Launcher] likewise: a launched document replaces it in place - the reference's
 * `add(item, 'main', {ref: id})` → `launcher.dispose()` (`launcher-extension/src/index.ts:95-106`).
 */
private fun Any?.isDocument(): Boolean = this is Editor ||
  this is FileEditor ||
  this is Terminal ||
  this is Console ||
  this is Settings ||
  this is Launcher
