package com.jupydroid.app.domain.model

import com.jupydroid.app.domain.port.KernelSessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * A file that is deleted takes the kernel session at its path with it - `DocumentManager.deleteFile`
 * asking `sessions.stopIfNeeded(path)` (jupyterlab@7f0e6906 `packages/docmanager/src/manager.ts`),
 * so the Running panel lists no kernel for a file that is gone.
 *
 * The reference asks from inside the one call every delete goes through. Here the callers reach the
 * workspace port directly, so the stop listens to what that port announces instead: a delete and
 * the target of an overwrite both arrive as [WorkspaceChange.Deleted], whoever asked for them. It
 * therefore lands *after* the delete where the reference's lands before, and a delete the disk
 * refuses costs no kernel.
 *
 * By path over the server's own list, never by open document: a notebook's session outlives its
 * tab, and a notebook deleted with its tab closed has nothing else left to end it.
 *
 * ```
 * DeletedDocumentSessions(workspaceRepository.changes, kernelSessions).watchIn(applicationScope)
 * ```
 */
class DeletedDocumentSessions(private val changes: Flow<WorkspaceChange>, private val kernelSessions: KernelSessions) {
  /**
   * Serves every deletion announced while [scope] lives; the [Job] ends the watch.
   *
   * Each stop runs on its own: the workspace's `emit` waits for its listeners to take a change, and
   * one held here for the length of a kernel's shutdown would hold an overwrite between its
   * `Deleted` and its `Renamed`.
   */
  fun watchIn(scope: CoroutineScope): Job = scope.launch {
    changes.filterIsInstance<WorkspaceChange.Deleted>().collect { launch { stopIfNeeded(it.path) } }
  }

  /**
   * `SessionManager.stopIfNeeded` (jupyterlab@7f0e6906 `packages/services/src/session/manager.ts`):
   * shut down only when exactly one session has the path, and "always succeed" - a refused listing
   * or a session the server culled a moment before must not end the watch.
   */
  private suspend fun stopIfNeeded(path: String) {
    try {
      val only = kernelSessions.running().singleOrNull { it.path == path } ?: return
      kernelSessions.shutdown(only.serverSessionId)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
    }
  }
}
