package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.SessionType

/**
 * A [KernelLifecycle] whose session start binds the Space's spare kernel when the session suits it,
 * and otherwise starts its kernel as [server] would (ADR-0080).
 *
 * A session suits the spare when it asks for the default kernel, or for the spare's own spec, and
 * its document sits at the workspace root. The root rule exists because the spare's cwd is the
 * root, and a bound kernel keeps its cwd: `create_session` passes over a kernel id it already has
 * (jupyter_server 2.20.0 `services/sessions/sessionmanager.py`). A document in a folder that bound
 * it would write its files to the root.
 *
 * The spare is bound by id alone. A spare that died is then the server's to replace: given an id
 * it does not have, `create_session` starts the default kernel, which is the spare's spec. So a
 * dead spare costs the session the kernel start it would have paid anyway, and never fails it.
 *
 * ```
 * SpareBindingKernelLifecycle(OkHttpKernelLifecycle(connection), claimSpare = server::claimSpareKernel)
 *   .start("Cold Path.ipynb")  // -> the spare's kernel, in a new session
 * ```
 */
class SpareBindingKernelLifecycle(
  private val server: KernelLifecycle,
  /** `SpaceServer.claimSpareKernel`: the spare, once, when the predicate accepts it. */
  private val claimSpare: suspend (accepts: (BareKernel) -> Boolean) -> BareKernel?,
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : KernelLifecycle by server {
  override suspend fun start(
    documentPath: String,
    kernel: KernelChoice?,
    sessionType: SessionType?,
  ): StartedKernelSession {
    val spare = spareFor(documentPath, kernel) ?: return server.start(documentPath, kernel, sessionType)
    return server.start(documentPath, KernelChoice.Running(spare.kernelId), sessionType)
      .also { logBinding(spare, it) }
  }

  private suspend fun spareFor(documentPath: String, kernel: KernelChoice?): BareKernel? {
    if ('/' in documentPath) return null
    return when (kernel) {
      null -> claimSpare { true }
      is KernelChoice.Spec -> claimSpare { it.kernelName == kernel.name }
      is KernelChoice.Running -> null
    }
  }

  /** The server answered with another kernel when the spare had died, or the document had a session. */
  private fun logBinding(spare: BareKernel, started: StartedKernelSession) {
    if (started.kernelId == spare.kernelId) {
      log.reached(KernelLog.SPARE_BOUND, mapOf("kernelId" to spare.kernelId))
    } else {
      log.reached(KernelLog.SPARE_MISSED, mapOf("spareKernelId" to spare.kernelId, "kernelId" to started.kernelId))
    }
  }
}
