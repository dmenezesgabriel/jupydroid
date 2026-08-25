package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import kotlinx.coroutines.flow.Flow

/**
 * Finds a query across the whole workspace, and replaces it there.
 *
 * `ISearchService.textSearch` and `IReplaceService.replace` as one port, because they are one
 * feature: the reference's replace-all walks the results the search just produced
 * (`searchModel.ts`), and a port that could find but not write would leave the writing to a screen.
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

  /**
   * Replaces every match [search] would report, and answers how many that was.
   *
   * The same query and the same scope, so a *"replace all"* cannot reach a file the results on
   * screen never named. `preserveCase` is here rather than on the query because it changes what a
   * replacement becomes and not what matches - the reference's own split, `IReplaceOptions` versus
   * the query (`searchmodel.ts:295-311`).
   *
   * Destructive, and there is no undo across files: the caller asks first, which is P2x-5.
   */
  suspend fun replaceAll(query: SearchQuery, replacement: String, preserveCase: Boolean, scope: SearchScope): Int
}
