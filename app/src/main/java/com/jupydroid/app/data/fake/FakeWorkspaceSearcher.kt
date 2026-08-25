package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.CompiledQuery
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.SearchProgress
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import com.jupydroid.app.domain.model.compile
import com.jupydroid.app.domain.model.matchedLinesIn
import com.jupydroid.app.domain.port.WorkspaceSearcher
import com.jupydroid.app.glob.SearchFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A workspace searched in memory, for callers with no directory to offer.
 *
 * The container's standing pattern - every port has a fake, so a screen composes before its
 * adapter is wired (`FakeWorkspaceRepository`, `FakeKernelGateway`). This is also what an x86_64
 * device gets, where no Space runs: the search panel draws and finds nothing rather than being
 * absent.
 *
 * ```
 * FakeWorkspaceSearcher(mapOf("notes.py" to "import os"))
 * ```
 */
class FakeWorkspaceSearcher(textByPath: Map<String, String> = emptyMap()) : WorkspaceSearcher {
  private val files = textByPath.toMutableMap()

  /** Replaces what this workspace holds - what a test seeds through. */
  fun holds(vararg files: Pair<String, String>) {
    this.files.clear()
    this.files.putAll(files)
  }

  override fun search(query: SearchQuery, scope: SearchScope): Flow<SearchProgress> = flow {
    matchingFiles(query, scope).forEach { emit(SearchProgress.Found(it)) }
    // Nothing bounds a map held in memory, so nothing was ever left out of one - the cap and the
    // walk that spends it are `DiskWorkspaceSearcher`'s.
    emit(SearchProgress.Complete(limitHit = false))
  }

  /** Every file this workspace holds that the query and the scope both reach, in path order. */
  private fun matchingFiles(query: SearchQuery, scope: SearchScope): List<MatchedFile> {
    val regex = (query.compile() as? CompiledQuery.Matching)?.regex ?: return emptyList()
    val filter = SearchFilter(scope.include, scope.exclude)
    return searchable(filter).mapNotNull { (path, text) ->
      matchedLinesIn(text, regex).takeIf { it.isNotEmpty() }?.let { MatchedFile(path, it) }
    }
  }

  /**
   * The files a scope reaches, in path order.
   *
   * Sorted rather than held in insertion order: the port promises workspace order, and a flat map
   * of root-level paths has no directories to put first - so the natural name order is all of that
   * rule this fake can express. The disk searcher owes the whole of it.
   */
  private fun searchable(filter: SearchFilter): List<Pair<String, String>> =
    files.toList().sortedBy { (path, _) -> path }.filter { (path, _) -> filter.searches(path) }
}
