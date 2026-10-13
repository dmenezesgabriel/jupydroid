package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.port.KernelSessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * `_updatePath`'s session half (jupyterlab@7f0e6906 `packages/docregistry/src/context.ts`): the
 * notebook moved, so the session it is on takes the new path and name, and the Running panel's row
 * opens the file where it lives now.
 *
 * ```
 * kernel.sessionRename.follow("g/b.ipynb")  // PATCH api/sessions/<id> {path, name}
 * ```
 */
class NotebookSessionRename(
  private val session: NotebookKernelSession,
  private val kernelSessions: KernelSessions,
  private val scope: CoroutineScope,
) {
  /**
   * A notebook with no session yet has nothing to move: its next connect already reads the live
   * path. Sent and not awaited, as the reference's `void` is: a refusal - the server culled the
   * session a moment before - is no reason for the rename to fail, and `running()`'s diff ends
   * that session on its own.
   */
  fun follow(documentPath: String) {
    val open = session.opened ?: return
    scope.launch {
      try {
        kernelSessions.setPath(open.serverSessionId, documentPath)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
      }
    }
  }
}
