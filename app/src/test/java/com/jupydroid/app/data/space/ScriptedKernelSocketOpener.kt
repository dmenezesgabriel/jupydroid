package com.jupydroid.app.data.space

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A [KernelSocketOpener] that answers whatever is sent to it with scripted frames.
 *
 * [reply] receives the `msg_id` of the request just sent, so a script can address its
 * frames to the right request the way a kernel does - which is the only way to test that
 * the gateway ignores traffic belonging to somebody else.
 *
 * ```
 * ScriptedKernelSocketOpener { id -> listOf(executeInput(id, 1), stream(id, "hi"), reply(id), idle(id)) }
 * ```
 */
class ScriptedKernelSocketOpener(
  private val reply: (requestMsgId: String) -> List<String>,
  /** Ends the socket once the scripted frames are delivered, so failure paths can be driven. */
  private val endAfter: JupyterSocketEvent.Ended? = null,
  /**
   * Whether the handshake has finished by the time [open] returns.
   *
   * False is the case that matters and the one a real socket has: OkHttp returns a `WebSocket`
   * immediately and reports `Opened` later. Call [finishHandshake] to release it, or
   * [failHandshake] to end the socket without ever opening.
   */
  private val openImmediately: Boolean = true,
  /**
   * Whether the *nth* `kernel_info_request` on this socket is answered, counting from 1.
   *
   * Never answering is the case JupyterLab's own FIXME describes - a kernel whose ZMQ
   * subscriptions are not established never resolves it - and it is what the failsafe timeout
   * exists for. Answering only from the second ask is what a **restart** looks like from the
   * socket's side: the process behind it is replaced while the socket stays open, because
   * `jupyter_server` restarts with `newports=False` and so reuses the same endpoints
   * (`jupyter_client/manager.py`, `_async_restart_kernel`).
   *
   * ```
   * answerKernelInfo = { ask -> ask > 1 }   // dead until the kernel is restarted
   * ```
   */
  private val answerKernelInfo: (ask: Int) -> Boolean = { true },
) : KernelSocketOpener {
  var openedKernelId: String? = null
    private set
  var openedSessionId: String? = null
    private set
  var closed = false
    private set
  var openCount = 0
    private set

  /** What was sent before the handshake finished - lost frames, on a real server. */
  val sentBeforeOpen = mutableListOf<String>()

  /** The `msg_type` of everything sent, in order, so the handshake's place in it can be asserted. */
  val sentMsgTypes = mutableListOf<String>()

  /**
   * Every frame sent, verbatim.
   *
   * Beside [sentMsgTypes] rather than replacing it: a request's *content* is part of the protocol
   * too - `history_request` is only the right question when it carries `hist_access_type`, `raw`
   * and `output` - and a type alone cannot say so.
   */
  val sent = mutableListOf<String>()

  private var opened = false
  private var events: ((JupyterSocketEvent) -> Unit)? = null
  private var kernelInfoAsks = 0

  /** Completes the handshake, the way OkHttp's `onOpen` does. */
  fun finishHandshake() {
    opened = true
    events?.invoke(JupyterSocketEvent.Opened)
  }

  /** Ends the socket without it ever opening: a refused upgrade, a 404 for a dead kernel. */
  fun failHandshake(reason: String) {
    events?.invoke(JupyterSocketEvent.Ended(reason = reason, failed = true))
  }

  override fun open(kernelId: String, sessionId: String, onEvent: (JupyterSocketEvent) -> Unit): JupyterSocket {
    openedKernelId = kernelId
    openedSessionId = sessionId
    openCount++
    events = onEvent
    if (openImmediately) {
      opened = true
      onEvent(JupyterSocketEvent.Opened)
    }
    return ScriptedSocket(onEvent)
  }

  private inner class ScriptedSocket(private val onEvent: (JupyterSocketEvent) -> Unit) : JupyterSocket {
    override fun send(frame: String): Boolean {
      // A real server drops what arrives before its own `open()` has associated the session, so
      // the fake keeps it and answers nothing - which is what the stall looks like.
      if (!opened) {
        // A real server drops what arrives before its own `open()` has associated the session.
        sentBeforeOpen += frame
        return true
      }
      val header = Json.parseToJsonElement(frame).jsonObject["header"]!!.jsonObject
      val requestMsgId = header["msg_id"]!!.jsonPrimitive.content
      val msgType = header["msg_type"]!!.jsonPrimitive.content
      sentMsgTypes += msgType
      sent += frame
      answer(msgType, requestMsgId)
      return true
    }

    private fun answer(msgType: String, requestMsgId: String) {
      if (msgType == "kernel_info_request") {
        kernelInfoAsks++
        if (answerKernelInfo(kernelInfoAsks)) {
          onEvent(JupyterSocketEvent.Received(KernelFrames.kernelInfoReply(requestMsgId)))
        }
        return
      }
      reply(requestMsgId).forEach { onEvent(JupyterSocketEvent.Received(it)) }
      endAfter?.let(onEvent)
    }

    override fun close() {
      closed = true
    }
  }
}

/** Frames a kernel sends, in the order it sends them. */
object KernelFrames {
  fun executeInput(parent: String, count: Int) =
    """{"channel":"iopub","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"execute_input"},
       "content":{"code":"2 + 2","execution_count":$count}}"""

  fun stream(parent: String, text: String) =
    """{"channel":"iopub","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"stream"},
       "content":{"name":"stdout","text":"$text"}}"""

  fun executeResult(parent: String, text: String, count: Int) =
    """{"channel":"iopub","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"execute_result"},
       "content":{"execution_count":$count,"data":{"text/plain":"$text"}}}"""

  fun executeReply(parent: String) =
    """{"channel":"shell","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"execute_reply"},
       "content":{"status":"ok","execution_count":1}}"""

  fun completeReply(parent: String, matches: List<String>, cursorStart: Int, cursorEnd: Int) =
    """{"channel":"shell","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"complete_reply"},
       "content":{"status":"ok","matches":[${matches.joinToString { "\"$it\"" }}],
       "cursor_start":$cursorStart,"cursor_end":$cursorEnd}}"""

  /**
   * A `history_reply`, whose entries are `[session, line, input]` triples
   * (`packages/services/src/kernel/messages.ts:1035`).
   *
   * The line numbers are this frame's own invention, because nothing reads them: with
   * `output: false` the app takes the third element and no other
   * (`packages/notebook/src/history.ts:361-365`).
   */
  fun historyReply(parent: String, inputs: List<String>) =
    """{"channel":"shell","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"history_reply"},
       "content":{"status":"ok","history":[${inputs.mapIndexed { line, input ->
      """[1,$line,"$input"]"""
    }.joinToString()}]}}"""

  fun kernelInfoReply(parent: String) =
    """{"channel":"shell","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"kernel_info_reply"},
       "content":{"status":"ok","protocol_version":"5.3"}}"""

  fun idle(parent: String) = """{"channel":"iopub","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"status"},
       "content":{"execution_state":"idle"}}"""

  /** A complete, successful execution: input, output, then both completion signals. */
  fun successfulExecution(parent: String, text: String = "4") = listOf(
    executeInput(parent, 1),
    executeResult(parent, text, 1),
    executeReply(parent),
    idle(parent),
  )

  /** A `debug_reply` on the control channel - [success] false is the DAP-level failure a caller must see. */
  fun debugReply(
    parent: String,
    command: String,
    success: Boolean = true,
    message: String? = null,
    body: JsonObject = buildJsonObject { },
  ): String {
    val messageField = message?.let { ""","message":"$it"""" }.orEmpty()
    return """{"channel":"control","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"debug_reply"},
       "content":{"seq":0,"type":"response","request_seq":0,"success":$success,"command":"$command"$messageField,
       "body":$body}}"""
  }

  /** A `debug_event` on iopub - no `parent_header`, since it answers no request (`session.ts:266`). */
  fun debugEvent(event: String, body: JsonObject = buildJsonObject { }) =
    """{"channel":"iopub","header":{"msg_type":"debug_event"},
       "content":{"seq":0,"type":"event","event":"$event","body":$body}}"""
}
