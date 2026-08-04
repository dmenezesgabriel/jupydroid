package com.jupydroid.app.data.space

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The Jupyter wire messages this app exchanges over a kernel WebSocket.
 *
 * jupyter_server speaks its original JSON protocol when the client requests no
 * subprotocol, which is what we do: one JSON text frame per message, carrying the
 * channel alongside the usual header/parent_header/metadata/content.
 *
 * Every reader here is total - a frame that is malformed, unrelated or simply of
 * another type returns null rather than throwing. A kernel is shared, so most
 * traffic on iopub belongs to somebody else, and one bad frame must not take the
 * channel down.
 */
object JupyterMessage {
  private val json = Json { ignoreUnknownKeys = true }

  fun executeRequest(code: String, sessionId: String, msgId: String): String =
    buildJsonObject {
      put("channel", "shell")
      put("header", header(msgId, sessionId))
      put("parent_header", buildJsonObject { })
      put("metadata", buildJsonObject { })
      put("content", executeContent(code))
      put("buffers", buildJsonArray { })
    }.toString()

  private fun header(msgId: String, sessionId: String) = buildJsonObject {
    put("msg_id", msgId)
    put("session", sessionId)
    put("username", "jupydroid")
    put("msg_type", "execute_request")
    put("version", PROTOCOL_VERSION)
  }

  private fun executeContent(code: String) = buildJsonObject {
    put("code", code)
    put("silent", false)
    put("store_history", true)
    put("allow_stdin", false) // there is no terminal behind this kernel
    put("stop_on_error", true)
  }

  /** The `text/plain` rendering of our request's result, or null if this is not it. */
  fun resultOf(frame: String, requestMsgId: String): String? {
    val message = parse(frame, requestMsgId, "execute_result") ?: return null
    return message["content"]?.jsonObject
      ?.get("data")?.jsonObject
      ?.get("text/plain")?.jsonPrimitive?.content
  }

  /** `ename: evalue` when our request raised, or null. */
  fun errorOf(frame: String, requestMsgId: String): String? {
    val content = parse(frame, requestMsgId, "error")?.get("content")?.jsonObject
    val name = content?.get("ename")?.jsonPrimitive?.content
    return name?.let { "$it: ${content["evalue"]?.jsonPrimitive?.content.orEmpty()}" }
  }

  private fun parse(frame: String, requestMsgId: String, msgType: String): JsonObject? {
    val message = decode(frame) ?: return null
    val isReplyToUs = message["parent_header"]?.jsonObject
      ?.get("msg_id")?.jsonPrimitive?.content == requestMsgId
    val isWantedType = message["header"]?.jsonObject
      ?.get("msg_type")?.jsonPrimitive?.content == msgType
    return message.takeIf { isReplyToUs && isWantedType }
  }

  @Suppress("SwallowedException") // an unparseable frame is other traffic, not our failure
  private fun decode(frame: String): JsonObject? =
    try {
      json.parseToJsonElement(frame).jsonObject
    } catch (e: SerializationException) {
      null
    } catch (e: IllegalArgumentException) {
      null
    }

  private const val PROTOCOL_VERSION = "5.3"
}
