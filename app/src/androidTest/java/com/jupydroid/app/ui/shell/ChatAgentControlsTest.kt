package com.jupydroid.app.ui.shell

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ui.shell.UntitledChatPanel.Companion.TIMEOUT_MILLIS
import com.jupydroid.extensions.ai.domain.AgentModel
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentSelection
import com.jupydroid.extensions.ai.domain.AgentSetting
import com.jupydroid.extensions.ai.domain.AgentSettingValue
import com.jupydroid.extensions.ai.domain.ChatTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The model chip and a chip per general setting in the Chat input's toolbar, where jupyter-ai puts its
 * persona controls (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`): whether there is
 * one to press, what pressing it picks, and whether a name is whole while the row has room, which no JVM
 * test can answer.
 */
@RunWith(AndroidJUnit4::class)
class ChatAgentControlsTest {
  @get:Rule val compose = createComposeRule()

  // At the tablet's width, where each control is its own chip: at a phone's they fold into one.
  private val panel = UntitledChatPanel(compose, width = 800.dp)

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

  @Test
  fun theModeChipNamesTheModeTheAgentUsesRightOfTheModelChip() {
    panel.show(SettableAgentChat(ChatTranscript(agentModels = MODELS, agentSettings = listOf(MODE))))

    compose.onNodeWithTag(MODE_CHIP).assertTextEquals("build")
    assertTrue(panel.bounds(MODEL_CHIP).right <= panel.bounds(MODE_CHIP).left)
  }

  @Test
  fun thereIsNoChipForASettingThatOffersNoValues() {
    val bare = MODE.copy(offered = emptyList())
    panel.show(SettableAgentChat(ChatTranscript(agentModels = MODELS, agentSettings = listOf(bare))))

    compose.onNodeWithTag(MODE_CHIP).assertDoesNotExist()
  }

  @Test
  fun aModePickedIsNamedAndTheNextQuestionIsAskedWithIt() {
    val chat = SettableAgentChat(ChatTranscript(agentModels = MODELS, agentSettings = listOf(MODE)))
    panel.show(chat)

    compose.onNodeWithTag(MODE_CHIP).performClick()
    compose.onNodeWithText("plan").performClick()
    panel.send("Plan it first")

    compose.onNodeWithTag(MODE_CHIP).assertTextEquals("plan")
    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked.isNotEmpty() }
    assertEquals(listOf(AgentSelection(settings = mapOf("__mode__" to "plan"))), chat.selections)
  }

  /** opencode lists `effort` only for a model with variants (`buildEffortSelectOption`): a pick it dropped is not sent. */
  @Test
  fun aPickForASettingTheAgentNoLongerListsIsNotSent() {
    val chat = SettableAgentChat(ChatTranscript(agentModels = MODELS, agentSettings = listOf(MODE)))
    panel.show(chat)

    compose.onNodeWithTag(MODE_CHIP).performClick()
    compose.onNodeWithText("plan").performClick()
    chat.shown.value = ChatTranscript(agentModels = MODELS)
    panel.send("hello?")

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked.isNotEmpty() }
    assertEquals(listOf(AgentSelection()), chat.selections)
  }

  /**
   * At the tablet's 800 dp, four chips, as many as Claude's agent, model, mode and effort, leave room in the
   * row, so none is cut: each was held to a quarter of it, and "Default (recommended)" was drawn as
   * "Default (recomm…" beside the empty rest.
   */
  @Test
  fun aChipsNameIsWholeWhileTheRowHasRoom() {
    val claude = AgentModels("default", listOf(AgentModel("default", "Default (recommended)")))
    val effort = MODE.copy(id = "effort", name = "Effort", current = "default", offered = TWO_VALUES)
    val thinking = MODE.copy(id = "thinking", name = "Thinking", current = "default", offered = TWO_VALUES)
    panel.show(SettableAgentChat(ChatTranscript(agentModels = claude, agentSettings = listOf(MODE, effort, thinking))))

    assertFalse("the model's name is cut", compose.onNodeWithText(claude.offered.single().name).isCut())
  }

  /**
   * Whether the text in this node is drawn ending in an ellipsis. Not `hasVisualOverflow`: a weighted text
   * laid out against the whole row reports an overflow while it is drawn whole.
   */
  private fun SemanticsNodeInteraction.isCut(): Boolean {
    val layouts = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    return layouts.single().isLineEllipsized(0)
  }

  private companion object {
    const val MODEL_CHIP = "chat-model"
    const val MODE_CHIP = "chat-setting-__mode__"

    val MODE = AgentSetting(
      id = "__mode__",
      name = "Session Mode",
      current = "build",
      offered = listOf(AgentSettingValue("build", "build"), AgentSettingValue("plan", "plan")),
    )

    val TWO_VALUES = listOf(AgentSettingValue("default", "Default"), AgentSettingValue("high", "High"))

    val MODELS = AgentModels(
      current = "opencode/big-pickle",
      offered = listOf(
        AgentModel("opencode/big-pickle", "Big Pickle"),
        AgentModel("opencode/gpt-5-nano", "GPT-5 Nano"),
      ),
    )
  }
}
