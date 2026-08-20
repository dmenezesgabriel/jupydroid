package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.StaleKernelSessionException

/**
 * The session a notebook holds: the one it has, and the one it gets when that server is gone.
 *
 * Split out of [NotebookKernel] for the reason that class was split out of `EditorViewModel` -
 * detekt counted the functions and the answer is the object that wants to exist. Since P1c there
 * is one: the app stops its own server when it is left alone, so "which session is this notebook
 * on" stopped being a field and became a question with an answer that can change underfoot.
 *
 * ```
 * val session = NotebookKernelSession(gateway, BASE_SPACE) { uiState.value.notebookId }
 * session.reopeningIfStale { open -> gateway.execute(open, cell.id, cell.source) }
 * ```
 */
class NotebookKernelSession(
  private val kernelGateway: KernelGateway,
  private val space: Space,
  /**
   * Read when a session is opened rather than captured, because since X1b the notebook's id *is*
   * its path and a rename moves it.
   */
  private val documentPath: () -> String,
) {
  /** The open session, or null when this notebook has never asked for a kernel. */
  var opened: KernelSession? = null
    private set

  /**
   * The notebook's own path goes to the kernel, because that is what gives the kernel a working
   * directory - so a cell writing `data.csv` writes it beside the notebook, which is the whole
   * point of the workspace being the kernel's `HOME` (X1a). The kernel keeps the directory it
   * started in if the file is later renamed - a process's cwd is fixed once it is running - which
   * is what happens in the reference too, where a rename retitles the session and leaves the
   * running kernel where it was.
   */
  suspend fun open(): KernelSession = opened ?: kernelGateway.connect(space, documentPath()).also { opened = it }

  /**
   * Runs [use] against the open session, opening a fresh one and trying once more if the session
   * belonged to a server that has since stopped.
   *
   * **P1c stopped that server on the app's own policy, so the app brings it back rather than
   * reporting it.** The reference has no equivalent: nothing there stops a server behind the
   * client, so a dead kernel is always news and `SessionContext` displays its error
   * (`packages/apputils/src/sessioncontext.tsx:1167`). Exactly one retry - a second refusal is a
   * real failure and belongs to the caller.
   */
  @Suppress("SwallowedException") // the refusal is the instruction to reconnect, not an error to report
  suspend fun <T> reopeningIfStale(use: suspend (KernelSession) -> T): T =
    try {
      use(open())
    } catch (stale: StaleKernelSessionException) {
      opened = null
      use(open())
    }

  /** Hands back the open session and forgets it, for a caller that is closing the socket. */
  fun forget(): KernelSession? = opened.also { opened = null }
}
