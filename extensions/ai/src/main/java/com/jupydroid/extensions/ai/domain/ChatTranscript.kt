package com.jupydroid.extensions.ai.domain

/**
 * One message of a chat: the user's question or an agent's answer.
 *
 * @param id unique within the chat; an agent streaming its answer sends the same id again with more
 *   of the body each time.
 * @param fromMe whether the user of this app sent it, rather than an agent.
 * @param toolCalls the agent's tool calls this message holds, in the order it made them.
 */
data class ChatMessage(
  val id: String,
  val body: String,
  val fromMe: Boolean,
  val toolCalls: List<ToolCall> = emptyList(),
)

/**
 * A tool the agent called: reading a file, running a command.
 *
 * ```
 * ToolCall(id = "c1", title = "probe.txt", status = ToolCallStatus.Running)
 * ```
 *
 * @param permission whether it waits, or waited, on the user; null when the agent ran it unasked.
 */
data class ToolCall(
  val id: String,
  val title: String,
  val status: ToolCallStatus,
  val permission: ToolCallPermission? = null,
) {
  /**
   * [status], except that a call the user rejected is [ToolCallStatus.Failed] whatever the agent then
   * reports, as jupyter-chat-components marks it (jupyter-chat-components@992c6fb
   * `src/components/grouped-tool-calls.tsx`, `ToolCallRow`'s `isRejected`).
   */
  val shownStatus: ToolCallStatus
    get() {
      val rejected = (permission as? ToolCallPermission.Resolved)?.chosen?.rejects == true
      return if (rejected) ToolCallStatus.Failed else status
    }
}

/** Where a [ToolCall] stands: ACP's `pending` and `in_progress` are both still [Running]. */
enum class ToolCallStatus { Running, Completed, Failed }

/**
 * A chat's messages, oldest first.
 *
 * ```
 * ChatTranscript().with(ChatMessage("q", "hello?", fromMe = true)).messages // [q]
 * ```
 */
data class ChatTranscript(val messages: List<ChatMessage> = emptyList()) {
  /**
   * [messages] without those that have neither text nor a tool call: jupyter-ai's ACP client creates
   * every message of an agent's with an empty body, and one that holds the agent's tool calls keeps it,
   * the calls being in its metadata (`usr/lib/python3.12/site-packages/jupyter_ai_acp_client/tool_call_manager.py`
   * in the shipped rootfs). jupyter-chat draws one under its sender's name
   * (jupyter-chat@9e02bb70 `packages/jupyter-chat/src/components/messages/messages.tsx`); a row here
   * has no name above it, so one with nothing in it would be a blank bubble.
   */
  val messagesShown: List<ChatMessage> get() = messages.filter { it.body.isNotEmpty() || it.toolCalls.isNotEmpty() }

  /**
   * [message] in place of the one with its id, or beneath the others if it is new: jupyterlab-chat
   * sends a new message and an update to one in the same frame (jupyterlab_chat 0.25.2 `ws_messages.py`,
   * `ServerMessageMessage`).
   */
  fun with(message: ChatMessage): ChatTranscript {
    val index = messages.indexOfFirst { it.id == message.id }
    if (index < 0) return ChatTranscript(messages + message)
    return ChatTranscript(messages.toMutableList().apply { set(index, message) })
  }
}
