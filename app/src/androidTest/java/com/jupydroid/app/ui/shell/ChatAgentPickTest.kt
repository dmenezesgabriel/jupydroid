package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ui.shell.UntitledChatPanel.Companion.TIMEOUT_MILLIS
import com.jupydroid.extensions.ai.domain.AgentModel
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentPersona
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatTranscript
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The agent chip, first in the Chat input's toolbar where jupyter-ai puts its persona menu
 * (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`): whether there is one to press, what it
 * lists, and that nothing can be sent while no agent is chosen, which no JVM test can answer.
 */
@RunWith(AndroidJUnit4::class)
class ChatAgentPickTest {
  @get:Rule val compose = createComposeRule()

  private val panel = UntitledChatPanel(compose)

  @Test
  fun theAgentChipListsEveryAgent() {
    panel.show(SettableAgentChat(TWO_AGENTS))

    compose.onNodeWithTag(AGENT_CHIP).performClick()

    compose.onNodeWithText("Claude").assertIsDisplayed()
    compose.onNodeWithText("OpenCode").assertIsDisplayed()
  }

  /** With two agents and no question to go by, none is guessed, and a question to no one is never answered. */
  @Test
  fun withTwoAgentsAndNoneChosenNothingCanBeSent() {
    panel.show(SettableAgentChat(TWO_AGENTS))

    compose.onNodeWithTag("chat-input").performTextInput("Which agent are you?")

    compose.onNodeWithTag(AGENT_CHIP).assertTextEquals("Choose agent")
    compose.onNodeWithTag("chat-send").assertIsNotEnabled()
    compose.onNodeWithTag("chat-model").assertDoesNotExist()
  }

  @Test
  fun aPickedAgentIsSelectedAndAsked() {
    val chat = SettableAgentChat(TWO_AGENTS)
    panel.show(chat)

    compose.onNodeWithTag(AGENT_CHIP).performClick()
    compose.onNodeWithText("Claude").performClick()
    panel.send("Which agent are you?")

    compose.onNodeWithTag(AGENT_CHIP).assertTextEquals("Claude")
    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked.isNotEmpty() }
    assertEquals(listOf(CLAUDE), chat.askedOf)
    assertEquals(listOf(CLAUDE), chat.selected)
  }

  /** The `.chat` file keeps each question's `to_persona`, so a chat opened again asks whom it asked last. */
  @Test
  fun aChatOpenedAgainAsksTheAgentItsNewestQuestionWentTo() {
    val asked = TWO_AGENTS.copy(messages = listOf(ChatMessage("q", "earlier", fromMe = true, askedOf = CLAUDE)))
    val chat = SettableAgentChat(asked)
    panel.show(chat)

    compose.onNodeWithTag(AGENT_CHIP).assertTextEquals("Claude")
    compose.onNodeWithTag("chat-input").performTextInput("again?")
    compose.onNodeWithTag("chat-send").assertIsEnabled()
    compose.waitUntil(TIMEOUT_MILLIS) { chat.selected.isNotEmpty() }
    assertEquals(listOf(CLAUDE), chat.selected)
  }

  /** A model picked for one agent is not asked of another, which may not offer it. */
  @Test
  fun pickingAnotherAgentDropsTheModelPickedForTheLast() {
    val chat = SettableAgentChat(TWO_AGENTS.copy(agentModels = MODELS))
    panel.show(chat)
    compose.onNodeWithTag(AGENT_CHIP).performClick()
    compose.onNodeWithText("OpenCode").performClick()
    compose.onNodeWithTag("chat-model").performClick()
    compose.onNodeWithText("GPT-5 Nano").performClick()

    compose.onNodeWithTag(AGENT_CHIP).performClick()
    compose.onNodeWithText("Claude").performClick()
    panel.send("hello?")

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked.isNotEmpty() }
    assertEquals(null, chat.selections.single().model)
  }

  private companion object {
    const val AGENT_CHIP = "chat-agent"
    const val CLAUDE = "jupyter-ai-personas::jupyter_ai_acp_client::ClaudeAcpPersona"

    val TWO_AGENTS = ChatTranscript(
      agentPersonas = listOf(AgentPersona(CLAUDE, "Claude"), AgentPersona(OPENCODE_PERSONA, "OpenCode")),
    )

    val MODELS = AgentModels(
      current = "opencode/big-pickle",
      offered = listOf(
        AgentModel("opencode/big-pickle", "Big Pickle"),
        AgentModel("opencode/gpt-5-nano", "GPT-5 Nano"),
      ),
    )
  }
}
