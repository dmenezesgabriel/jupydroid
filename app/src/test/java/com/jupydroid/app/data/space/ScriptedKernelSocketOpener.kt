package com.jupydroid.app.data.space

import kotlinx.serialization.json.Json
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
  private val endAfter: KernelSocketEvent.Ended? = null,
  /**
   * Whether the handshake has finished by the time [open] returns.
   *
   * False is the case that matters and the one a real socket has: OkHttp returns a `WebSocket`
   * immediately and reports `Opened` later. Call [finishHandshake] to release it, or
   * [failHandshake] to end the socket without ever opening.
   */
  private val openImmediately: Boolean = true,
  /**
   * Whether a `kernel_info_request` is answered.
   *
   * False is the case JupyterLab's own FIXME describes - a kernel whose ZMQ subscriptions are not
   * established never resolves it - and it is what the failsafe timeout exists for.
   */
  private val answerKernelInfo: Boolean = true,
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

  private var opened = false
  private var events: ((KernelSocketEvent) -> Unit)? = null

  /** Completes the handshake, the way OkHttp's `onOpen` does. */
  fun finishHandshake() {
    opened = true
    events?.invoke(KernelSocketEvent.Opened)
  }

  /** Ends the socket without it ever opening: a refused upgrade, a 404 for a dead kernel. */
  fun failHandshake(reason: String) {
    events?.invoke(KernelSocketEvent.Ended(reason = reason, failed = true))
  }

  override fun open(
    kernelId: String,
    sessionId: String,
    onEvent: (KernelSocketEvent) -> Unit,
  ): KernelSocket {
    openedKernelId = kernelId
    openedSessionId = sessionId
    openCount++
    events = onEvent
    if (openImmediately) {
      opened = true
      onEvent(KernelSocketEvent.Opened)
    }
    return ScriptedSocket(onEvent)
  }

  private inner class ScriptedSocket(private val onEvent: (KernelSocketEvent) -> Unit) : KernelSocket {
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
      answer(msgType, requestMsgId)
      return true
    }

    private fun answer(msgType: String, requestMsgId: String) {
      if (msgType == "kernel_info_request") {
        if (answerKernelInfo) onEvent(KernelSocketEvent.Received(KernelFrames.kernelInfoReply(requestMsgId)))
        return
      }
      reply(requestMsgId).forEach { onEvent(KernelSocketEvent.Received(it)) }
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

  fun kernelInfoReply(parent: String) =
    """{"channel":"shell","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"kernel_info_reply"},
       "content":{"status":"ok","protocol_version":"5.3"}}"""

  fun idle(parent: String) =
    """{"channel":"iopub","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"status"},
       "content":{"execution_state":"idle"}}"""

  /** A complete, successful execution: input, output, then both completion signals. */
  fun successfulExecution(parent: String, text: String = "4") = listOf(
    executeInput(parent, 1),
    executeResult(parent, text, 1),
    executeReply(parent),
    idle(parent),
  )
}
