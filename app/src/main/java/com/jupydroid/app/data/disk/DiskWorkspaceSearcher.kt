package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.CompiledQuery
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.NOTEBOOK_SUFFIX
import com.jupydroid.app.domain.model.SearchProgress
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.compile
import com.jupydroid.app.domain.model.matchedCellsIn
import com.jupydroid.app.domain.model.matchedLinesIn
import com.jupydroid.app.domain.port.WorkspaceSearcher
import com.jupydroid.app.glob.SearchFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
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
class DiskWorkspaceSearcher(
  rootDir: File,
  /**
   * `search.maxResults`, which the reference reads **per query** rather than baking in
   * (`searchView.ts:1670`). A parameter for the same reason, so a settings screen can change it;
   * every caller otherwise takes the registered default.
   */
  private val maxResults: Int = MAX_SEARCH_RESULTS,
  /**
   * How a `.ipynb` is read - the same reader and the same leniency [DiskNotebookRepository] has,
   * because a notebook the editor can open is one the walk has to be able to search.
   */
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WorkspaceSearcher {
  private val root = WorkspaceRoot(rootDir)

  override fun search(query: SearchQuery, scope: SearchScope): Flow<SearchProgress> = flow {
    val regex = (query.compile() as? CompiledQuery.Matching)?.regex
    // A query that came to nothing walked nothing, so nothing was left out - see `SearchProgress`.
    emit(SearchProgress.Complete(limitHit = regex != null && emitMatchingFiles(regex, scope)))
  }.flowOn(ioDispatcher)

  /**
   * Emits one [SearchProgress.Found] per matching file, and answers whether the cap cost anything.
   *
   * `search.maxResults` spent as the walk goes rather than counted afterwards, so a workspace with
   * more matches than the cap stops being *read* at the cap. True the moment something is left
   * out - a file truncated mid-way, or a file the budget never reached - and false when the cap
   * happened to land exactly on the last match, which is the case a caller counting for itself
   * cannot tell from truncation.
   */
  private suspend fun FlowCollector<SearchProgress>.emitMatchingFiles(regex: Regex, scope: SearchScope): Boolean {
    var budget = maxResults
    for (file in searchablePaths(scope).mapNotNull { matchedFileAt(it, regex) }) {
      // A spent budget caps a file to *nothing*, which is why the emit is guarded rather than the
      // loop: "the walk never reached this file" and "the walk stopped inside it" are the same
      // arithmetic, and both mean something was left out.
      val capped = file.cappedAt(budget)
      if (capped.matches.isNotEmpty()) emit(SearchProgress.Found(capped))
      budget -= capped.matches.size
      if (capped.matches.size < file.matches.size) return true
    }
    return false
  }

  /** What one file holds, or null when it holds nothing - a file with no match is never a result. */
  private fun matchedFileAt(path: String, regex: Regex): MatchedFile? {
    val matches = if (path.endsWith(NOTEBOOK_SUFFIX)) matchedCellsAt(path, regex) else matchedTextAt(path, regex)
    return if (matches.isNullOrEmpty()) null else MatchedFile(path, matches)
  }

  /** An ordinary file, read as the characters it is. */
  private fun matchedTextAt(path: String, regex: Regex): List<MatchedLine>? =
    readableTextAt(path)?.let { matchedLinesIn(it, regex) }

  /**
   * A notebook, read as the cells it is, rather than skipped for not being plain text.
   *
   * **A notebook this app cannot read is declined rather than thrown on**, which makes it the
   * fourth kind of file a walk passes over. `readNotebook` raises for JSON
   * that is not an object and for a `cell_type` outside nbformat's three, and one such file in a
   * workspace must not end the walk over every other - the reference swallows a serializer's
   * failure per file for the same reason (`extHostNotebook.ts:555-558`, `catch (e) { return; }`).
   */
  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  private fun matchedCellsAt(path: String, regex: Regex): List<MatchedLine>? {
    val file = root.fileAt(path)
    if (!file.isFile) return null
    return try {
      matchedCellsIn(json.readNotebook(file, path), regex)
    } catch (unreadable: Exception) {
      null
    }
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
        filter.searches(entry.path) -> yield(entry.path)
      }
    }
  }

  /**
   * A directory reached through a symlink, which the walk does **not** enter.
   *
   * `search.followSymlinks` defaults to true, and this departs from it: the workspace lives inside
   * a rootfs and a cell can call `os.symlink`, so a loop is reachable - ripgrep detects one and
   * nothing here would.
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

/** At most [budget] of this file's matches - see `emitMatchingFiles` for what spends the budget. */
private fun MatchedFile.cappedAt(budget: Int): MatchedFile =
  if (matches.size <= budget) this else copy(matches = matches.take(budget))

/** `search.maxResults`' registered default (`search.contribution.ts:202-205`, `search.ts:32`). */
internal const val MAX_SEARCH_RESULTS = 20_000

private const val NUL: Byte = 0
