package com.jupydroid.extensions.ai.ui

import com.jupydroid.extensions.ai.domain.AgentChoice
import com.jupydroid.extensions.ai.domain.AgentModel
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentPersona
import com.jupydroid.extensions.ai.domain.AgentPersonas
import com.jupydroid.extensions.ai.domain.AgentPick
import com.jupydroid.extensions.ai.domain.AgentSelection
import com.jupydroid.extensions.ai.domain.AgentSetting
import com.jupydroid.extensions.ai.domain.AgentSettingValue
import com.jupydroid.extensions.ai.domain.ChatTranscript
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which controls the Chat's toolbar offers, what the one chip they fold into says, and what it tells a screen
 * reader.
 */
class ChatControlTest {
  @Test
  fun theLabelNamesEveryControlAndTheValueItUses() {
    assertEquals(
      "Agent: OpenCode, Model: GPT-5 Nano, Session Mode: build",
      controlsLabel(listOf(AGENT, MODEL.copy(picked = "opencode/gpt-5-nano"), MODE)),
    )
  }

  @Test
  fun theOneChipNamesTheModelInUse() {
    assertEquals("Big Pickle", controlsName(listOf(AGENT, MODEL, MODE)))
  }

  @Test
  fun withNoModelTheOneChipNamesTheAgent() {
    assertEquals("OpenCode", controlsName(listOf(AGENT, MODE)))
  }

  @Test
  fun anAgentNotYetChosenIsNamedAsTheChipAsksForOne() {
    val unchosen = AGENT.copy(choice = AgentPersonas(current = null, offered = PERSONAS))

    assertEquals("Agent: Choose agent", controlsLabel(listOf(unchosen)))
  }

  @Test
  fun theControlsAreTheAgentThenItsModelThenEachSetting() {
    val transcript = ChatTranscript(
      agentPersonas = listOf(OPENCODE),
      agentModels = MODELS,
      agentSettings = listOf(MODE_SETTING),
    )

    assertEquals(listOf("Agent", "Model", "Session Mode"), chatControls(transcript, AgentPick()) {}.map { it.label })
  }

  @Test
  fun withNoAgentChosenTheOnlyControlIsTheAgent() {
    val transcript = ChatTranscript(agentPersonas = PERSONAS, agentModels = MODELS)

    assertEquals(listOf("Agent"), chatControls(transcript, AgentPick()) {}.map { it.label })
  }

  @Test
  fun aSettingOfferingNoValuesIsNoControl() {
    val bare = AgentSetting("__mode__", "Session Mode", "build", emptyList())
    val transcript = ChatTranscript(agentPersonas = listOf(OPENCODE), agentSettings = listOf(bare))

    assertEquals(listOf("Agent"), chatControls(transcript, AgentPick()) {}.map { it.label })
  }

  @Test
  fun aSettingPickedIsPickedForTheNextQuestion() {
    val transcript = ChatTranscript(
      agentPersonas = listOf(OPENCODE),
      agentSettings = listOf(MODE_SETTING),
    )
    var picked = AgentPick()

    chatControls(transcript, picked) { picked = it }.single { it.label == "Session Mode" }.onPick("plan")

    assertEquals(AgentSelection(settings = mapOf("__mode__" to "plan")), picked.selection)
  }

  private companion object {
    val OPENCODE = AgentPersona("opencode", "OpenCode")
    val PERSONAS = listOf(OPENCODE, AgentPersona("claude", "Claude"))
    val AGENT = control(
      "Agent",
      AgentPersonas("opencode", PERSONAS),
      ChoiceChip("chat-agent", searchable = false, unchosen = "Choose agent"),
    )
    val MODELS = AgentModels(
      "opencode/big-pickle",
      listOf(AgentModel("opencode/big-pickle", "Big Pickle"), AgentModel("opencode/gpt-5-nano", "GPT-5 Nano")),
    )
    val MODE_SETTING = AgentSetting("__mode__", "Session Mode", "build", listOf(AgentSettingValue("build", "build")))
    val MODEL = control("Model", MODELS, ChoiceChip("chat-model", searchable = true))
    val MODE = control("Session Mode", MODE_SETTING, ChoiceChip("chat-setting-__mode__", searchable = false))

    fun control(label: String, choice: AgentChoice, chip: ChoiceChip) = ChatControl(label, choice, null, chip) {}
  }
}
