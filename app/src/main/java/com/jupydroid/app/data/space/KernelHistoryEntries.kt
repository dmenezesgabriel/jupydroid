package com.jupydroid.app.data.space

import com.jupydroid.app.data.space.JupyterFrame.content
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * Reads a `history_reply` into the inputs it carries, oldest first.
 *
 * The reply's shape is the protocol's, verified against JupyterLab's own message types rather than
 * assumed: `history` is a list of `[session, line, input]` triples
 * (`jupyterlab@7f0e6906 packages/services/src/kernel/messages.ts:1033-1037`). Only
 * the third element is read, which is what the reference reads too
 * (`packages/notebook/src/history.ts:361-365`) - the session number it also records is never
 * consulted by anything, and the line number is IPython's own bookkeeping.
 *
 * Total, like every other reader here: a frame belonging to somebody else, of the wrong type, or
 * unparseable is null rather than an exception, because a shared kernel's shell channel carries all
 * three. An entry of the wrong *shape* is dropped rather than failing the reply, for the same
 * reason - with `output: true` the third element is an `[input, output]` pair, and a caller that
 * asked for one is not this app.
 *
 * ```
 * KernelHistoryEntries.of(frame, requestMsgId)   // ["x = 1", "x + 1"]
 * ```
 */
internal object KernelHistoryEntries {
  fun of(frame: String, requestMsgId: String): List<String>? =
    JupyterFrame.ofType(frame, requestMsgId, "history_reply")?.let { reply ->
      // Absent on an error reply, which has no history to offer rather than something to report -
      // the same reading `KernelCompletions` gives an error `complete_reply`.
      val entries = reply.content()?.get("history")?.jsonArray ?: return@let emptyList()
      entries.mapNotNull { entry -> (entry as? JsonArray)?.getOrNull(INPUT)?.let { it as? JsonPrimitive }?.content }
    }

  /** `[session, line, input]` - the reference reads the third and nothing else. */
  private const val INPUT = 2
}
