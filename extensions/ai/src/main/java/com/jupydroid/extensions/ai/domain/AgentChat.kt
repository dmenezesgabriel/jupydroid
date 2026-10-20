package com.jupydroid.extensions.ai.domain

import kotlinx.coroutines.flow.Flow

/**
 * A chat with an agent in the entered Space.
 *
 * `OkHttpAgentChat` speaks jupyterlab-chat's socket to jupyter-ai's opencode persona;
 * `FakeAgentChat` answers from a map.
 *
 * ```
 * chat.transcript().collect { shown = it } // and, once the first transcript has arrived:
 * chat.ask("What is a Space?")
 * ```
 */
interface AgentChat {
  /**
   * The chat's transcript, first as it stands and then after every message that arrives. The chat is
   * open while this is collected, and only then can it be [ask]ed.
   *
   * @throws AgentChatException when the chat cannot be opened or answers something unreadable.
   */
  fun transcript(): Flow<ChatTranscript>

  /**
   * Sends [question] to the agent. The question, and the agent's answer, arrive in [transcript].
   *
   * @throws AgentChatException when no [transcript] is being collected.
   */
  suspend fun ask(question: String)

  /**
   * Answers [call], which waits on the user, with [option], one of those it offers. The call, resolved,
   * arrives in [transcript].
   *
   * @throws ToolCallChoiceException when nothing waits on that call any more, or the server refuses.
   */
  suspend fun choose(call: ToolCall, option: PermissionOption)

  /**
   * Stops the reply an agent is writing. What it wrote so far stays, and [transcript] then says no
   * agent writes ([ChatTranscript.agentWriting]); with none writing, nothing happens.
   *
   * @throws AgentChatException when no [transcript] is being collected, or the server refuses.
   */
  suspend fun stop()
}

/** The chat failed, or was asked [question] while it was not open. */
open class AgentChatException(reason: String, question: String? = null) :
  Exception(question?.let { "asking '$it' failed: $reason" } ?: reason)

/**
 * The chat's socket ended without a close frame, as it does when the server behind it exits: the one
 * ending worth opening the chat again for ([readings]).
 */
class AgentChatDroppedException(reason: String) : AgentChatException(reason)
