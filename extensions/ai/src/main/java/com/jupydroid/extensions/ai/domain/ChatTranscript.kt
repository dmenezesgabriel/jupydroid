package com.jupydroid.extensions.ai.domain

/**
 * One message of a chat: the user's question or an agent's answer.
 *
 * @param id unique within the chat; an agent streaming its answer sends the same id again with more
 *   of the body each time.
 * @param fromMe whether the user of this app sent it, rather than an agent.
 */
data class ChatMessage(val id: String, val body: String, val fromMe: Boolean)

/**
 * A chat's messages, oldest first.
 *
 * ```
 * ChatTranscript().with(ChatMessage("q", "hello?", fromMe = true)).messages // [q]
 * ```
 */
data class ChatTranscript(val messages: List<ChatMessage> = emptyList()) {
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
