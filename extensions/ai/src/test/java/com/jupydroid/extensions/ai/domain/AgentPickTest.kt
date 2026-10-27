package com.jupydroid.extensions.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentPickTest {
  private val twoAgents =
    ChatTranscript(agentPersonas = listOf(AgentPersona("claude", "Claude"), AgentPersona("opencode", "OpenCode")))

  @Test
  fun theAgentPickedIsTheOneAsked() {
    assertEquals("claude", AgentPick().withAgent("claude").agentIn(twoAgents))
  }

  @Test
  fun withNothingPickedTheChatSaysWhoIsAsked() {
    assertNull(AgentPick().agentIn(twoAgents))
  }

  /** A model picked for one agent may not be offered by the next. */
  @Test
  fun pickingAnAgentDropsWhatWasPickedForTheLast() {
    val picked = AgentPick(agent = "opencode", selection = AgentSelection(model = "opencode/gpt-5-nano"))

    assertEquals(AgentSelection(), picked.withAgent("claude").selection)
  }
}
