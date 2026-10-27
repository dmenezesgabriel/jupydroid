package com.jupydroid.app.ui.shell

import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentPersona
import com.jupydroid.extensions.ai.domain.AgentSelection
import com.jupydroid.extensions.ai.domain.ChatTranscript
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A chat showing whatever the test last set in [shown], so a test can change a message the panel
 * already draws, as the server does when a call it holds comes to ask. What it was asked is in [asked],
 * oldest first, with the agent each was asked of in [askedOf] and what each was asked with in
 * [selections], and no answer comes of it. Each agent selected is in [selected]. A transcript that lists
 * no agent is shown listing opencode's ([listingAnAgent]).
 *
 * ```
 * val chat = SettableAgentChat(before) // the panel draws before ...
 * chat.shown.value = after             // ... and then after
 * ```
 */
class SettableAgentChat(first: ChatTranscript) : AgentChat {
  val shown = MutableStateFlow(first)

  override fun transcript(): Flow<ChatTranscript> = shown.map { it.listingAnAgent() }

  val asked = CopyOnWriteArrayList<String>()

  val askedOf = CopyOnWriteArrayList<String>()

  val selections = CopyOnWriteArrayList<AgentSelection>()

  val selected = CopyOnWriteArrayList<String>()

  override suspend fun ask(question: String, persona: String, selection: AgentSelection) {
    selections += selection
    askedOf += persona
    asked += question
  }

  override suspend fun select(persona: String) {
    selected += persona
  }

  override suspend fun choose(call: ToolCall, option: PermissionOption) = Unit

  override suspend fun stop() = Unit
}

/** opencode's persona, as the image names it (jupyter_ai_acp_client 0.3.3 `acp_personas/opencode.py`). */
const val OPENCODE_PERSONA = "jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"

/**
 * This transcript, listing opencode's persona as its sole agent if it lists none: a Space's server always
 * lists the personas it loaded, and a panel with no agent to ask sends nothing.
 */
fun ChatTranscript.listingAnAgent(): ChatTranscript =
  if (agentPersonas.isNotEmpty()) this else copy(agentPersonas = listOf(AgentPersona(OPENCODE_PERSONA, "OpenCode")))
