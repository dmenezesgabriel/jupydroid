package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import kotlinx.coroutines.flow.Flow

/**
 * Finds a query across the whole workspace.
 *
 * `ISearchService.textSearch`, and **only** that. P2x-1c bundled `IReplaceService.replace` in here
 * too, on the argument that they are one feature; reading the reference retired it. VS Code keeps
 * them apart because they need different machinery: search walks files, and replace goes through
 * the *editor model* stack - `ReplaceService.replace` builds `ResourceTextEdit`s and hands them to
 * `IBulkEditService` (`replaceService.ts:115-118`), which resolves a text model for every resource,
 * open or not (`bulkTextEdits.ts:227`), edits the models, and only then saves
 * (`replaceService.ts:136`). A replace that wrote files behind an open document's back could not
 * reach that, and `WorkspaceSearcher` is the wrong owner for it. P2x-5 gives it its own home.
 *
 * **Beside [WorkspaceRepository] rather than inside it.** The repository answers what is *at* a
 * path; this answers which paths hold something, which is a different question with a different
 * shape - it is a stream, it takes a query, and it reads far more files than it reports.
 *
 * ```
 * searcher.search(SearchQuery("cat"), SearchScope(include = "*.py")).collect { println(it.path) }
 * ```
 */
interface WorkspaceSearcher {
  /**
   * Every file with a match, **as they are found**.
   *
   * Streamed rather than gathered, which is `ISearchProgressItem`'s whole point: a workspace walk
   * is slow enough that a list at the end is a blank screen for the length of it. One item per
   * file, in workspace order - directories first then natural name order, the order
   * `List<WorkspaceEntry>.inWorkspaceOrder` already gives the browser - so what arrives first is
   * what a listing would have shown first.
   *
   * A file with no match is never emitted. An empty or unparseable query finds nothing at all,
   * which is `CompiledQuery.Empty` and `CompiledQuery.Invalid`.
   */
  fun search(query: SearchQuery, scope: SearchScope): Flow<MatchedFile>
}
