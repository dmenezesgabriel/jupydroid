package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.pathAfterRename
import com.jupydroid.app.domain.port.WorkspaceWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * An open document following its file: where this app moves it, that it was deleted, and what
 * anything else writes to it.
 *
 * The moves are `Context._onFileChanged` (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`):
 * the document hears them off the contents layer rather than from whichever screen did the rename,
 * because a document that kept a stale path would write itself back under the old name. The writes
 * are vscode@3f3380c's `onDidFilesChange`
 * (`src/vs/workbench/services/textfile/common/textFileEditorModelManager.ts`), which re-reads a
 * clean model when anything writes its file - whether this one is clean, and whether the stamp is
 * its own save's, is [onWritten]'s to decide, since only the document holds both.
 *
 * One class for a notebook and a text file, which follow their files the same way.
 *
 * ```
 * DocumentFileFollow(workspaceRepository.changes, workspaceWatcher, { livePath }, ::adopt, ::stopWriting) {
 *   conflict.followDisk(it)
 * }.followIn(viewModelScope)
 * ```
 */
class DocumentFileFollow(
  private val changes: Flow<WorkspaceChange>,
  private val watcher: WorkspaceWatcher,
  /** Where the document lives now - read at each change, since a rename moves it. */
  private val path: () -> String,
  /** This app moved the file there; the document takes on the new path. */
  private val onMoved: (String) -> Unit,
  /** This app deleted the file; the document must stop writing before anything closes it. */
  private val onGone: () -> Unit,
  /** Something wrote the file, which had [FileStamp] when the write was heard. */
  private val onWritten: suspend (FileStamp) -> Unit,
) {
  /** The path the watch is on, moved by a rename so the old name's writes are someone else's. */
  private val watched = MutableStateFlow(path())

  /** Both follows, for as long as [scope] lives - the document's own. */
  fun followIn(scope: CoroutineScope) {
    scope.launch { changes.collect(::follow) }
    scope.launch { writesSettled().collect { onWritten(it) } }
  }

  private fun follow(change: WorkspaceChange) {
    when (change) {
      is WorkspaceChange.Renamed -> pathAfterRename(path(), change.from, change.to)?.let(::moveTo)
      is WorkspaceChange.Deleted -> if (change.path == path()) onGone()
    }
  }

  private fun moveTo(moved: String) {
    watched.value = moved
    onMoved(moved)
  }

  /**
   * A cell writing in chunks closes the file many times, and vscode aggregates a burst for
   * `FILE_CHANGES_HANDLER_DELAY = 75` ms before it acts (`src/vs/platform/files/node/watcher/parcel/parcelWatcher.ts`).
   * Ours waits for the 75 ms to pass quietly rather than for the first event's 75 ms to end: a
   * `sample` ticks for as long as it is collected. A writer that never pauses is followed once it
   * does, and the stamp makes a second follow of one write a no-op.
   */
  @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
  private fun writesSettled(): Flow<FileStamp> =
    watched.flatMapLatest { watcher.writesTo(it) }.debounce(WRITES_SETTLE_MILLIS)

  private companion object {
    const val WRITES_SETTLE_MILLIS = 75L
  }
}
