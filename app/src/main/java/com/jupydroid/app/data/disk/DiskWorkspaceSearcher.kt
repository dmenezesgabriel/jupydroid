package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.CompiledQuery
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.compile
import com.jupydroid.app.domain.model.matchedLinesIn
import com.jupydroid.app.domain.port.WorkspaceSearcher
import com.jupydroid.app.glob.SearchFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * The workspace under [rootDir], walked for a query.
 *
 * Beside [DiskWorkspaceRepository] and shaped like it - the same [WorkspaceRoot] refusing anything
 * outside the tree, the same `workspaceEntriesUnder` deciding what is in a directory and in what
 * order, the same dispatcher. What it adds is the walk, and the three things a walk has to decline
 * to read.
 *
 * ```
 * DiskWorkspaceSearcher(notebookWorkspace(context)).search(SearchQuery("cat"), SearchScope())
 * ```
 */
class DiskWorkspaceSearcher(rootDir: File, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
  WorkspaceSearcher {
  private val root = WorkspaceRoot(rootDir)

  override fun search(query: SearchQuery, scope: SearchScope): Flow<MatchedFile> = flow {
    val regex = (query.compile() as? CompiledQuery.Matching)?.regex ?: return@flow
    var budget = MAX_SEARCH_RESULTS
    for (file in searchablePaths(scope).mapNotNull { matchedFileAt(it, regex) }) {
      if (budget <= 0) break
      val capped = file.cappedAt(budget)
      emit(capped)
      budget -= capped.matches.size
    }
  }.flowOn(ioDispatcher)

  /** What one file holds, or null when it holds nothing - a file with no match is never a result. */
  private fun matchedFileAt(path: String, regex: Regex): MatchedFile? {
    val text = readableTextAt(path) ?: return null
    val matches = matchedLinesIn(text, regex)
    return if (matches.isEmpty()) null else MatchedFile(path, matches)
  }

  /** Every file a [scope] reaches, in the order the walk finds them. One rule for both operations. */
  private fun searchablePaths(scope: SearchScope): Sequence<String> =
    pathsUnder("", SearchFilter(scope.include, scope.exclude))

  /**
   * The files under [directory] and everything below it, depth-first in the browser's order.
   *
   * A `Sequence` rather than a `Flow`: it is a plain recursive walk with nothing to suspend on, and
   * both callers already run inside [ioDispatcher]. Lazy either way, so a search that stops at its
   * cap stops the walk with it.
   */
  private fun pathsUnder(directory: String, filter: SearchFilter): Sequence<String> = sequence {
    for (entry in root.fileAt(directory).workspaceEntriesUnder(directory)) {
      when {
        entry.isADirectoryLink() -> Unit
        entry.kind == WorkspaceEntryKind.DIRECTORY ->
          if (filter.descendsInto(entry.path)) yieldAll(pathsUnder(entry.path, filter))
        // A notebook read as the JSON it is would report `cell_type` and a base64 output as hits,
        // which is what P2x-4's gate forbids. P2x-4 lifts this and makes a hit a *cell* hit.
        entry.path.endsWith(NOTEBOOK_SUFFIX) -> Unit
        filter.searches(entry.path) -> yield(entry.path)
      }
    }
  }

  /**
   * A directory reached through a symlink, which the walk does **not** enter.
   *
   * `search.followSymlinks` defaults to true, and this departs from it: the workspace lives inside
   * a rootfs and a cell can call `os.symlink`, so a loop is reachable - ripgrep detects one and
   * nothing here would. `docs/DECISIONS.md` carries it.
   */
  private fun WorkspaceEntry.isADirectoryLink(): Boolean =
    kind == WorkspaceEntryKind.DIRECTORY && root.isLink(root.fileAt(path))

  /**
   * The file's text, or null when there is nothing text-shaped to search.
   *
   * **Binary is ripgrep's rule**, which is the rule VS Code has because it states none of its own -
   * it sets `maxFileSize` to 16 GB and delegates the whole question (`rawSearchService.ts:85-87`).
   * *"A file is considered 'binary' if and only if it contains a NUL byte somewhere in its
   * contents"*, and the default mode - the one that applies to a recursive walk - removes such a
   * file from the search completely (ripgrep `GUIDE.md`, "Binary data").
   *
   * Checked on the bytes rather than after decoding, so a PNG is never turned into a megabyte of
   * replacement characters on the way to being discarded. A dangling symlink is not a file and is
   * passed over here, which is where a walk meets one.
   */
  private fun readableTextAt(path: String): String? {
    val file = root.fileAt(path)
    if (!file.isFile) return null
    val bytes = file.readBytes()
    return if (bytes.any { it == NUL }) null else bytes.decodeToString()
  }
}

/**
 * At most [budget] of this file's matches - `search.maxResults` spent as the walk goes rather than
 * counted afterwards, so a workspace with more matches than the cap stops being read at the cap.
 */
private fun MatchedFile.cappedAt(budget: Int): MatchedFile =
  if (matches.size <= budget) this else copy(matches = matches.take(budget))

/** `search.maxResults`' registered default (`search.contribution.ts:202-205`, `search.ts:32`). */
internal const val MAX_SEARCH_RESULTS = 20_000

private const val NOTEBOOK_SUFFIX = ".ipynb"

private const val NUL: Byte = 0
