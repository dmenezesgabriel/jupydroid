package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.KernelSessions
import kotlinx.coroutines.flow.SharedFlow

/**
 * The base Space's session list - `KernelSessions` over the same per-server [KernelSockets]
 * the gateway talks through, so a shutdown here and a cell run there meet on one lifecycle.
 *
 * Both verbs are *passive* for the reason [BaseSpaceKernelGateway.endingIfStillLive] is: asking
 * what is running, or ending a session, must never boot a runtime - a stopped server already
 * answered the question, and the answer is that everything it held is gone. That answer is told
 * to the registry rather than kept: [running]'s diff and a dead server's [KernelSessionRegistry.endAll]
 * are the two ways "this session no longer exists" reaches the notebooks still claiming it.
 */
class BaseSpaceKernelSessions(
  private val sharedSockets: PerRunningServer<KernelSockets>,
  private val registry: KernelSessionRegistry,
) : KernelSessions {
  override val ended: SharedFlow<String> = registry.ended

  /**
   * `GET api/sessions`, plus the reconciliation the reference reaches through `runningChanged`:
   * a session the list no longer holds went away without asking us, so end it here - and close
   * its socket, since a web socket beside a dead session only waits to 404 on its next frame.
   */
  override suspend fun running(): List<KernelSessionModel> {
    val sockets = sharedSockets.liveOrNull()
      ?: return emptyList<KernelSessionModel>().also { registry.endAll() }
    val models = sockets.lifecycle.sessions()
    registry.dropped(models.mapTo(mutableSetOf()) { it.serverSessionId })
      .forEach { sockets.close(it.kernelSession) }
    return models
  }

  /**
   * `DELETE api/sessions/<id>` against the live server - and `ended` either way, because a dead
   * server's sessions ended with it even though there is nothing left to DELETE them on.
   */
  override suspend fun shutdown(serverSessionId: String) {
    val sockets = sharedSockets.liveOrNull()
    sockets?.lifecycle?.shutdownSession(serverSessionId)
    registry.end(serverSessionId)?.let { sockets?.close(it.kernelSession) }
  }
}
