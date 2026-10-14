package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.port.WorkspaceWatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * A watcher that hears only what it is told, for callers with no directory to watch.
 *
 * Silent by default, so a screen composes and nothing it shows ever re-reads by itself; a test
 * announces the write a cell or a shell would have made.
 *
 * ```
 * watcher.announceWrite("notes.txt", workspace.writeText("notes.txt", "theirs"))
 * ```
 */
class FakeWorkspaceWatcher : WorkspaceWatcher {
  private val writes = MutableSharedFlow<Pair<String, FileStamp>>(extraBufferCapacity = ANNOUNCEMENTS_HELD)

  override fun writesTo(path: String): Flow<FileStamp> = writes.filter { it.first == path }.map { it.second }

  /** Reports a finished write on [path] to whoever is watching it now, as the disk's watcher would. */
  fun announceWrite(path: String, stamp: FileStamp) {
    check(writes.tryEmit(path to stamp)) {
      "announcement for '$path' dropped; expected at most $ANNOUNCEMENTS_HELD unread"
    }
  }

  private companion object {
    /** More than any test announces before it lets the collectors run. */
    const val ANNOUNCEMENTS_HELD = 64
  }
}
