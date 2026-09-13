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
    currentDebugger().attach(session)
    attachedSessions += session.id
  }

  override suspend fun configurationDone(session: KernelSession) = known(session).configurationDone(session)

  override suspend fun dumpCell(session: KernelSession, code: String): String = known(session).dumpCell(session, code)

  override suspend fun setBreakpoints(
    session: KernelSession,
    codeId: String,
    breakpoints: List<BreakpointRequest>,
  ): List<VerifiedBreakpoint> = known(session).setBreakpoints(session, codeId, breakpoints)

  override suspend fun stackTrace(session: KernelSession, threadId: Int): List<DebugStackFrame> =
    known(session).stackTrace(session, threadId)

  override suspend fun scopes(session: KernelSession, frameId: Int): List<DebugScope> =
    known(session).scopes(session, frameId)

  override suspend fun variables(session: KernelSession, variablesReference: Int): List<DebugVariable> =
    known(session).variables(session, variablesReference)

  override suspend fun step(session: KernelSession, threadId: Int, command: SteppingCommand) =
    known(session).step(session, threadId, command)

  /** `known` is suspend, so the check happens once collection actually starts, inside this builder. */
  override fun events(session: KernelSession): Flow<DebugEvent> = flow {
    emitAll(known(session).events(session))
  }

  /** The running debugger, for a session it actually knows; [StaleKernelSessionException] if not. */
  private suspend fun known(session: KernelSession): KernelDebugger {
    val debugger = currentDebugger()
    if (session.id !in attachedSessions) throw StaleKernelSessionException(session.id)
    return debugger
  }

  /** [attachedSessions] is cleared the moment a different [KernelSockets] shows up, from either side. */
  private suspend fun currentDebugger(): KernelDebugger {
    val sockets = sharedSockets.get()
    if (sockets !== trackedSockets) {
      attachedSessions.clear()
      trackedSockets = sockets
    }
    return debuggerFor(sockets)
  }
}
