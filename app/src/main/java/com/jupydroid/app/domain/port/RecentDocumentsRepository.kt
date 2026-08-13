package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.RecentDocument
import kotlinx.coroutines.flow.Flow

/**
 * The documents that were opened, most recent first.
 *
 * `RecentsManager`'s job (`packages/docmanager/src/recents.ts`), which is deliberately *not* the
 * notebook repository's: a recent is a record of an act, where a listing is a record of a tree.
 * Conflating them is what made a notebook inside a folder invisible to Recents before X2h - the
 * old list was `rootDir.listFiles()`, so where a document lived decided whether it could be found.
 *
 * ```
 * recentDocuments.record(RecentDocument("reports/summary.ipynb", WorkspaceEntryKind.FILE))
 * ```
 */
interface RecentDocumentsRepository {
  /** Ordered by recency, deduplicated by path, capped at [com.jupydroid.app.domain.model.MAX_RECENTS]. */
  fun observeRecents(): Flow<List<RecentDocument>>

  /**
   * Remembers [document] as the most recently opened, moving it up if it is already listed.
   *
   * An implementation over a real workspace refuses a path it cannot address, because a recent
   * that cannot be opened is worse than one that was never recorded.
   */
  suspend fun record(document: RecentDocument)

  /** `docmanager:clear-recents` - forgets the lot, which the reference offers from its submenu. */
  suspend fun clear()

  /**
   * Drops every entry whose path no longer names anything, and keeps the rest.
   *
   * The reference does **not** make a recent follow a rename; it drops what stops resolving.
   * `_isValid` treats a 404 as invalid (`recents.ts:169-181`) and `RecentsMenu.onBeforeAttach`
   * re-checks the whole list every time it is about to be shown. A surface that keeps recents on
   * screen has to ask at the moment it refreshes instead.
   */
  suspend fun revalidate()
}
