package com.jupydroid.app.data.space

import com.jupydroid.app.data.space.JupyterFrame.content
import com.jupydroid.app.data.space.JupyterFrame.msgType
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
      message.content()?.let { outputFrom(message.msgType(), it) }
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
  fun isIdleFor(frame: String, requestMsgId: String): Boolean =
    JupyterFrame.ofType(frame, requestMsgId, "status")
      ?.content()?.get("execution_state")?.jsonPrimitive?.content == "idle"

  /**
   * Whether this frame is the shell `execute_reply` to our request - the other completion
   * signal.
   *
   * An execution is done only once **both** this and [isIdleFor] have arrived, in either
   * order, which is what JupyterLab's own `KernelFutureHandler` does
   * (reference/research_repos/jupyterlab/packages/services/src/kernel/future.ts:14-17,
   * 241-243, 265-269). They travel on separate channels - shell and iopub - with no
   * ordering guarantee between them, so waiting on idle alone ends the cell while output
   * may still be in flight, and waiting on the reply alone does the same.
   */
  fun isReplyFor(frame: String, requestMsgId: String): Boolean =
    JupyterFrame.ofType(frame, requestMsgId, "execute_reply") != null

  private fun outputFrom(msgType: String?, content: JsonObject): CellOutput? = when (msgType) {
    "stream" -> content.toStream()
    "execute_result" -> content.toExecuteResult()
    "display_data", "update_display_data" -> CellOutput.DisplayData(content.mimeBundle())
    "error" -> content.toError()
    "clear_output" -> CellOutput.ClearOutput
    else -> null
  }

  private fun JsonObject.toStream(): CellOutput? {
    // Deliberately not defaulting to stdout: mislabelling stderr would render a warning as
    // ordinary output, and the protocol defines only these two names.
    val name = when (get("name")?.jsonPrimitive?.content) {
      "stdout" -> StreamName.STDOUT
      "stderr" -> StreamName.STDERR
      else -> return null
    }
    return CellOutput.Stream(name, get("text")?.jsonPrimitive?.content.orEmpty())
  }

  private fun JsonObject.toExecuteResult(): CellOutput? =
    executionCount()?.let { CellOutput.ExecuteResult(mimeBundle(), it) }

  private fun JsonObject.toError(): CellOutput? {
    val name = get("ename")?.jsonPrimitive?.content ?: return null
    return CellOutput.Error(
      name = name,
      value = get("evalue")?.jsonPrimitive?.content.orEmpty(),
      traceback = get("traceback")?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
    )
  }

  /**
   * mime -> payload. Text mimes are strings; `application/json` and friends are objects,
   * so they keep their JSON rather than being dropped for not being a primitive.
   */
  private fun JsonObject.mimeBundle(): Map<String, String> =
    get("data")?.jsonObject.orEmpty().mapValues { (_, payload) ->
      (payload as? JsonPrimitive)?.content ?: payload.toString()
    }

  private fun JsonObject.executionCount(): Int? = get("execution_count")?.jsonPrimitive?.intOrNull
}
