package com.jupydroid.app.ui.editor

/**
 * What the notebook's kernel is doing, as the bar reports it - see [KernelIndicator].
 *
 * **JupyterLab's `KernelDisplayStatus`, minus the states this app cannot reach.** The reference
 * names twelve (`packages/apputils/src/kernelstatuses.tsx:27-41`) and folds connection and kernel
 * state into one value the same way (`sessioncontext.tsx:620-660`); the five here are the ones
 * something in this codebase actually produces, which is the rule [ExecutionStatus]'s own doc
 * comment states. `dead`, `autorestarting` and `terminating` need a kernel-wide status stream, and
 * this app subscribes to iopub per execution rather than per kernel.
 */
enum class KernelStatus {
  /**
   * No session has been opened - the notebook has never run a cell, or its kernel was shut down.
   *
   * **A departure from the reference's `'unknown'`**, which is what `kernelDisplayStatus` returns
   * with no kernel: JupyterLab starts a session with the document, so having none there means
   * something went wrong. Here it is the resting state of every notebook being read, by design -
   * typing must not boot a Python runtime (see [NotebookKernel.completionsAt]). The reference's own
   * word for this condition is on the *name* rather than the status, `noKernelName` =
   * **"No Kernel"** (`packages/apputils/src/sessioncontext.tsx:675-677`), and that is the word used.
   */
  NO_KERNEL,

  /**
   * A run is in flight and the socket is not open yet.
   *
   * On a cold app this is the Space installing, `jupyter_server` starting and the kernel starting -
   * **fourteen seconds** on the tablet (`evidence/p1c-app-death-backstop/`), during which nothing
   * on screen used to say a kernel was coming up.
   */
  STARTING,

  /** The kernel has this notebook's work in hand, or waiting behind it. */
  BUSY,

  IDLE,

  /** `notebook:restart-kernel` in flight: the process is being replaced. */
  RESTARTING,
}

/**
 * The one rule that decides which of the five a notebook is in.
 *
 * Derived rather than stored, so it cannot disagree with the run it describes - the same call
 * `runningCellId` makes over [EditorUiState.executions]. Two booleans are all the state it needs
 * that the executions do not already carry: whether a socket is open, and whether a restart is in
 * flight.
 *
 * ```
 * when (state.kernelStatus) { KernelStatus.BUSY -> … }
 * ```
 */
val EditorUiState.kernelStatus: KernelStatus
  get() = when {
    // First, and deliberately: `restart` clears the abandoned run only once the gateway has
    // answered, so a rule that read the executions first would call a restarting kernel busy.
    kernelRestarting -> KernelStatus.RESTARTING
    kernelConnected -> if (kernelHasWork) KernelStatus.BUSY else KernelStatus.IDLE
    kernelHasWork -> KernelStatus.STARTING
    else -> KernelStatus.NO_KERNEL
  }

/**
 * Whether anything is running or waiting to.
 *
 * **Queued counts**, which is `ExecutionIndicator`'s own reading: it reports busy off
 * `scheduledCell.size` rather than off whichever cell the kernel has in hand
 * (`packages/notebook/src/executionindicator.tsx:153`). A Run All whose first cell has just
 * finished is not idle for the gap before the second starts.
 */
private val EditorUiState.kernelHasWork: Boolean
  get() = executions.values.any {
    it.status == ExecutionStatus.RUNNING || it.status == ExecutionStatus.QUEUED
  }
