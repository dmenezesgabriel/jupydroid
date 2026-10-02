package com.jupydroid.app.domain.model

import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.SessionType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A session this process minted, the document it was minted for, and the kind it declared -
 * [type] is the `type` field of its `POST api/sessions` ([SessionType]), which a `GET
 * api/sessions` row reports back verbatim, so it is what a `running()` answer built from
 * tracked sessions has to carry.
 */
data class TrackedKernelSession(val kernelSession: KernelSession, val documentPath: String, val type: SessionType)

/**
 * The kernel sessions this process minted, keyed by the server's own session id.
 *
 * The panel's `GET api/sessions` answers in server ids and the gateway's `KernelSession` carries
 * both, so this is the one place the two halves meet: the gateway registers at connect, and a
 * session that ends - the panel's shutdown, the server forgetting it, the server being replaced -
 * is announced on [ended] exactly once so every holder of it lands on No Kernel the way
 * `session.disposed` lands JupyterLab's own `SessionContext` on `noKernelName`.
 *
 * ```
 * val registry = KernelSessionRegistry()
 * registry.register(session, "Analysis.ipynb")          // at connect
 * registry.end("ss1")                                    // -> ended emits "ss1"
 * ```
 */
class KernelSessionRegistry {
  private val tracked = LinkedHashMap<String, TrackedKernelSession>()
  private val _ended = MutableSharedFlow<String>(extraBufferCapacity = ENDED_BUFFER)
  private val _rebound = MutableSharedFlow<KernelSession>(extraBufferCapacity = ENDED_BUFFER)

  /** The serverSessionIds of sessions that ended - one emission per session, ever. */
  val ended: SharedFlow<String> = _ended.asSharedFlow()

  /**
   * Sessions whose kernel was swapped under them - `kernelChanged` to [ended]'s `disposed`
   * (`session/session.ts`). A `PATCH api/sessions` keeps the session id, so holders keep theirs
   * too and only the kernel changes hands.
   */
  val rebound: SharedFlow<KernelSession> = _rebound.asSharedFlow()

  /** Every serverSessionId still tracked - the set `running()` reconciles its answer against. */
  val liveIds: Set<String> get() = tracked.keys.toSet()

  /** Everything tracked, insertion-ordered the way the server lists its own sessions. */
  val all: List<TrackedKernelSession> get() = tracked.values.toList()

  fun trackedBy(serverSessionId: String): TrackedKernelSession? = tracked[serverSessionId]

  /**
   * Minted a session: remember it so its end can be told apart from sessions this process never
   * held. Silent - an arrival announces itself on the session object the caller already has.
   *
   * [type] is what the session declared on its POST - null answers "the document rule" the way
   * [KernelGateway.connect]'s own parameter does.
   */
  fun register(session: KernelSession, documentPath: String, type: SessionType? = null) {
    tracked[session.serverSessionId] =
      TrackedKernelSession(session, documentPath, type ?: SessionType.forPath(documentPath))
  }

  /**
   * The session is gone from the server - say so once to whoever is still holding it, and hand
   * the tracking entry back so its socket can be closed with it.
   */
  suspend fun end(serverSessionId: String): TrackedKernelSession? =
    tracked.remove(serverSessionId)?.also { _ended.emit(serverSessionId) }

  /**
   * The session's kernel changed - a `PATCH api/sessions`, which keeps the session's own id.
   * The tracked entry is replaced rather than ended, and the rebound is announced so a second
   * holder of the same session (a console sharing it) follows the new kernel rather than
   * running against one the server has since shut down.
   */
  suspend fun rebind(session: KernelSession) {
    val entry = tracked[session.serverSessionId]
    if (entry?.kernelSession == session) return // re-announcing an unchanged binding is noise
    if (entry != null) tracked[session.serverSessionId] = entry.copy(kernelSession = session)
    _rebound.emit(session)
  }

  /** The server itself went: everything it issued is dead, so say so for all of it. */
  suspend fun endAll() {
    val gone = tracked.keys.toList()
    tracked.clear()
    gone.forEach { _ended.emit(it) }
  }

  /**
   * Ends the tracked sessions [liveIds] does not contain and returns them - the `running()` diff,
   * where "the server forgot this one" is learned from a list rather than a refusal.
   */
  suspend fun dropped(liveIds: Set<String>): List<TrackedKernelSession> =
    (tracked.keys - liveIds).mapNotNull { end(it) }

  private companion object {
    /** A burst of ends must not suspend the lister - endings are events, not backpressure. */
    const val ENDED_BUFFER = 64
  }
}
