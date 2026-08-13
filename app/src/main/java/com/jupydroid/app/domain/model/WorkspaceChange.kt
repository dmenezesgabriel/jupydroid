package com.jupydroid.app.domain.model

/**
 * Something that happened to a path, announced so that whatever is open on it can react.
 *
 * The reference's `Contents.IChangedArgs`: one signal off the contents layer carrying a `type`,
 * which every open `Context` listens to (`packages/docregistry/src/context.ts:93`). One channel
 * rather than one per verb, because the listeners are the same listeners and a second channel is
 * how the two fall out of step.
 *
 * It travels from the repository rather than through the screens because a change can come from
 * anywhere - the browser beside the document, or a second client on the same tree.
 *
 * ```
 * workspaceRepository.changes.collect { change ->
 *   when (change) {
 *     is WorkspaceChange.Renamed -> pathAfterRename(path, change.from, change.to)?.let(::adopt)
 *     is WorkspaceChange.Deleted -> if (path == change.path) stopWriting()
 *   }
 * }
 * ```
 */
sealed interface WorkspaceChange {
  /** A path that moved. Ask [pathAfterRename] where it went - a folder takes its contents along. */
  data class Renamed(val from: String, val to: String) : WorkspaceChange

  /**
   * A path that is gone.
   *
   * A document open on it must stop writing before anything closes it: our teardown flushes the
   * last edit, where the reference's `SaveHandler.dispose()` clears its timer and writes nothing
   * (`docmanager/src/savehandler.ts:62-69`). A flush after the delete would put the file back.
   */
  data class Deleted(val path: String) : WorkspaceChange
}
