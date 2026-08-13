package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What "recent" means, which is JupyterLab's answer and not ours.
 *
 * Recents is a **history of documents opened**, not a scan of a directory - and that distinction is
 * the whole point of X2h. `RecentsManager.addRecent`
 * (`packages/docmanager/src/recents.ts:94-110`) removes any entry already holding the path, then
 * `unshift`s the new one at the front; `_setRecents` (`:186-201`) slices the result to
 * `maximalRecentsLength`, whose default is **10**
 * (`docmanager-extension/schema/plugin.json:153-158`).
 *
 * The display order is a second rule, from `RecentsMenu.updateItems`
 * (`packages/mainmenu-extension/src/recents.ts`): directories sort before documents, and the
 * comparator returns 0 for equal kinds - so recency survives inside each group.
 *
 * Both are pure functions over domain types, so they live beside the type they order, exactly as
 * [inWorkspaceOrder] does for a directory listing.
 */
class RecentDocumentsTest {
  @Test
  fun theMostRecentGoesToTheFront() {
    val recents = listOf(document("a.ipynb"), document("b.ipynb"))

    val recorded = recents.withMostRecent(document("c.ipynb"))

    assertEquals(listOf("c.ipynb", "a.ipynb", "b.ipynb"), recorded.map { it.path })
  }

  /**
   * Re-opening something already listed moves it up rather than listing it twice.
   *
   * `addRecent` splices the existing index out before it unshifts, which is what makes the list a
   * set ordered by recency instead of a log.
   */
  @Test
  fun openingSomethingAgainMovesItRatherThanRepeatingIt() {
    val recents = listOf(document("a.ipynb"), document("b.ipynb"), document("c.ipynb"))

    val recorded = recents.withMostRecent(document("c.ipynb"))

    assertEquals(listOf("c.ipynb", "a.ipynb", "b.ipynb"), recorded.map { it.path })
  }

  /** The path is the identity, so the same path recorded as a different kind is still one entry. */
  @Test
  fun thePathIsTheIdentity() {
    val recents = listOf(RecentDocument("reports", WorkspaceEntryKind.DIRECTORY))

    val recorded = recents.withMostRecent(RecentDocument("reports", WorkspaceEntryKind.FILE))

    assertEquals(1, recorded.size)
    assertEquals(WorkspaceEntryKind.FILE, recorded.single().kind)
  }

  @Test
  fun theListStopsAtTheCap() {
    val recents = (1..MAX_RECENTS).map { document("n$it.ipynb") }

    val recorded = recents.withMostRecent(document("newest.ipynb"))

    assertEquals(MAX_RECENTS, recorded.size)
    assertEquals("newest.ipynb", recorded.first().path)
    assertEquals("the oldest should have fallen off", "n${MAX_RECENTS - 1}.ipynb", recorded.last().path)
  }

  /** The reference's default, so a phone remembers as much as a browser does. */
  @Test
  fun theCapIsTheReferenceDefault() {
    assertEquals(10, MAX_RECENTS)
  }

  @Test
  fun directoriesAreListedBeforeDocuments() {
    val recents = listOf(
      document("a.ipynb"),
      RecentDocument("reports", WorkspaceEntryKind.DIRECTORY),
      document("b.py"),
    )

    assertEquals(listOf("reports", "a.ipynb", "b.py"), recents.inRecentsOrder().map { it.path })
  }

  /**
   * Within a kind, recency is left alone.
   *
   * The reference's comparator returns 0 when the content types match, and `Array.prototype.sort`
   * is stable - so the ordering is "directories first" and nothing more.
   */
  @Test
  fun recencySurvivesInsideEachGroup() {
    val recents = listOf(
      document("newest.ipynb"),
      RecentDocument("second", WorkspaceEntryKind.DIRECTORY),
      document("oldest.ipynb"),
      RecentDocument("first", WorkspaceEntryKind.DIRECTORY),
    )

    val ordered = recents.inRecentsOrder().map { it.path }

    assertEquals(listOf("second", "first", "newest.ipynb", "oldest.ipynb"), ordered)
  }

  /**
   * Opening a document remembers the folder it was in, too.
   *
   * `_recordAsRecentlyOpened` records the path and then, for anything that is not itself a
   * directory, its parent as `contentType: 'directory'`
   * (`packages/docmanager/src/widgetmanager.ts:414-422`). That is what makes a folder you actually
   * work in reachable in one tap instead of by descending to it again.
   */
  @Test
  fun openingADocumentAlsoRemembersItsFolder() {
    val recorded = recentsForOpening("reports/summary.ipynb")

    assertEquals(
      listOf(
        RecentDocument("reports/summary.ipynb", WorkspaceEntryKind.FILE),
        RecentDocument("reports", WorkspaceEntryKind.DIRECTORY),
      ),
      recorded,
    )
  }

  @Test
  fun theFolderIsTheImmediateOneRatherThanTheTop() {
    val recorded = recentsForOpening("a/b/c/deep.py")

    assertEquals("a/b/c", recorded.last().path)
  }

  /**
   * A document at the top level records no folder.
   *
   * The reference records the root as `''` here, and we depart: the explorer already stands at the
   * root, so a row pointing at it would navigate nowhere. Everything else about the rule is theirs.
   */
  @Test
  fun aDocumentAtTheRootRecordsOnlyItself() {
    val recorded = recentsForOpening("notes.ipynb")

    assertEquals(listOf(RecentDocument("notes.ipynb", WorkspaceEntryKind.FILE)), recorded)
  }

  private fun document(path: String) = RecentDocument(path, WorkspaceEntryKind.FILE)
}
