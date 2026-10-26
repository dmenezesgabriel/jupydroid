package com.jupydroid.extensions.ai.data.fake

import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatContract
import com.jupydroid.extensions.ai.domain.AgentCommand
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentSetting
import com.jupydroid.extensions.ai.domain.ChatMessage

class FakeAgentChatTest : AgentChatContract() {
  override fun chat(
    history: List<ChatMessage>,
    replies: Map<String, String>,
    writing: Boolean,
    models: AgentModels,
    settings: List<AgentSetting>,
    commands: List<AgentCommand>,
  ): AgentChat = FakeAgentChat(history, replies, writing, models, settings, commands)
}
