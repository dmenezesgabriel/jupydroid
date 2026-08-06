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
) : KernelSocketOpener {
  var openedKernelId: String? = null
    private set
  var openedSessionId: String? = null
    private set
  var closed = false
    private set
  var openCount = 0
    private set

  override fun open(
    kernelId: String,
    sessionId: String,
    onEvent: (KernelSocketEvent) -> Unit,
  ): KernelSocket {
    openedKernelId = kernelId
    openedSessionId = sessionId
    openCount++
    onEvent(KernelSocketEvent.Opened)
    return ScriptedSocket(onEvent)
  }

  private inner class ScriptedSocket(private val onEvent: (KernelSocketEvent) -> Unit) : KernelSocket {
    override fun send(frame: String): Boolean {
      val requestMsgId = Json.parseToJsonElement(frame)
        .jsonObject["header"]!!.jsonObject["msg_id"]!!.jsonPrimitive.content
      reply(requestMsgId).forEach { onEvent(KernelSocketEvent.Received(it)) }
      endAfter?.let(onEvent)
      return true
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
