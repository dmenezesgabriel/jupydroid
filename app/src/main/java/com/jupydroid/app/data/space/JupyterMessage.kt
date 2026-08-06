package com.jupydroid.app.data.space

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The requests this app sends over a kernel WebSocket.
 *
 * jupyter_server speaks its original JSON protocol when the client requests no
 * subprotocol, which is what we do: one JSON text frame per message, carrying the
 * channel alongside the usual header/parent_header/metadata/content.
 *
 * Reading is [KernelOutputs]' job; correlation is [JupyterFrame]'s.
 */
object JupyterMessage {
  fun executeRequest(code: String, sessionId: String, msgId: String): String =
    shellRequest("execute_request", sessionId, msgId, executeContent(code))

  /** [cursorOffset] becomes `cursor_pos`, which the protocol counts in characters, not bytes. */
  fun completeRequest(code: String, cursorOffset: Int, sessionId: String, msgId: String): String =
    shellRequest(
      "complete_request",
      sessionId,
      msgId,
      buildJsonObject {
        put("code", code)
        put("cursor_pos", cursorOffset)
      },
    )

  private fun shellRequest(msgType: String, sessionId: String, msgId: String, content: JsonObject) =
    buildJsonObject {
      put("channel", "shell")
      put("header", header(msgType, msgId, sessionId))
      put("parent_header", buildJsonObject { })
      put("metadata", buildJsonObject { })
      put("content", content)
      put("buffers", buildJsonArray { })
    }.toString()

  private fun header(msgType: String, msgId: String, sessionId: String) = buildJsonObject {
    put("msg_id", msgId)
    put("session", sessionId)
    put("username", "jupydroid")
    put("msg_type", msgType)
    put("version", PROTOCOL_VERSION)
  }

  private fun executeContent(code: String) = buildJsonObject {
    put("code", code)
    put("silent", false)
    put("store_history", true)
    put("allow_stdin", false) // there is no terminal behind this kernel
    put("stop_on_error", true)
  }

  private const val PROTOCOL_VERSION = "5.3"
}
