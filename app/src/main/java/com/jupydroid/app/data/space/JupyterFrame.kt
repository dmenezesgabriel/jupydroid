package com.jupydroid.app.data.space

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decoding and correlation for kernel WebSocket frames - the one thing every reader needs
 * before it can read anything.
 *
 * A kernel is shared, so most of what arrives on iopub belongs to another cell, another
 * notebook or the kernel talking about itself. Correlation is therefore not an optimisation
 * but the difference between showing a user their own output and showing them somebody
 * else's; and every function here is total, because one malformed frame must not take the
 * channel down.
 *
 * ```
 * JupyterFrame.ours(frame, requestMsgId)?.msgType()
 * ```
 */
internal object JupyterFrame {
  private val json = Json { ignoreUnknownKeys = true }

  /** The decoded frame, but only when it is a reply to the request we are waiting on. */
  fun ours(frame: String, requestMsgId: String): JsonObject? = decode(frame)?.takeIf {
    it["parent_header"]?.jsonObject?.get("msg_id")?.jsonPrimitive?.content == requestMsgId
  }

  /** [ours], narrowed to a single message type. */
  fun ofType(frame: String, requestMsgId: String, msgType: String): JsonObject? =
    ours(frame, requestMsgId)?.takeIf { it.msgType() == msgType }

  fun JsonObject.msgType(): String? = get("header")?.jsonObject?.get("msg_type")?.jsonPrimitive?.content

  /**
   * The message type of any frame, ours or not, for the log.
   *
   * The fourth sighting of the execution stall showed three frames arriving and then silence -
   * which ruled out "nothing came back" and "nobody was listening" and left "which three?"
   * unanswerable, because the log carried only their byte length. Unparseable frames report
   * null rather than throwing: this is a diagnostic, and it must never be the thing that fails.
   */
  fun msgTypeOf(frame: String): String? = decode(frame)?.msgType()

  /**
   * The request a frame is answering, or null when it answers none.
   *
   * The field that separates "the kernel never saw our request" from "the kernel is answering it
   * slowly": a `status` with our `msg_id` as its parent is the kernel acknowledging the execute,
   * while a parentless one is its own startup chatter. The fifth sighting of the stall showed
   * three statuses and no `execute_input`, and without the parent there was no way to say which.
   */
  fun parentMsgIdOf(frame: String): String? =
    decode(frame)?.get("parent_header")?.jsonObject?.get("msg_id")?.jsonPrimitive?.content

  fun JsonObject.content(): JsonObject? = get("content")?.jsonObject

  @Suppress("SwallowedException") // an unparseable frame is other traffic, not our failure
  private fun decode(frame: String): JsonObject? =
    try {
      json.parseToJsonElement(frame).jsonObject
    } catch (e: SerializationException) {
      null
    } catch (e: IllegalArgumentException) {
      null
    }
}
