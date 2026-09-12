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

  /**
   * The first message on any new socket, and the reason is not curiosity about the kernel.
   *
   * A completed websocket handshake is not a kernel ready to be asked anything: `jupyter_server`
   * is still subscribing the session to the kernel's ZMQ channels, and a request arriving in that
   * window is dropped in silence. JupyterLab sends this first on every connection "to get kernel
   * status back and ensure iopub is fully established"
   * (`packages/services/src/kernel/default.ts`, `_updateConnectionStatus`).
   */
  fun kernelInfoRequest(sessionId: String, msgId: String): String =
    shellRequest("kernel_info_request", sessionId, msgId, buildJsonObject { })

  /** [cursorOffset] becomes `cursor_pos`, which the protocol counts in characters, not bytes. */
  fun completeRequest(code: String, cursorOffset: Int, sessionId: String, msgId: String): String = shellRequest(
    "complete_request",
    sessionId,
    msgId,
    buildJsonObject {
      put("code", code)
      put("cursor_pos", cursorOffset)
    },
  )

  /**
   * The tail of the kernel's input history - the reference's own request, transcribed
   * (`packages/notebook/src/history.ts:402-409`).
   *
   * `raw` is the input as it was typed rather than after IPython's transformations, and `output`
   * is false because the app pastes a previous *input* into a cell and has no use for what it
   * printed. The two are not cosmetic: the shipped kernel passes both straight to
   * `history_manager.get_tail`, and `output: true` changes the shape of every entry from a string
   * to an `[input, output]` pair (`ipykernel/ipkernel.py:612-645` in the shipped rootfs).
   */
  fun historyRequest(entries: Int, sessionId: String, msgId: String): String = shellRequest(
    "history_request",
    sessionId,
    msgId,
    buildJsonObject {
      put("output", false)
      put("raw", true)
      put("hist_access_type", "tail")
      put("n", entries)
    },
  )

  /**
   * A DAP request framed as a `debug_request` on the **control** channel - `debugpy`/`ipykernel`
   * speak DAP wrapped in this envelope rather than DAP's own base protocol.
   * `content: {seq, type:'request', command, arguments}`
   * (`packages/services/src/kernel/messages.ts:1244`, the reference's own `IDebugRequestMsg`).
   * [seq] is the caller's to track - JupyterLab's own session counts it per debug session,
   * starting at 0 (`packages/debugger/src/session.ts:302`), and this builder only frames one
   * request, so it takes what the caller is already counting rather than counting its own.
   */
  fun debugRequest(command: String, args: JsonObject, seq: Int, sessionId: String, msgId: String): String =
    controlRequest("debug_request", sessionId, msgId, debugContent(command, args, seq))

  private fun shellRequest(msgType: String, sessionId: String, msgId: String, content: JsonObject) =
    request("shell", msgType, sessionId, msgId, content)

  private fun controlRequest(msgType: String, sessionId: String, msgId: String, content: JsonObject) =
    request("control", msgType, sessionId, msgId, content)

  private fun request(channel: String, msgType: String, sessionId: String, msgId: String, content: JsonObject) =
    buildJsonObject {
      put("channel", channel)
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

  private fun debugContent(command: String, args: JsonObject, seq: Int) = buildJsonObject {
    put("seq", seq)
    put("type", "request")
    put("command", command)
    put("arguments", args)
  }

  private const val PROTOCOL_VERSION = "5.3"
}
