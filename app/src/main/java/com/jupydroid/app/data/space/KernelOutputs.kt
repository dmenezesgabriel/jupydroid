package com.jupydroid.app.data.space

import com.jupydroid.app.data.space.JupyterFrame.content
import com.jupydroid.app.data.space.JupyterFrame.msgType
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.KernelInputRequest
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads a kernel's iopub frames into the domain's [CellOutput] types.
 *
 * Everything is per-request and total: a frame carrying no output for us - which is most
 * of them - returns null rather than throwing or guessing.
 *
 * ```
 * KernelOutputs.outputOf(frame, requestMsgId)?.let { emit(KernelEvent.Output(cellId, it)) }
 * ```
 */
object KernelOutputs {
  /** The output this frame carries for our request, or null when it carries none. */
  fun outputOf(frame: String, requestMsgId: String): CellOutput? =
    JupyterFrame.ours(frame, requestMsgId)?.let { message ->
      message.content()?.toCellOutput(message.msgType())
    }

  /**
   * The cell's execution count, which arrives on `execute_input` before any output. The
   * count on `execute_result` is the same number, but only appears when the cell returned
   * a value, so it cannot be relied on alone.
   */
  fun executionCountOf(frame: String, requestMsgId: String): Int? =
    JupyterFrame.ofType(frame, requestMsgId, "execute_input")?.content()?.executionCount()

  /**
   * Whether this frame is the kernel going idle for *our* request. One of the two signals
   * an execution is complete - see [isReplyFor] for why one is not enough. Matching another
   * request's idle would truncate our output; matching none would run the cell forever.
   */
  fun isIdleFor(frame: String, requestMsgId: String): Boolean = JupyterFrame.ofType(frame, requestMsgId, "status")
    ?.content()?.get("execution_state")?.jsonPrimitive?.content == "idle"

  /**
   * Whether this frame is the shell `execute_reply` to our request - the other completion
   * signal.
   *
   * An execution is done only once **both** this and [isIdleFor] have arrived, in either
   * order, which is what JupyterLab's own `KernelFutureHandler` does
   * (jupyterlab@7f0e6906 packages/services/src/kernel/future.ts:14-17,
   * 241-243, 265-269). They travel on separate channels - shell and iopub - with no
   * ordering guarantee between them, so waiting on idle alone ends the cell while output
   * may still be in flight, and waiting on the reply alone does the same.
   */
  fun isReplyFor(frame: String, requestMsgId: String): Boolean =
    JupyterFrame.ofType(frame, requestMsgId, "execute_reply") != null

  /**
   * The prompt this frame asks for on our request's behalf, or null when it asks none. The header
   * is kept whole because the reply carries it back ([JupyterMessage.inputReply]).
   */
  fun inputRequestOf(frame: String, requestMsgId: String): KernelInputRequest? =
    JupyterFrame.ofType(frame, requestMsgId, "input_request")?.let { message ->
      val content = message.content() ?: return null
      KernelInputRequest(
        prompt = content["prompt"]?.jsonPrimitive?.content.orEmpty(),
        password = content["password"]?.jsonPrimitive?.booleanOrNull == true,
        requestHeader = message["header"].toString(),
      )
    }
}
