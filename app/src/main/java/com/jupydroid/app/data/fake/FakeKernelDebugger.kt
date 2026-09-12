package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.DebugScope
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.SteppingCommand
import com.jupydroid.app.domain.port.VerifiedBreakpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Stub [KernelDebugger]; what `AppContainer` wires until P6a-4 shares `RealKernelDebugger` there. */
class FakeKernelDebugger : KernelDebugger {
  /** Which sessions attached, so a test can tell "the command ran" from "it did nothing". */
  val attachedSessions = mutableListOf<KernelSession>()

  override suspend fun attach(session: KernelSession) {
    attachedSessions += session
  }

  override suspend fun configurationDone(session: KernelSession) = Unit

  private val dumpedPaths = mutableMapOf<String, String>()

  /**
   * A path per distinct [code], the same property ipykernel's own hash-named file has - but
   * content-keyed rather than hashed, since matching the real Murmur2 name is P6a-5's job, not
   * this fake's.
   */
  override suspend fun dumpCell(session: KernelSession, code: String): String =
    dumpedPaths.getOrPut(code) { "/fake/debug-cells/cell-${dumpedPaths.size}.py" }

  /** Requested lines per [codeId], so a test can assert what was armed without a real debugpy. */
  val breakpointsByCodeId = mutableMapOf<String, List<Int>>()

  /** Always verified: this fake never runs the line to find out whether debugpy could arm it. */
  override suspend fun setBreakpoints(
    session: KernelSession,
    codeId: String,
    lines: List<Int>,
  ): List<VerifiedBreakpoint> {
    breakpointsByCodeId[codeId] = lines
    return lines.map { VerifiedBreakpoint(line = it, verified = true) }
  }

  /** Scripted per test; the default is the single-frame stack every P6a-6/7b test already asserts against. */
  var stackFramesToReturn: List<DebugStackFrame> =
    listOf(DebugStackFrame(id = 1, name = "<module>", line = 1, column = 1))

  /** Scripted per test; the default pairs with [stackFramesToReturn]'s default frame id. */
  var scopesToReturn: List<DebugScope> =
    listOf(DebugScope(name = "Locals", variablesReference = 101, expensive = false))

  /** Scripted per test, keyed by the [DebugScope.variablesReference] a scope named. */
  var variablesByReference: Map<Int, List<DebugVariable>> =
    mapOf(101 to listOf(DebugVariable(name = "x", value = "1", type = "int", variablesReference = 0)))

  override suspend fun stackTrace(session: KernelSession, threadId: Int): List<DebugStackFrame> = stackFramesToReturn

  override suspend fun scopes(session: KernelSession, frameId: Int): List<DebugScope> = scopesToReturn

  override suspend fun variables(session: KernelSession, variablesReference: Int): List<DebugVariable> =
    variablesByReference[variablesReference].orEmpty()

  /** Every stepping request this session made, in order, so a test can tell which command fired. */
  val stepRequests = mutableListOf<Pair<Int, SteppingCommand>>()

  override suspend fun step(session: KernelSession, threadId: Int, command: SteppingCommand) {
    stepRequests += threadId to command
  }

  private val eventsBySession = mutableMapOf<KernelSession, MutableSharedFlow<DebugEvent>>()

  private fun eventsFor(session: KernelSession) = eventsBySession.getOrPut(session) {
    MutableSharedFlow(extraBufferCapacity = EVENT_BUFFER)
  }

  override fun events(session: KernelSession): Flow<DebugEvent> = eventsFor(session)

  /** Test-only: there is no kernel here to hit a breakpoint or resume on its own. */
  suspend fun emit(session: KernelSession, event: DebugEvent) {
    eventsFor(session).emit(event)
  }

  private companion object {
    const val EVENT_BUFFER = 8
  }
}
