package com.jupydroid.app.data.disk

import android.os.FileObserver
import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.port.WorkspaceWatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The workspace's writes as the kernel reports them, through `inotify` - `android.os.FileObserver`.
 *
 * **The directory the file is in, not the tree.** inotify watches one directory per watch, and only
 * an open document acts on what is heard, so a folder is watched while a document in it is open.
 * vscode@3f3380c watches the workspace recursively because its explorer listens too
 * (`src/vs/platform/files/node/watcher/parcel/parcelWatcher.ts`); ours keeps its poll.
 *
 * **One observer per folder, shared by every file watched in it.** inotify answers a second watch
 * on a folder with the first one's descriptor, and `FileObserver` keeps one observer per
 * descriptor: a second observer took the folder's events from the first, and stopping either removed
 * the watch from both (`twoFilesWatchedInOneFolderAreEachHeard`,
 * `aFileStillWatchedIsHeardAfterItsNeighbourStops`).
 *
 * **`CLOSE_WRITE` and `MOVED_TO`**, measured on the device under proot: a cell's
 * `open('notes.py', 'w').write(...)` is reported as `CLOSE_WRITE` on the file, and this app's own save
 * as `MOVED_TO` onto it, because a save writes beside and renames over (ADR-0089). A `MODIFY` per
 * chunk would report a file half-written.
 *
 * ```
 * InotifyWorkspaceWatcher(notebookWorkspace(context)).writesTo("reports/notes.txt")
 * ```
 */
class InotifyWorkspaceWatcher(rootDir: File) : WorkspaceWatcher {
  private val root = WorkspaceRoot(rootDir)
  private val folders = mutableMapOf<File, FolderWatch>()

  /**
   * The stamp is taken when the write is heard: a file gone again by then reports nothing, since
   * there is nothing left for a document to read.
   */
  override fun writesTo(path: String): Flow<FileStamp> = callbackFlow {
    val file = root.fileAt(path).absoluteFile
    val folder = checkNotNull(file.parentFile) {
      "'$path' has no folder to watch; expected a path under the workspace"
    }
    val listener: (String) -> Unit = { name ->
      if (name == file.name) runCatching { file.stamp() }.onSuccess { trySend(it) }
    }
    listen(folder, listener)
    awaitClose { stopListening(folder, listener) }
  }.conflate()

  @Synchronized
  private fun listen(folder: File, listener: (String) -> Unit) {
    folders.getOrPut(folder) { FolderWatch(folder).also { it.startWatching() } }.listeners += listener
  }

  @Synchronized
  private fun stopListening(folder: File, listener: (String) -> Unit) {
    val watch = folders[folder] ?: return
    watch.listeners -= listener
    if (watch.listeners.isNotEmpty()) return
    watch.stopWatching()
    folders -= folder
  }

  /** The folder's one observer, handing each name written in it to whoever listens there now. */
  private class FolderWatch(folder: File) : FileObserver(folder, CLOSE_WRITE or MOVED_TO) {
    val listeners = CopyOnWriteArraySet<(String) -> Unit>()

    override fun onEvent(event: Int, name: String?) {
      if (name != null) listeners.forEach { it(name) }
    }
  }
}
