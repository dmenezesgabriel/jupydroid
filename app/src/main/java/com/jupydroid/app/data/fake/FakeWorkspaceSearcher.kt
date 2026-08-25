package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.CompiledQuery
import com.jupydroid.app.domain.model.MatchedFile
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

  /** Replaces what this workspace holds - what a test seeds through, and what a replace writes to. */
  fun holds(vararg files: Pair<String, String>) {
    this.files.clear()
    this.files.putAll(files)
  }

  override fun search(query: SearchQuery, scope: SearchScope): Flow<MatchedFile> = flow {
    val regex = (query.compile() as? CompiledQuery.Matching)?.regex ?: return@flow
    val filter = SearchFilter(scope.include, scope.exclude)
    searchable(filter).forEach { (path, text) ->
      val matches = matchedLinesIn(text, regex)
      if (matches.isNotEmpty()) emit(MatchedFile(path, matches))
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
