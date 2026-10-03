package com.jupydroid.app

import androidx.navigation3.runtime.NavKey

/**
 * `labShell.widgets('main')` - the tab strip's whole model: every main-area entry, in the order
 * the stack keeps it, which is the order the strip draws it in. The browser is the list pane and
 * never a tab, so [isDocument]'s roll is the projection and there is no second list to keep in
 * step.
 */
fun List<NavKey>.mainAreaTabs(): List<NavKey> = filter { it.isDocument() }

/**
 * `shell.activateById` for a tab's press - the entry moves to the top of the stack, which is what
 * the scene draws. Same remove-then-add [openInMainArea] does for a reveal: activating is the
 * reveal the tab strip performs.
 */
fun MutableList<NavKey>.activateMainAreaTab(tab: NavKey) {
  openInMainArea(tab)
}

/**
 * The order the strip draws the tabs in - **opening order**, which is not the stack's.
 *
 * `activateById` changes the dock panel's `currentWidget` and nothing else: a click on a Lumino
 * tab never moves the tab (`packages/application/src/shell.ts`). The back stack *has* to promote
 * the activated entry - its top is what the scene draws - so the strip keeps its own order beside
 * it: every tab still open keeps the place it first took, and a tab opened new joins at the end.
 * [known] is the strip's previous order; the caller hands the remembered list back each pass.
 *
 * ```
 * var order = emptyList<NavKey>()
 * order = backStack.mainAreaTabs().orderedAsOpened(order)   // opening order, stable across clicks
 * ```
 */
fun List<NavKey>.orderedAsOpened(known: List<NavKey>): List<NavKey> =
  known.filter { it in this } + filter { it !in known }

/**
 * The per-pass reconcile the shell's per-tab holders share: [order] reseeds to [tabs]' own
 * list, and the two key-indexed maps drop the keys that left.
 *
 * Dead keys are not tidiness: a tab's entry in [liveDocumentPaths] keeps answering
 * [liveDocumentPath] after its tab closes, so a file reopened under the name a closed tab was
 * renamed to would be marked by the path the tab left behind. OpenDocumentMarkingTest is the
 * composition-level proof.
 *
 * ```
 * LaunchedEffect(orderedTabs) { reconcileOpenTabs(orderedTabs, openTabOrder, liveTabTitles, openDocumentPaths) }
 * ```
 */
fun reconcileOpenTabs(
  tabs: List<NavKey>,
  order: MutableList<NavKey>,
  liveTabTitles: MutableMap<NavKey, String>,
  liveDocumentPaths: MutableMap<NavKey, String>,
) {
  order.clear()
  order.addAll(tabs)
  val open = tabs.toSet()
  liveTabTitles.keys.retainAll(open)
  liveDocumentPaths.keys.retainAll(open)
}

/**
 * Where the document this key opened lives **now** - the key keeps the path it was opened under,
 * because replacing it would re-create the entry and drop its `ViewModelStore`, so a rename is
 * reported beside it into [liveDocumentPaths] (the way `_contextsForPath` filters contexts by
 * their current path, never by the one a widget was created with). Null for the kinds with no
 * path to follow - the answer for them is in the key itself.
 */
fun NavKey.liveDocumentPath(liveDocumentPaths: Map<NavKey, String>): String? = when (this) {
  is Editor -> liveDocumentPaths[this] ?: notebookId
  is FileEditor -> liveDocumentPaths[this] ?: path
  else -> null
}

/**
 * The path this key opened its document under - what its field still says. Unlike
 * [liveDocumentPath] it never moves, which is the point: the strip finds a document *through*
 * its own rename by it, in the one case where the live-path map cannot know yet.
 */
fun NavKey.openedUnderPath(): String? = when (this) {
  is Editor -> notebookId
  is FileEditor -> path
  else -> null
}

/**
 * The live document this key opened - a tab's own lookup. By [OpenDocument.openedAs] rather than
 * [liveDocumentPath]: a rename that lands while the document's screen is out of composition moves
 * [OpenDocument.path] before the map learns of it, and the opened-under path is the one address
 * that survives.
 */
fun NavKey.documentIn(openDocuments: OpenDocuments): OpenDocument? = openedUnderPath()?.let(openDocuments::openedUnder)

/**
 * `title.label` for one tab.
 *
 * For a document it is the basename of [liveDocumentPath] (`PathExt.basename(localPath)`,
 * `docregistry/src/default.ts:618-621`). The other kinds carry labels of their own: the
 * terminal's session name the running panel already shows (`terminals/1`), the console's
 * `name || 'Console %1'`, the launcher's and settings' own nouns.
 */
fun NavKey.mainAreaTabTitle(liveDocumentPaths: Map<NavKey, String>): String = when (this) {
  is Editor -> liveDocumentPath(liveDocumentPaths)!!.substringAfterLast('/')
  is FileEditor -> liveDocumentPath(liveDocumentPaths)!!.substringAfterLast('/')
  is Terminal -> "Terminal $name"
  is Console -> title
  Launcher -> "Launcher"
  Settings -> "Settings"
  else -> error(
    "mainAreaTabTitle: $this is not a main-area key " +
      "(Editor|FileEditor|Terminal|Console|Launcher|Settings)",
  )
}

/**
 * `title.label` resolved the way [mainAreaTabTitle] leaves it live: a document's **current** path
 * (`title.label` follows `context.path` through a rename), then a terminal's reported OSC title,
 * then the key's own static noun. The one resolution both the tab strip and the Running panel's
 * Open Tabs rows draw, because the reference draws them from the same property -
 * `OpenTab.label()` returns `TabBarSvg.titleLabel(this._widget.title)`, the strip's own value
 * (`running-extension/src/opentabs.ts:113-115`).
 */
fun NavKey.runningTabTitle(
  openDocuments: OpenDocuments,
  liveDocumentPaths: Map<NavKey, String>,
  liveTabTitles: Map<NavKey, String>,
): String = documentIn(openDocuments)?.path?.substringAfterLast('/')
  ?: liveTabTitles[this]
  ?: mainAreaTabTitle(liveDocumentPaths)

/** What an Open Tabs row acts on - the tab's key - and what it is called, resolved as the strip resolves it. */
data class RunningTab(val key: NavKey, val title: String)

/**
 * A tab's close - `TabBar.onCloseRequest`. Removing the entry is the whole act when it was not
 * current; when it was, the reference re-selects a surviving sibling (the next tab in the bar,
 * else the previous - `TabBar._onTabCloseRequested`'s currentIndex adjustment), never the file
 * browser, which is not a main-area widget at all. [order] is the strip's opening order, so the
 * sibling picked is the one the eye expects.
 */
fun MutableList<NavKey>.closeMainAreaTab(tab: NavKey, order: List<NavKey>) {
  val wasCurrent = lastOrNull() == tab
  remove(tab)
  if (!wasCurrent) return
  // Siblings still on the stack only - a stale [order] could name a tab that closed earlier.
  val survivors = order.filter { it in this }
  val at = order.indexOf(tab)
  val next = survivors.firstOrNull { order.indexOf(it) > at } ?: survivors.lastOrNull()
  next?.let(::activateMainAreaTab)
}
