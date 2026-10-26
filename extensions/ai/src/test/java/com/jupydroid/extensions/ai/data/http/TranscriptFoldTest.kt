package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentModel
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentSetting
import com.jupydroid.extensions.ai.domain.AgentSettingValue
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatTranscript
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFoldTest {
  private val out = Channel<ChatTranscript>(Channel.UNLIMITED)
  private val fold = TranscriptFold(out, OPENCODE_PERSONA_ID)

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

  @Test
  fun anotherPersonasModelsAreNeverShown() {
    fold.opened(CHAT_ID, ChatTranscript())

    fold.stated(PersonaState(CHAT_ID, "jupyter-ai-personas::other::Persona", MODELS))

    assertEquals(AgentModels(), latest().agentModels)
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
    val MODELS = AgentModels("opencode/big-pickle", listOf(AgentModel("opencode/big-pickle", "Big Pickle")))
    val SETTINGS =
      listOf(AgentSetting("__mode__", "Session Mode", "build", listOf(AgentSettingValue("build", "build"))))
  }
}
