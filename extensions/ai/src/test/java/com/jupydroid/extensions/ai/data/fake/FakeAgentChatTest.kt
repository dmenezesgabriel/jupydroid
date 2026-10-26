package com.jupydroid.extensions.ai.data.fake

import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatContract
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.ChatMessage

class FakeAgentChatTest : AgentChatContract() {
  override fun chat(
    history: List<ChatMessage>,
    replies: Map<String, String>,
    writing: Boolean,
    models: AgentModels,
  ): AgentChat = FakeAgentChat(history, replies, writing, models)
}
