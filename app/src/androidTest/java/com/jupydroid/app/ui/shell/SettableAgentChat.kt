package com.jupydroid.app.ui.shell

import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentSelection
import com.jupydroid.extensions.ai.domain.ChatTranscript
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A chat showing whatever the test last set in [shown], so a test can change a message the panel
 * already draws, as the server does when a call it holds comes to ask. What it was asked is in [asked],
 * oldest first, with what each was asked with in [selections], and no answer comes of it.
 *
 * ```
 * val chat = SettableAgentChat(before) // the panel draws before ...
 * chat.shown.value = after             // ... and then after
 * ```
 */
class SettableAgentChat(first: ChatTranscript) : AgentChat {
  val shown = MutableStateFlow(first)

  override fun transcript(): Flow<ChatTranscript> = shown

  val asked = CopyOnWriteArrayList<String>()

  val selections = CopyOnWriteArrayList<AgentSelection>()

  override suspend fun ask(question: String, selection: AgentSelection) {
    selections += selection
    asked += question
  }

  override suspend fun choose(call: ToolCall, option: PermissionOption) = Unit

  override suspend fun stop() = Unit
}
