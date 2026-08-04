package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.CellOutput
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
    buildJsonObject {
      put("channel", "shell")
      put("header", header(msgId, sessionId))
      put("parent_header", buildJsonObject { })
      put("metadata", buildJsonObject { })
      put("content", executeContent(code))
      put("buffers", buildJsonArray { })
    }.toString()

  /**
   * The `text/plain` rendering of our request's result, or null if this is not it.
   * Retires with [KernelChannel] at Step 10.5; [KernelOutputs.outputOf] is the shape the
   * `KernelGateway` port streams.
   */
  fun resultOf(frame: String, requestMsgId: String): String? =
    (KernelOutputs.outputOf(frame, requestMsgId) as? CellOutput.ExecuteResult)?.mimeBundle?.get("text/plain")

  /** `ename: evalue` when our request raised, or null. Retires with [resultOf]. */
  fun errorOf(frame: String, requestMsgId: String): String? =
    (KernelOutputs.outputOf(frame, requestMsgId) as? CellOutput.Error)?.let { "${it.name}: ${it.value}" }

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

  private const val PROTOCOL_VERSION = "5.3"
}
