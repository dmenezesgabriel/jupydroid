package com.jupydroid.app.data.space

/**
 * Decides when one execution is finished.
 *
 * An execution is done only once **both** the shell `execute_reply` and the iopub
 * `status: idle` have arrived, in either order. This is what JupyterLab's
 * `KernelFutureHandler` does
 * (jupyterlab@7f0e6906 packages/services/src/kernel/future.ts:14-17,
 * 241-243, 265-269), and the reason is that the two travel on separate channels with no
 * ordering guarantee between them: finishing on idle alone ends a cell while its output may
 * still be in flight, and finishing on the reply alone does the same.
 *
 * ```
 * if (completion.accept(frame, requestMsgId)) emit(KernelEvent.Idle)
 * ```
 */
internal class ExecutionCompletion {
  private var sawReply = false
  private var sawIdle = false

  /** Feeds one frame in and returns whether the execution is now complete. */
  fun accept(frame: String, requestMsgId: String): Boolean {
    sawReply = sawReply || KernelOutputs.isReplyFor(frame, requestMsgId)
    sawIdle = sawIdle || KernelOutputs.isIdleFor(frame, requestMsgId)
    return sawReply && sawIdle
  }
}
