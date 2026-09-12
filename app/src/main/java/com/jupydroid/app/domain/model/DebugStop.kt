package com.jupydroid.app.domain.model

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
