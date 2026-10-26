package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ui.shell.UntitledChatPanel.Companion.TIMEOUT_MILLIS
import com.jupydroid.extensions.ai.domain.AgentModel
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentSelection
import com.jupydroid.extensions.ai.domain.ChatTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The model chip in the Chat input's toolbar, where jupyter-ai puts its Model control
 * (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`): whether there is one to press, and
 * what pressing it picks, which no JVM test can answer.
 */
@RunWith(AndroidJUnit4::class)
class ChatAgentControlsTest {
  @get:Rule val compose = createComposeRule()

  private val panel = UntitledChatPanel(compose)

  @Test
  fun theChipNamesTheModelTheAgentUses() {
    panel.show(SettableAgentChat(ChatTranscript(agentModels = MODELS)))

    compose.onNodeWithTag(MODEL_CHIP).assertTextEquals("Big Pickle")
  }

  /** jupyter-ai draws no Model control for a persona that offers none (`buildControls`). */
  @Test
  fun thereIsNoChipWhileTheAgentOffersNoModels() {
    panel.show(SettableAgentChat(ChatTranscript()))

    compose.onNodeWithTag(MODEL_CHIP).assertDoesNotExist()
  }

  @Test
  fun aModelPickedIsNamedAndTheNextQuestionIsAskedWithIt() {
    val chat = SettableAgentChat(ChatTranscript(agentModels = MODELS))
    panel.show(chat)

    compose.onNodeWithTag(MODEL_CHIP).performClick()
    compose.onNodeWithText("GPT-5 Nano").performClick()
    panel.send("Which model are you?")

    compose.onNodeWithTag(MODEL_CHIP).assertTextEquals("GPT-5 Nano")
    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked.isNotEmpty() }
    assertEquals(listOf(AgentSelection(model = "opencode/gpt-5-nano")), chat.selections)
  }

  @Test
  fun aQuestionAskedWithNothingPickedKeepsTheAgentsModel() {
    val chat = SettableAgentChat(ChatTranscript(agentModels = MODELS))
    panel.show(chat)

    panel.send("hello?")

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked.isNotEmpty() }
    assertEquals(listOf(AgentSelection()), chat.selections)
  }

  @Test
  fun theChipIsAFingerTall() {
    panel.show(SettableAgentChat(ChatTranscript(agentModels = MODELS)))

    val touch = compose.onNodeWithTag(MODEL_CHIP).fetchSemanticsNode().touchBoundsInRoot
    val finger = with(compose.density) { 48.dp.toPx() }
    assertTrue("the chip is touched within $touch, under 48 dp", touch.height >= finger)
  }

  private companion object {
    const val MODEL_CHIP = "chat-model"

    val MODELS = AgentModels(
      current = "opencode/big-pickle",
      offered = listOf(
        AgentModel("opencode/big-pickle", "Big Pickle"),
        AgentModel("opencode/gpt-5-nano", "GPT-5 Nano"),
      ),
    )
  }
}
