package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.BreakpointRequest
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
 * finalised at nine methods with no separate `initialize`, since nothing else on this
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
  private val dumpCache = CellDumpCache()

  /**
   * debugpy keeps both `initialize` and `attach` state for the life of the **kernel process**, not
   * this app's own session object - repeating either after an earlier one already succeeded (a
   * later cell run, or a notebook reopened onto the same still-live kernel) is rejected with
   * `"Invalid message: Session is already initialized"` / `"… already started"`, not a real
   * failure: each call's own postcondition already holds. Reproduced live on the tablet:
   * disarming a breakpoint and re-running the cell it was on threw the first, uncaught, and a
   * third run threw the second - since
   * [NotebookDebugger][com.jupydroid.app.ui.editor.NotebookDebugger] still has to attach on every
   * run to send the clearing `setBreakpoints` a stale kernel-side breakpoint needs. Any other
   * failure still throws - only these two specific, expected messages are tolerated.
   */
  override suspend fun attach(session: KernelSession) {
    tolerating("already initialized") { sendDebugRequest(session, "initialize", initializeArgs(session)) }
    tolerating("already started") { sendDebugRequest(session, "attach", buildJsonObject { }) }
  }

  override suspend fun configurationDone(session: KernelSession) {
    sendDebugRequest(session, "configurationDone", buildJsonObject { })
  }

  /**
   * Sends the real DAP `dumpCell` request once per distinct [code] per session - [CellDumpCache]
   * predicts the same `codeId` a `debugInfo` reply's own params would name, so a repeat needs no
   * round trip.
   */
  override suspend fun dumpCell(session: KernelSession, code: String): String = dumpCache.sourcePathFor(
    session,
    code,
    debugInfo = { sendDebugRequest(session, "debugInfo", buildJsonObject { }) },
    dumpCell = { sendDebugRequest(session, "dumpCell", buildJsonObject { put("code", code) }) },
  )

  override suspend fun setBreakpoints(
    session: KernelSession,
    codeId: String,
    breakpoints: List<BreakpointRequest>,
  ): List<VerifiedBreakpoint> {
    val reply = sendDebugRequest(session, "setBreakpoints", setBreakpointsArgs(codeId, breakpoints))
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

  override suspend fun evaluate(session: KernelSession, expression: String, frameId: Int): DebugVariable {
    val reply = sendDebugRequest(session, "evaluate", evaluateArgs(expression, frameId))
    return KernelDebugReplies.evaluated(reply, expression)
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
    val seq = nextSeq(seqLock, seqBySession, session)
    return sockets.socketFor(session).askOnce(
      request = { msgId -> JupyterMessage.debugRequest(command, args, seq, session.id, msgId) },
      readReply = KernelDebugReplies::of,
    )
  }
}

/**
 * JupyterLab's own `DebuggerSession._seq` (`session.ts:302`) - a plain per-session counter, ours to
 * keep. Top-level rather than a member of [RealKernelDebugger] - [evaluate][RealKernelDebugger.evaluate]
 * pushed that class past detekt's own eleven-per-class cap, the same reason [tolerating] already
 * lives at file scope instead of as a private method.
 */
private suspend fun nextSeq(seqLock: Mutex, seqBySession: MutableMap<String, Int>, session: KernelSession): Int =
  seqLock.withLock {
    val seq = seqBySession.getOrDefault(session.id, 0)
    seqBySession[session.id] = seq + 1
    seq
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

/**
 * `condition` sent only when [BreakpointRequest.condition] is present and not blank - an empty
 * string is the same as no condition to a caller clearing a text field, and DAP's own
 * `SetBreakpointsArguments` treats the key's absence as "unconditional," not `condition: ""`.
 */
private fun setBreakpointsArgs(codeId: String, breakpoints: List<BreakpointRequest>) = buildJsonObject {
  put("source", buildJsonObject { put("path", codeId) })
  put(
    "breakpoints",
    buildJsonArray {
      breakpoints.forEach { requested ->
        add(
          buildJsonObject {
            put("line", requested.line)
            requested.condition?.takeIf { it.isNotBlank() }?.let { put("condition", it) }
          },
        )
      }
    },
  )
  put("sourceModified", false)
}

/**
 * `context: "repl"` is debugpy's own convention for an ad hoc expression a person typed, distinct
 * from `"watch"`/`"hover"` - confirmed against `vscode-jupyter`'s own `DebuggerVariables.evaluate`
 * rather than guessed.
 */
private fun evaluateArgs(expression: String, frameId: Int) = buildJsonObject {
  put("expression", expression)
  put("frameId", frameId)
  put("context", "repl")
}

private fun SteppingCommand.dapCommand(): String = when (this) {
  SteppingCommand.NEXT -> "next"
  SteppingCommand.STEP_IN -> "stepIn"
  SteppingCommand.STEP_OUT -> "stepOut"
  SteppingCommand.CONTINUE -> "continue"
}

/**
 * Runs [block], swallowing a [DebugRequestFailedException] whose own message names
 * [alreadyDonePhrase] - debugpy's own way of saying the step [block] just repeated already
 * happened for this kernel process, which is not a failure from the caller's side: the
 * postcondition it asked for already holds. Any other failure still throws.
 */
private suspend inline fun tolerating(alreadyDonePhrase: String, block: () -> Unit) {
  try {
    block()
  } catch (e: DebugRequestFailedException) {
    if (e.message?.contains(alreadyDonePhrase, ignoreCase = true) != true) throw e
  }
}

/**
 * What [RealKernelDebugger.dumpCell] would otherwise ask the kernel for on every call: a
 * session's own `debugInfo` hash/tmp-file parameters, fetched once, and the real `sourcePath` a
 * `dumpCell` reply named for a `codeId` [CellCodeId] already predicted - so a cell whose text has
 * not changed needs neither request sent twice.
 *
 * Unsynchronized on purpose: a race on the very first `dumpCell`/`debugInfo` for a session costs
 * one extra, harmless round trip (the kernel's own `dumpCell` is idempotent for identical code),
 * not a wrong answer - not worth a second `Mutex` beside [RealKernelDebugger]'s own `seqLock`,
 * which guards something a race on *would* corrupt.
 */
private class CellDumpCache {
  private val hashParamsBySession = mutableMapOf<String, DebugInfoHashParams>()
  private val dumpedPathsBySession = mutableMapOf<String, MutableMap<String, String>>()

  suspend fun sourcePathFor(
    session: KernelSession,
    code: String,
    debugInfo: suspend () -> JsonObject,
    dumpCell: suspend () -> JsonObject,
  ): String {
    val params = hashParamsBySession.getOrPut(session.id) { debugInfoHashParams(debugInfo()) }
    val codeId = CellCodeId.codeId(code, params.hashSeed, params.tmpFilePrefix, params.tmpFileSuffix)
    return dumpedPathsBySession.getOrPut(session.id) { mutableMapOf() }
      .getOrPut(codeId) { KernelDebugReplies.sourcePath(dumpCell()) }
  }
}
