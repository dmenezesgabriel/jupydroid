package com.jupydroid.app.domain.model

import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable

/**
 * Where the kernel is paused, once a [com.jupydroid.app.domain.port.DebugEvent.Stopped] resolves
 * to a line via `stackTrace`'s own top frame - [NotebookDebugger][com.jupydroid.app.ui.editor.NotebookDebugger]'s
 * own state, mirroring [Breakpoint]'s split from `.ipynb`: a stop is live kernel state, not
 * something a notebook file has ever recorded.
 *
 * @property cellId the cell whose debug session stopped - the one [NotebookDebugger.startSession]
 *   armed, since this port never debugs more than one cell at a time.
 * @property line **one-based**, [com.jupydroid.app.domain.port.DebugStackFrame]'s own line.
 * @property threadId carried through so a later step/continue command (P6a-9) knows which thread
 *   to resume.
 */
data class DebugStop(val cellId: String, val line: Int, val threadId: Int)

/**
 * One [DebugScope][com.jupydroid.app.domain.port.DebugScope]'s variables, kept apart from every
 * other scope's - `Locals` and `Globals` can each hold a variable named the same thing, and
 * flattening them into one list would lose which is which. Mirrors JupyterLab's own
 * already-resolved `IScope { name, variables }` shape (`packages/debugger/src/tokens.ts:352-365`),
 * without that reference's interactive scope switcher: P6a's own phone-scoped surface renders
 * every group at once instead.
 */
data class DebugVariableGroup(val scope: String, val variables: List<DebugVariable>)

/**
 * What P6a-8's panel renders for a stop: every frame of the call stack, and the **top** frame's
 * variables only, grouped by scope.
 *
 * **Only the top frame's variables** - [NotebookDebugger.watchForStop] chains `scopes`/`variables`
 * for `frames.first()` alone, matching JupyterLab's own default selection on a fresh stop
 * (`packages/debugger/src/panels/callstack/model.ts:31-44`, which auto-selects `frames[0]`).
 * Tapping another frame to refocus [groups] onto it is not built - a documented gap in
 * `docs/PLAN.md`'s Open risks, not a silent omission.
 *
 * ```
 * val stack = DebugStack(frames = kernelDebugger.stackTrace(session, threadId), groups = ...)
 * ```
 */
data class DebugStack(val frames: List<DebugStackFrame>, val groups: List<DebugVariableGroup>)
