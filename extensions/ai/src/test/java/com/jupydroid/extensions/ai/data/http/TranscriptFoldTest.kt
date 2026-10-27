package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentCommand
import com.jupydroid.extensions.ai.domain.AgentModel
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentPersona
import com.jupydroid.extensions.ai.domain.AgentSetting
import com.jupydroid.extensions.ai.domain.AgentSettingValue
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatTranscript
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFoldTest {
  private val out = Channel<ChatTranscript>(Channel.UNLIMITED)
  private val fold = TranscriptFold(out).apply { selected(OPENCODE_PERSONA_ID) }

  /** The catch-up can beat the chat's `connection` frame: the bus and the chat are two sockets. */
  @Test
  fun aStateThatArrivedBeforeTheChatSaidItsIdIsShownOnceItDoes() {
    fold.stated(PersonaState(CHAT_ID, OPENCODE_PERSONA_ID, MODELS))

    fold.opened(CHAT_ID, ChatTranscript())

    assertEquals(MODELS, latest().agentModels)
  }

  @Test
  fun aStateWithNoModelsLeavesThoseShown() {
    fold.opened(CHAT_ID, ChatTranscript())
    fold.stated(PersonaState(CHAT_ID, OPENCODE_PERSONA_ID, MODELS))

    fold.stated(PersonaState(CHAT_ID, OPENCODE_PERSONA_ID, models = null))

    assertEquals(MODELS, latest().agentModels)
  }

  /** `report_model_configuration` and `report_settings_configuration` each emit their own event. */
  @Test
  fun settingsAndModelsArriveApartAndAreBothShown() {
    fold.opened(CHAT_ID, ChatTranscript())
    fold.stated(PersonaState(CHAT_ID, OPENCODE_PERSONA_ID, MODELS))

    fold.stated(PersonaState(CHAT_ID, OPENCODE_PERSONA_ID, models = null, settings = SETTINGS))

    val shown = latest()
    assertEquals(MODELS, shown.agentModels)
    assertEquals(SETTINGS, shown.agentSettings)
  }

  /** `report_slash_commands` emits the commands alone, when the agent sends `available_commands_update`. */
  @Test
  fun commandsArriveApartAndAreShownBesideTheModels() {
    fold.opened(CHAT_ID, ChatTranscript())
    fold.stated(PersonaState(CHAT_ID, OPENCODE_PERSONA_ID, MODELS))

    fold.stated(PersonaState(CHAT_ID, OPENCODE_PERSONA_ID, models = null, commands = COMMANDS))

    val shown = latest()
    assertEquals(MODELS, shown.agentModels)
    assertEquals(COMMANDS, shown.agentCommands)
  }

  @Test
  fun anUnselectedPersonasModelsAreNotShown() {
    fold.opened(CHAT_ID, ChatTranscript())

    fold.stated(PersonaState(CHAT_ID, CLAUDE_PERSONA_ID, MODELS))

    assertEquals(AgentModels(), latest().agentModels)
  }

  /**
   * jupyter-ai's controls follow the selected persona (jupyter-ai-persona-manager@d5ed04b
   * `src/persona-controls.tsx`).
   */
  @Test
  fun selectingAnotherPersonaShowsWhatItLastSaid() {
    fold.opened(CHAT_ID, ChatTranscript())
    fold.stated(PersonaState(CHAT_ID, OPENCODE_PERSONA_ID, MODELS))
    fold.stated(PersonaState(CHAT_ID, CLAUDE_PERSONA_ID, CLAUDE_MODELS, commands = COMMANDS))

    fold.selected(CLAUDE_PERSONA_ID)

    val shown = latest()
    assertEquals(CLAUDE_MODELS, shown.agentModels)
    assertEquals(COMMANDS, shown.agentCommands)
  }

  @Test
  fun withNoPersonaSelectedNoStateIsShown() {
    val unselected = TranscriptFold(out)
    unselected.stated(PersonaState(CHAT_ID, OPENCODE_PERSONA_ID, MODELS))

    unselected.opened(CHAT_ID, ChatTranscript())

    assertEquals(AgentModels(), latest().agentModels)
  }

  /** The list, like a state, can arrive before the chat says its id. */
  @Test
  fun theChatsPersonaListIsInItsTranscript() {
    fold.listed(PersonaList(CHAT_ID, PERSONAS))

    fold.opened(CHAT_ID, ChatTranscript())

    assertEquals(PERSONAS, latest().agentPersonas)
  }

  @Test
  fun anotherChatsPersonaListIsNot() {
    fold.opened(CHAT_ID, ChatTranscript())

    fold.listed(PersonaList("another-chat", PERSONAS))

    assertEquals(emptyList<AgentPersona>(), latest().agentPersonas)
  }

  @Test
  fun aMessageKeepsThePersonaList() {
    fold.opened(CHAT_ID, ChatTranscript())
    fold.listed(PersonaList(CHAT_ID, PERSONAS))

    fold.changed { it.with(ChatMessage("q", "hello?", fromMe = true)) }

    assertEquals(PERSONAS, latest().agentPersonas)
  }

  @Test
  fun aMessageKeepsTheModelsShown() {
    fold.opened(CHAT_ID, ChatTranscript())
    fold.stated(PersonaState(CHAT_ID, OPENCODE_PERSONA_ID, MODELS))

    fold.changed { it.with(ChatMessage("q", "hello?", fromMe = true)) }

    val shown = latest()
    assertEquals(MODELS, shown.agentModels)
    assertEquals(listOf("hello?"), shown.messages.map { it.body })
  }

  private fun latest(): ChatTranscript {
    var last = out.tryReceive().getOrThrow()
    while (true) last = out.tryReceive().getOrNull() ?: return last
  }

  private companion object {
    const val CLAUDE_PERSONA_ID = "jupyter-ai-personas::jupyter_ai_acp_client::ClaudeAcpPersona"
    val MODELS = AgentModels("opencode/big-pickle", listOf(AgentModel("opencode/big-pickle", "Big Pickle")))
    val CLAUDE_MODELS = AgentModels("default", listOf(AgentModel("default", "Default (recommended)")))
    val PERSONAS = listOf(AgentPersona(CLAUDE_PERSONA_ID, "Claude"), AgentPersona(OPENCODE_PERSONA_ID, "OpenCode"))
    val COMMANDS = listOf(AgentCommand("/compact", "compact the session"))
    val SETTINGS =
      listOf(AgentSetting("__mode__", "Session Mode", "build", listOf(AgentSettingValue("build", "build"))))
  }
}
