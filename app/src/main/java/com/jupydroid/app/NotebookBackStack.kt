package com.jupydroid.app

import androidx.navigation3.runtime.NavKey

/**
 * Opens a notebook: pushed when it is not already open, revealed when it is.
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
 * **A different notebook stacks rather than replacing** (P8d). The back stack is the main area's
 * tab list: every open document keeps its entry, so its ViewModel - and its kernel session - stays
 * alive while another document is on top, and Back reveals the previous document the way closing
 * a tab reveals the neighbour. The single-document swap this replaced existed because a stacked
 * second editor kept its kernel alive *with nothing on screen*; that is now the point, not the
 * defect.
 *
 * ```
 * backStack.openNotebook("6f0b…")   // [Explorer] -> [Explorer, Editor("6f0b…")]
 * backStack.openNotebook("a91c…")   // -> [Explorer, Editor("6f0b…"), Editor("a91c…")]
 * backStack.openNotebook("6f0b…")   // -> [Explorer, Editor("a91c…"), Editor("6f0b…")]
 * ```
 */
fun MutableList<NavKey>.openNotebook(notebookId: String) {
  openInMainArea(Editor(notebookId))
}

/** Opens a plain file - [openNotebook] for the file editor's document kind. */
fun MutableList<NavKey>.openFile(filePath: String) {
  openInMainArea(FileEditor(filePath))
}

/**
 * Opens a shell - [openNotebook] for the terminal document kind.
 *
 * Unlike a notebook or a file, a terminal has no path to reveal by: [name] is the server's own
 * session name, minted fresh on every "New Terminal", so [openInMainArea]'s already-open check
 * never matches and a second tap always stacks a new shell - the reference's `terminal:create-new`
 * adds a second tab rather than revealing the first.
 */
fun MutableList<NavKey>.openTerminal(name: String) {
  openInMainArea(Terminal(name))
}

/**
 * Opens a Console for the notebook already showing - stacked on top of it, because the whole
 * point is running against the same kernel while the notebook stays open beneath, so this never
 * disposes what it is attached to. Revealed rather than duplicated on a second tap, [Console]'s
 * own de-dupe rule.
 *
 * The [removeAll] is [openStandaloneConsole]'s own constraint held globally: `entry<Console>`
 * draws from the one `consoleSession` holder, so a Console deeper in the stack than the incoming
 * one would keep composing against the newer session. JupyterLab tracks many consoles; this
 * holder is why this app cannot (P6d-5, `docs/DECISIONS.md`).
 *
 * ```
 * backStack.openConsole("nb-1")   // [Explorer, Editor("nb-1")] -> [Explorer, Editor("nb-1"), Console("nb-1")]
 * ```
 */
fun MutableList<NavKey>.openConsole(notebookId: String) {
  val console = Console(notebookId)
  if (lastOrNull() == console) return
  removeAll { it is Console }
  add(console)
}

/**
 * Opens a standalone Console - the Launcher card's `console:create` (`console-extension`), added to
 * the main area like any other document. The sweep first is the single-holder rule on [Console]
 * itself: explicit, because it must also reach a Console sitting under entries above its own
 * notebook. Disposing the launcher that minted it is the launch handler's rule
 * (`add(panel, 'main', {ref})` -> `launcher.dispose()`), not this opener's.
 */
fun MutableList<NavKey>.openStandaloneConsole(path: String, title: String) {
  removeAll { it is Console }
  openInMainArea(Console(path, title))
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
 * The extra [remove] over `openSettings`'s body is [openInMainArea]'s invariant applied to a
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
 * `main.title.closable = Array.from(labShell.widgets('main')).length > 1`
 * (`launcher-extension/src/index.ts:126`) - a launcher that is the *only* thing in the main area
 * gets no close affordance, because there is nothing beneath it to return to. The reference
 * counts main widgets; the stack's equivalent is the detail-slot entries, the launcher itself
 * included.
 */
fun List<NavKey>.launcherIsClosable(): Boolean = count { it.isDocument() } > 1

/**
 * The main area's open-or-activate, which is the whole of P8d's model: `shell.add(widget, 'main')`
 * when the document is not already open, `shell.activateById` when it is - and activate *is* a
 * move to the top of this stack, since the top entry is the one the scene draws. There is no sweep:
 * everything beneath stays open the way a tab stays open.
 *
 * [remove] before [add] is the reveal half: an equal key deeper in the stack would otherwise sit
 * beside its new twin, and equal keys share `contentKey` - one `ViewModelStore` between two
 * entries, the P6b-0 defect this has guarded since it was `lastOrNull() == document` alone.
 */
internal fun MutableList<NavKey>.openInMainArea(document: NavKey) {
  if (lastOrNull() == document) return
  remove(document)
  add(document)
}

/**
 * Which kinds occupy the main area - the entries `mainAreaIsEmpty` and `launcherIsClosable` count
 * and a surface's Back can reveal. Special-casing kinds is only true until the next main-area
 * widget opens; when it does, add it here. `internal` because the tab projection in
 * `MainAreaTabs.kt` filters on the same roll.
 */
internal fun Any?.isDocument(): Boolean = this is Editor ||
  this is FileEditor ||
  this is Terminal ||
  this is Console ||
  this is Settings ||
  this is Launcher
