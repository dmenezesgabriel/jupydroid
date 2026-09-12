package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.DebugScope
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.SteppingCommand
import com.jupydroid.app.domain.port.VerifiedBreakpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A `debug_reply` reporting `success: false` - the DAP-level failure the reply itself names. */
class DebugRequestFailedException(command: String, message: String?) :
  IllegalStateException("debug_request \"$command\" failed: ${message ?: "no message"}")

/**
 * The real [KernelDebugger]: DAP over the control channel, sharing [KernelSockets]' one-shot
 * [SessionSocket.askOnce] every other control/shell request already uses.
 *
 * [attach] sends **two** round trips, `initialize` then `attach` - `KernelDebugger`'s own port was
 * finalised at nine methods (P6a-3a) with no separate `initialize`, since nothing else on this
 * phone-scoped surface needs its capabilities response; this mirrors JupyterLab's own
 * `DebuggerSession.start()` (`packages/debugger/src/session.ts:180-204`) folding the same two
 * calls behind one entry point.
 *
 * Every `debug_reply` is success-checked uniformly by [KernelDebugReplies], throwing
 * [DebugRequestFailedException] on `success: false` - a departure from JupyterLab's own
 * inconsistent per-call checking (`service.ts` throws only for `initialize`), because silently
 * proceeding past a rejected `setBreakpoints` is a worse failure mode than a thrown exception.
 */
class RealKernelDebugger(private val sockets: KernelSockets) : KernelDebugger {
  private val seqLock = Mutex()
  private val seqBySession = mutableMapOf<String, Int>()

  override suspend fun attach(session: KernelSession) {
    sendDebugRequest(session, "initialize", initializeArgs(session))
    sendDebugRequest(session, "attach", buildJsonObject { })
  }

  override suspend fun configurationDone(session: KernelSession) {
    sendDebugRequest(session, "configurationDone", buildJsonObject { })
  }

  override suspend fun dumpCell(session: KernelSession, code: String): String =
    KernelDebugReplies.sourcePath(sendDebugRequest(session, "dumpCell", buildJsonObject { put("code", code) }))

  override suspend fun setBreakpoints(
    session: KernelSession,
    codeId: String,
    lines: List<Int>,
  ): List<VerifiedBreakpoint> {
    val reply = sendDebugRequest(session, "setBreakpoints", setBreakpointsArgs(codeId, lines))
    return KernelDebugReplies.verifiedBreakpoints(reply)
  }

  override suspend fun stackTrace(session: KernelSession, threadId: Int): List<DebugStackFrame> {
    val reply = sendDebugRequest(session, "stackTrace", buildJsonObject { put("threadId", threadId) })
    return KernelDebugReplies.stackFrames(reply)
  }

  override suspend fun scopes(session: KernelSession, frameId: Int): List<DebugScope> =
    KernelDebugReplies.scopes(sendDebugRequest(session, "scopes", buildJsonObject { put("frameId", frameId) }))

  override suspend fun variables(session: KernelSession, variablesReference: Int): List<DebugVariable> =
    KernelDebugReplies.variables(
      sendDebugRequest(session, "variables", buildJsonObject { put("variablesReference", variablesReference) }),
    )

  override suspend fun step(session: KernelSession, threadId: Int, command: SteppingCommand) {
    sendDebugRequest(session, command.dapCommand(), buildJsonObject { put("threadId", threadId) })
  }

  /** `debug_event` has no `parent_header` - its own subscription onto the shared socket, not a reply. */
  override fun events(session: KernelSession): Flow<DebugEvent> = flow {
    emitAll(
      sockets.socketFor(session).events.mapNotNull { event ->
        (event as? JupyterSocketEvent.Received)?.let { KernelDebugEvents.eventOf(it.frame) }
      },
    )
  }

  private suspend fun sendDebugRequest(session: KernelSession, command: String, args: JsonObject): JsonObject {
    val seq = nextSeq(session)
    return sockets.socketFor(session).askOnce(
      request = { msgId -> JupyterMessage.debugRequest(command, args, seq, session.id, msgId) },
      readReply = KernelDebugReplies::of,
    )
  }

  /** JupyterLab's own `DebuggerSession._seq` (`session.ts:302`) - a plain per-session counter, ours to keep. */
  private suspend fun nextSeq(session: KernelSession): Int = seqLock.withLock {
    val seq = seqBySession.getOrDefault(session.id, 0)
    seqBySession[session.id] = seq + 1
    seq
  }
}

/**
 * DAP's `initialize` arguments, mirroring JupyterLab's own (`session.ts:181-192`) except two
 * capabilities stated honestly rather than copied: this port has no variable-paging method and no
 * `runInTerminal` reverse-request handler, so both are `false` rather than a claim nothing backs.
 */
private fun initializeArgs(session: KernelSession) = buildJsonObject {
  put("clientID", "jupydroid")
  put("clientName", "JupyterDroid")
  put("adapterID", session.kernelId)
  put("pathFormat", "path")
  put("linesStartAt1", true)
  put("columnsStartAt1", true)
  put("supportsVariableType", true)
  put("supportsVariablePaging", false)
  put("supportsRunInTerminalRequest", false)
  put("locale", "en")
}

private fun setBreakpointsArgs(codeId: String, lines: List<Int>) = buildJsonObject {
  put("source", buildJsonObject { put("path", codeId) })
  put("breakpoints", buildJsonArray { lines.forEach { line -> add(buildJsonObject { put("line", line) }) } })
  put("sourceModified", false)
}

private fun SteppingCommand.dapCommand(): String = when (this) {
  SteppingCommand.NEXT -> "next"
  SteppingCommand.STEP_IN -> "stepIn"
  SteppingCommand.STEP_OUT -> "stepOut"
  SteppingCommand.CONTINUE -> "continue"
}
