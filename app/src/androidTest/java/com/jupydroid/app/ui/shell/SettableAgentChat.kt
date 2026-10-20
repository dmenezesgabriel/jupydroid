package com.jupydroid.app.ui.shell

import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.ChatTranscript
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A chat showing whatever the test last set in [shown], so a test can change a message the panel
 * already draws, as the server does when a call it holds comes to ask.
 *
 * ```
 * val chat = SettableAgentChat(before) // the panel draws before ...
 * chat.shown.value = after             // ... and then after
 * ```
 */
class SettableAgentChat(first: ChatTranscript) : AgentChat {
  val shown = MutableStateFlow(first)

  override fun transcript(): Flow<ChatTranscript> = shown

  override suspend fun ask(question: String) = Unit

  override suspend fun choose(call: ToolCall, option: PermissionOption) = Unit
}
