package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.SearchProgress
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import kotlinx.coroutines.flow.Flow

/**
 * Finds a query across the whole workspace.
 *
 * `ISearchService.textSearch`, and **only** that. An earlier draft bundled `IReplaceService.replace` here
 * too, on the argument that they are one feature; reading the reference retired it. VS Code keeps
 * them apart because they need different machinery: search walks files, and replace goes through
 * the *editor model* stack - `ReplaceService.replace` builds `ResourceTextEdit`s and hands them to
 * `IBulkEditService` (`replaceService.ts:115-118`), which resolves a text model for every resource,
 * open or not (`bulkTextEdits.ts:227`), edits the models, and only then saves
 * (`replaceService.ts:136`). A replace that wrote files behind an open document's back could not
 * reach that, and `WorkspaceSearcher` is the wrong owner for it; it has its own home.
 *
 * **Beside [WorkspaceRepository] rather than inside it.** The repository answers what is *at* a
 * path; this answers which paths hold something, which is a different question with a different
 * shape - it is a stream, it takes a query, and it reads far more files than it reports.
 *
 * ```
 * searcher.search(SearchQuery("cat"), SearchScope(include = "*.py")).collect { println(it) }
 * ```
 */
interface WorkspaceSearcher {
  /**
   * Every file with a match **as it is found**, then one [SearchProgress.Complete].
   *
   * Streamed rather than gathered, which is `ISearchProgressItem`'s whole point: a workspace walk
   * is slow enough that a list at the end is a blank screen for the length of it. One item per
   * file, in workspace order - directories first then natural name order, the order
   * `List<WorkspaceEntry>.inWorkspaceOrder` already gives the browser - so what arrives first is
   * what a listing would have shown first.
   *
   * A file with no match is never emitted. An empty or unparseable query finds nothing at all,
   * which is `CompiledQuery.Empty` and `CompiledQuery.Invalid` - and **still completes**, because
   * a walk that found nothing and a flow nobody ran look identical otherwise.
   */
  fun search(query: SearchQuery, scope: SearchScope): Flow<SearchProgress>
}
