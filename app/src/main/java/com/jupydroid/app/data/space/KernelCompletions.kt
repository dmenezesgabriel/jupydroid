package com.jupydroid.app.data.space

import com.jupydroid.app.data.space.JupyterFrame.content
import com.jupydroid.app.domain.port.CompletionMatch
import com.jupydroid.app.domain.port.CompletionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads a `complete_reply` into a [CompletionResult].
 *
 * The reply's shape is the protocol's, verified against JupyterLab's own message types rather
 * than assumed: `matches`, plus the `cursor_start`/`cursor_end` range those matches replace
 * (`reference/research_repos/jupyterlab/packages/services/src/kernel/messages.ts:866-878`).
 *
 * Total, like every other reader here: a frame belonging to somebody else, of the wrong type,
 * or unparseable is null rather than an exception, because a shared kernel's shell channel
 * carries all three.
 *
 * ```
 * KernelCompletions.of(frame, requestMsgId)?.matches
 * ```
 */
internal object KernelCompletions {
  fun of(frame: String, requestMsgId: String): CompletionResult? =
    JupyterFrame.ofType(frame, requestMsgId, "complete_reply")?.let { reply ->
      val content = reply.content() ?: return@let CompletionResult.NONE
      CompletionResult(
        // A kernel's `matches` are bare strings: what the row shows is what accepting writes.
        matches = content.strings("matches").map { CompletionMatch(it, it) },
        replacedStart = content.int("cursor_start"),
        replacedEnd = content.int("cursor_end"),
      )
    }

  /** Absent on an error reply, which has nothing to offer rather than something to report. */
  private fun JsonObject.strings(key: String): List<String> =
    get(key)?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

  private fun JsonObject.int(key: String): Int = get(key)?.jsonPrimitive?.content?.toIntOrNull() ?: 0
}
