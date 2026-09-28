package com.jupydroid.app.domain.port

import kotlinx.coroutines.flow.SharedFlow

/**
 * The server's session list, and this process's claim on it - `Session.IManager`'s `running()`
 * and `runningChanged` (`packages/services/src/session/manager.ts`) reduced to what P8f's panel
 * and P8f's "No Kernel" landing ask: what is running, what just ended.
 *
 * The model is [KernelSessionModel]'s, the server's own: a session row names its kernel only
 * when one is attached, and ends by `DELETE api/sessions/<id>`, never by the client-side session
 * id a `KernelSession` also carries.
 */
interface KernelSessions {
  /**
   * The serverSessionIds of sessions this process minted that are gone - shut down here, culled
   * on the server, or lost with a server that was replaced. `SessionContext` holds the
   * equivalent subscription for `session.disposed`, which is what lands an open document on
   * "No Kernel" the moment its kernel stops rather than on the next use of it.
   */
  val ended: SharedFlow<String>

  /**
   * Sessions whose kernel was swapped under them - `kernelChanged` to [ended]'s `disposed`
   * (`session/session.ts`). The emission carries the rebound [KernelSession] itself, so a holder
   * matching on `serverSessionId` trades the old kernel id for the new one.
   */
  val rebound: SharedFlow<KernelSession>

  /** Every session the server is holding now - `GET api/sessions`, reconciled against [ended]. */
  suspend fun running(): List<KernelSessionModel>

  /** `DELETE api/sessions/<serverSessionId>` - ends the session and the kernel behind it. */
  suspend fun shutdown(serverSessionId: String)
}
