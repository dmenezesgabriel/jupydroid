package com.jupydroid.extensions.ai.data.fake

import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.AgentCommand
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentSelection
import com.jupydroid.extensions.ai.domain.AgentSetting
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatTranscript
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallChoiceException
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/**
 * A chat already holding [history], whose agent answers each question in [replies] with the reply it
 * maps to and every other question with nothing; when [writing], its agent is writing a reply until
 * it is stopped. Its agent offers [models], [settings] and [commands], and switches to what a question is
 * asked with.
 *
 * What the panel talks to where the Space has no server, so it still opens and claims no answer
 * nobody gave: with nothing configured, a question goes unanswered.
 *
 * ```
 * FakeAgentChat(replies = mapOf("hello?" to "hi")).ask("hello?") // while transcript() is collected
 * ```
 */
class FakeAgentChat(
  history: List<ChatMessage> = emptyList(),
  private val replies: Map<String, String> = emptyMap(),
  writing: Boolean = false,
  models: AgentModels = AgentModels(),
  settings: List<AgentSetting> = emptyList(),
  commands: List<AgentCommand> = emptyList(),
) : AgentChat {
  private val shown = MutableStateFlow(
    ChatTranscript(history, writing, agentModels = models, agentSettings = settings, agentCommands = commands),
  )
  private val collectors = AtomicInteger()

  override fun transcript(): Flow<ChatTranscript> = shown
    .onStart { collectors.incrementAndGet() }
    .onCompletion { collectors.decrementAndGet() }

  override suspend fun ask(question: String, selection: AgentSelection) {
    if (collectors.get() == 0) throw AgentChatException("the chat is not open", question)
    selection.model?.let { model -> shown.update { it.copy(agentModels = it.agentModels.copy(current = model)) } }
    val switched = shown.value.agentSettings.map { it.copy(current = selection.settings[it.id] ?: it.current) }
    shown.update { it.copy(agentSettings = switched) }
    val asked = shown.value.messages.size
    shown.update { it.with(ChatMessage(id = "question-$asked", body = question, fromMe = true)) }
    val reply = replies[question] ?: return
    shown.update { it.with(ChatMessage(id = "reply-$asked", body = reply, fromMe = false)) }
  }

  /** Resolves [call] with [option] in the message holding it, as the server does once answered. */
  override suspend fun choose(call: ToolCall, option: PermissionOption) {
    val holding = shown.value.messages.find { message -> message.toolCalls.any { it.awaits(call) } }
      ?: throw ToolCallChoiceException(call, option, "nothing waits on it")
    val resolved = holding.toolCalls.map {
      if (it.awaits(call)) it.copy(permission = ToolCallPermission.Resolved(option)) else it
    }
    shown.update { it.with(holding.copy(toolCalls = resolved)) }
  }

  override suspend fun stop() {
    if (collectors.get() == 0) throw AgentChatException("the chat is not open")
    shown.update { it.copy(agentWriting = false) }
  }

  private fun ToolCall.awaits(call: ToolCall): Boolean = id == call.id && permission is ToolCallPermission.Pending
}
