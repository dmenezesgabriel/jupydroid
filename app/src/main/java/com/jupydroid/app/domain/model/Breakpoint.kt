package com.jupydroid.app.domain.model

/**
 * A breakpoint set on one line of a cell's source, kept in memory rather than in the notebook -
 * JupyterLab's own `DebuggerModel`, not `.ipynb`. A cell has no debug session until P6a-7a's
 * handshake runs, so [verified] is `false` and [codeId] is `null` for every breakpoint this app
 * can create today; both are filled in once `KernelDebugger.setBreakpoints` answers for real.
 *
 * @property line **one-based**, matching [KernelDebugger][com.jupydroid.app.domain.port.KernelDebugger]'s
 *   own `setBreakpoints`/[VerifiedBreakpoint][com.jupydroid.app.domain.port.VerifiedBreakpoint].
 * @property verified whether debugpy actually armed this line - always `false` until a real
 *   `setBreakpoints` round trip answers for it.
 * @property codeId the path `dumpCell` returned for the cell this line belongs to, or `null`
 *   before that round trip has ever run for this cell.
 */
data class Breakpoint(val line: Int, val verified: Boolean = false, val codeId: String? = null)
