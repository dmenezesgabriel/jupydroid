package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.BreakpointRequest
import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.DebugScope
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.StaleKernelSessionException
import com.jupydroid.app.domain.port.SteppingCommand
import com.jupydroid.app.domain.port.VerifiedBreakpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The kernel debugger for the base Space's server, sharing [sharedSockets] with
 * [BaseSpaceKernelGateway] rather than each building its own - a `debug_request` reuses the
 * identical per-session socket `execute`/`complete`/`history` already share.
 *
 * The debugger has no `connect()` of its own to issue a session: [attach] is DAP's own entry
 * point, so it plays that role here, the same way `connect` does for the gateway. Every other
 * call requires the session to have attached under the *currently running* server -
 * [StaleKernelSessionException] otherwise, for the same reason [BaseSpaceKernelGateway] guards
 * `execute`: a session surviving a full server restart would otherwise open a socket against a
 * server that never heard of it.
 */
class BaseSpaceKernelDebugger(
  private val sharedSockets: PerRunningServer<KernelSockets>,
  private val debuggerFor: (KernelSockets) -> KernelDebugger,
) : KernelDebugger {
  private val attachedSessions = mutableSetOf<String>()
  private var trackedSockets: KernelSockets? = null

  override suspend fun attach(session: KernelSession) {
    resolveDebugger(null).attach(session)
    attachedSessions += session.id
  }

  override suspend fun configurationDone(session: KernelSession) = resolveDebugger(session).configurationDone(session)

  override suspend fun dumpCell(session: KernelSession, code: String): String =
    resolveDebugger(session).dumpCell(session, code)

  override suspend fun setBreakpoints(
    session: KernelSession,
    codeId: String,
    breakpoints: List<BreakpointRequest>,
  ): List<VerifiedBreakpoint> = resolveDebugger(session).setBreakpoints(session, codeId, breakpoints)

  override suspend fun stackTrace(session: KernelSession, threadId: Int): List<DebugStackFrame> =
    resolveDebugger(session).stackTrace(session, threadId)

  override suspend fun scopes(session: KernelSession, frameId: Int): List<DebugScope> =
    resolveDebugger(session).scopes(session, frameId)

  override suspend fun variables(session: KernelSession, variablesReference: Int): List<DebugVariable> =
    resolveDebugger(session).variables(session, variablesReference)

  override suspend fun step(session: KernelSession, threadId: Int, command: SteppingCommand) =
    resolveDebugger(session).step(session, threadId, command)

  override suspend fun evaluate(session: KernelSession, expression: String, frameId: Int): DebugVariable =
    resolveDebugger(session).evaluate(session, expression, frameId)

  /** `resolveDebugger` is suspend, so the check happens once collection actually starts, inside this builder. */
  override fun events(session: KernelSession): Flow<DebugEvent> = flow {
    emitAll(resolveDebugger(session).events(session))
  }

  /**
   * The running debugger for [sharedSockets]'s current [KernelSockets], resetting [attachedSessions]
   * the moment a different one shows up from either side. [session] is `null` only for [attach]
   * itself, the one call that has nothing yet to check attachment against; every other caller
   * passes its own session and gets [StaleKernelSessionException] if it is not [attachedSessions].
   */
  private suspend fun resolveDebugger(session: KernelSession?): KernelDebugger {
    val sockets = sharedSockets.get()
    if (sockets !== trackedSockets) {
      attachedSessions.clear()
      trackedSockets = sockets
    }
    if (session != null && session.id !in attachedSessions) throw StaleKernelSessionException(session.id)
    return debuggerFor(sockets)
  }
}
