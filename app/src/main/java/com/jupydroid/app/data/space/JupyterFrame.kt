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
  fun ours(frame: String, requestMsgId: String): JsonObject? =
    decode(frame)?.takeIf {
      it["parent_header"]?.jsonObject?.get("msg_id")?.jsonPrimitive?.content == requestMsgId
    }

  /** [ours], narrowed to a single message type. */
  fun ofType(frame: String, requestMsgId: String, msgType: String): JsonObject? =
    ours(frame, requestMsgId)?.takeIf { it.msgType() == msgType }

  fun JsonObject.msgType(): String? =
    get("header")?.jsonObject?.get("msg_type")?.jsonPrimitive?.content

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
