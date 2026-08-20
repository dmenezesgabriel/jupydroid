package com.jupydroid.app.domain.model

/**
 * Something that was opened, remembered so it can be opened again without being hunted for.
 *
 * A recent is *not* a file that changed - it is a document a person opened, which is what
 * `RecentsManager` records (`packages/docmanager/src/recents.ts`) and why the list survives a
 * kernel writing into the workspace behind it. The path is the identity, as it is everywhere else
 * since X1b (`Contents.IModel.path`, suffix included).
 *
 * [kind] separates a folder from a document because the reference does: `addRecent` records the
 * containing directory alongside the document (`docmanager/src/widgetmanager.ts:418-422`), and a
 * recent directory is *navigated to* rather than opened - the reference sends it to the file
 * browser's `openPath` and only a document to `docmanager:open`
 * (`packages/mainmenu-extension/src/recents.ts`).
 */
data class RecentDocument(val path: String, val kind: WorkspaceEntryKind)

/**
 * How many are remembered: `maxNumberRecents`, whose reference default is 10
 * (`docmanager-extension/schema/plugin.json:153-158`).
 */
const val MAX_RECENTS: Int = 10

/**
 * [document] at the front, listed once, with the oldest dropped past [cap].
 *
 * `addRecent` (`recents.ts:94-110`) splices out any entry already holding the path before it
 * `unshift`s the new one, and `_setRecents` (`:186-201`) slices to the cap. That is what makes
 * this a set ordered by recency rather than a log of every open.
 *
 * ```
 * recents.withMostRecent(RecentDocument("reports/summary.ipynb", WorkspaceEntryKind.FILE))
 * ```
 */
fun List<RecentDocument>.withMostRecent(document: RecentDocument, cap: Int = MAX_RECENTS): List<RecentDocument> =
  (listOf(document) + filterNot { it.path == document.path }).take(cap)

/**
 * What opening the document at [path] should remember: the document, and the folder it sits in.
 *
 * `_recordAsRecentlyOpened` records the path and then its parent as `contentType: 'directory'`
 * (`packages/docmanager/src/widgetmanager.ts:414-422`), so a folder someone actually works in is
 * one tap away instead of a descent. **One departure:** the reference records the root as `''` for
 * a top-level document, and we do not - the explorer already stands at the root, so that row would
 * navigate nowhere.
 *
 * Returned in the order the reference records them - the document, then its folder - so a caller
 * recording them in turn leaves the folder at the head of the list, exactly as two `addRecent`
 * calls do. Which of the two leads is invisible anyway, since [inRecentsOrder] hoists directories
 * before anything is shown.
 *
 * ```
 * recentsForOpening("reports/summary.ipynb").forEach { recentDocuments.record(it) }
 * ```
 */
fun recentsForOpening(path: String): List<RecentDocument> {
  val document = RecentDocument(path, WorkspaceEntryKind.FILE)
  val folder = path.substringBeforeLast('/', missingDelimiterValue = "")
  if (folder.isEmpty()) return listOf(document)
  return listOf(document, RecentDocument(folder, WorkspaceEntryKind.DIRECTORY))
}

/**
 * Directories first, recency untouched inside each group.
 *
 * `RecentsMenu.updateItems` sorts on content type alone and returns 0 when the two match
 * (`packages/mainmenu-extension/src/recents.ts`); `Array.prototype.sort` is stable, so the rule is
 * "directories first" and nothing more. [sortedBy] is stable too, which is what carries that.
 *
 * ```
 * recents.inRecentsOrder()
 * ```
 */
fun List<RecentDocument>.inRecentsOrder(): List<RecentDocument> = sortedBy { it.kind != WorkspaceEntryKind.DIRECTORY }

/**
 * What the recents section shows while [directory] is being browsed: everything at the root, and
 * nothing anywhere else.
 *
 * A recents list is not a property of the folder you are standing in. The reference's own surfaces
 * are global - a File menu submenu (`mainmenu-extension/src/recents.ts`) and a sidebar panel
 * (`running-extension/src/recents.ts`) - and neither is scoped to a directory. One level down, the
 * screen is about *that folder*, and the width belongs to its rows.
 *
 * ```
 * recents.shownWhileBrowsing(uiState.path)
 * ```
 */
fun List<RecentDocument>.shownWhileBrowsing(directory: String): List<RecentDocument> =
  if (directory.isEmpty()) inRecentsOrder() else emptyList()
