package com.jupydroid.app.domain.port

import kotlinx.coroutines.flow.Flow

/** One requested breakpoint's answer: the line debugpy actually armed, and whether it took. */
data class VerifiedBreakpoint(val line: Int, val verified: Boolean)

/**
 * One line to arm, DAP's own `SetBreakpointsArguments.breakpoints` element shape -
 * [condition] is a Python expression gating whether this line actually stops the kernel once
 * armed, `null` for an ordinary breakpoint.
 */
data class BreakpointRequest(val line: Int, val condition: String? = null)

/** One frame of a stopped call stack, `stackTrace`'s own shape (`id` is what `scopes` addresses). */
data class DebugStackFrame(val id: Int, val name: String, val line: Int, val column: Int)

/** One variable scope, `scopes`' own shape (`variablesReference` is what `variables` addresses). */
data class DebugScope(val name: String, val variablesReference: Int, val expensive: Boolean)

/** One variable, `variables`' own shape - `variablesReference` is nonzero when it can itself be expanded. */
data class DebugVariable(val name: String, val value: String, val type: String?, val variablesReference: Int)

/**
 * The four DAP execution-control requests, one enum rather than four methods.
 *
 * `continue` is a Kotlin keyword and detekt's `FunctionNaming` rejects the backticked escape, so a
 * fourth `KernelDebugger` method named for it was never spellable here the way `next`/`stepIn`/
 * `stepOut` are - and four near-identical methods differing only in which DAP command string they
 * send is exactly what [KernelDebugger.step] replaces with one, which is also what kept the
 * interface's own function count at detekt's eleven-per-interface cap.
 */
enum class SteppingCommand { NEXT, STEP_IN, STEP_OUT, CONTINUE }

/** Events streamed from [KernelDebugger.events]; `debug_event` on iopub, not a reply to anything. */
sealed interface DebugEvent {
  data class Stopped(val threadId: Int) : DebugEvent

  data object Continued : DebugEvent
}

/**
 * DAP over the kernel's control channel (`debug_request`/`debug_reply`), the transport
 * `JupyterMessage.debugRequest` already frames. Phone scope: breakpoints in a cell, step/continue,
 * variables, call stack - not the full DAP surface.
 */
interface KernelDebugger {
  suspend fun attach(session: KernelSession)

  suspend fun configurationDone(session: KernelSession)

  /**
   * Writes [code] to a real file debugpy can set breakpoints against and returns its path.
   *
   * ipykernel names that file from a hash of [code] (`ipykernel/compiler.py`'s `get_file_name`),
   * so the same cell text must always come back with the same [sourcePath][String] - a caller
   * that dumps a cell twice must not have to treat the two paths as different sources.
   */
  suspend fun dumpCell(session: KernelSession, code: String): String

  /** [codeId] is the path [dumpCell] returned for the cell these [breakpoints] belong to. */
  suspend fun setBreakpoints(
    session: KernelSession,
    codeId: String,
    breakpoints: List<BreakpointRequest>,
  ): List<VerifiedBreakpoint>

  suspend fun stackTrace(session: KernelSession, threadId: Int): List<DebugStackFrame>

  suspend fun scopes(session: KernelSession, frameId: Int): List<DebugScope>

  suspend fun variables(session: KernelSession, variablesReference: Int): List<DebugVariable>

  suspend fun step(session: KernelSession, threadId: Int, command: SteppingCommand)

  /**
   * Runs [expression] against [frameId]'s own scope while stopped there, DAP's own
   * `evaluate` request with `context: "repl"` - debugpy's own convention for an ad hoc expression
   * a person typed, distinct from `"watch"`/`"hover"` (confirmed against `vscode-jupyter`'s own
   * `DebuggerVariables.evaluate`, `context: 'repl'`, rather than guessed). The answer is shaped
   * like [DebugVariable] because it already is one DAP-side - [DebugVariable.name] holds
   * [expression] itself, since an ad hoc result has no variable name of its own to report.
   */
  suspend fun evaluate(session: KernelSession, expression: String, frameId: Int): DebugVariable

  /** `debug_event` has no `parent_header` to correlate with a request - its own subscription, not a reply. */
  fun events(session: KernelSession): Flow<DebugEvent>
}
