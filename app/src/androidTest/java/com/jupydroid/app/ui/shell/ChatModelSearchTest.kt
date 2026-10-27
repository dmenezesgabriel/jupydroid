package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The model chip's list when the agent offers hundreds of models: opencode signed in to OpenRouter
 * offered 519 on SM-X216B. jupyter-ai and VS Code both open theirs on a search field
 * (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx` `ControlMenu`; vscode@3f3380c
 * `modelPickerWidget.ts`, "Search models"), and jupyter-ai holds it to 60% of the viewport
 * (`style/base.css`). Whether there is a field to type in, and how tall the list is, no JVM test can see.
 */
@RunWith(AndroidJUnit4::class)
class ChatModelSearchTest {
  @get:Rule val compose = createComposeRule()

  // At the tablet's width, where each control is its own chip: at a phone's they fold into one.
  private val panel = UntitledChatPanel(compose, width = 800.dp)

  private fun inList(name: String) = hasText(name) and hasAnyAncestor(hasTestTag(MODEL_LIST))

  private fun openModels(
    models: AgentModels = MODELS,
    chat: SettableAgentChat = SettableAgentChat(transcript(models)),
  ) {
    panel.show(chat)
    compose.onNodeWithTag(MODEL_CHIP).performClick()
  }

  @Test
  fun theModelListOpensWithTheSearchFieldFocused() {
    openModels()

    compose.onNodeWithTag(SEARCH).assertIsFocused()
  }

  @Test
  fun whatIsTypedNarrowsTheListToTheModelsThatHoldIt() {
    openModels()

    compose.onNodeWithTag(SEARCH).performTextInput("north")

    compose.onNode(inList("North Mini Code (free)")).assertExists()
    compose.onNode(inList("GPT-5 Nano")).assertDoesNotExist()
    compose.onNode(inList("Big Pickle")).assertDoesNotExist()
  }

  @Test
  fun aModelFoundBySearchIsTheOneTheNextQuestionIsAskedWith() {
    val chat = SettableAgentChat(transcript(MODELS))
    openModels(chat = chat)

    compose.onNodeWithTag(SEARCH).performTextInput("cohere")
    compose.onNode(inList("North Mini Code (free)")).performClick()
    panel.send("Which model are you?")

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked.isNotEmpty() }
    assertEquals(listOf(AgentSelection(model = "openrouter/cohere/north-mini-code:free")), chat.selections)
  }

  @Test
  fun aSearchNothingMatchesSaysSo() {
    openModels()

    compose.onNodeWithTag(SEARCH).performTextInput("zzz")

    compose.onNodeWithText("No matches").assertExists()
  }

  /** jupyter-ai heads its list with the value in use (`defaultChoiceLabel`), so it is seen without a scroll. */
  @Test
  fun theModelInUseIsTheListsFirstRow() {
    openModels()

    val first = compose.onNode(inList("Big Pickle")).fetchSemanticsNode().boundsInRoot
    val second = compose.onNode(inList("GPT-5 Nano")).fetchSemanticsNode().boundsInRoot
    assertTrue("Big Pickle at $first, GPT-5 Nano at $second", first.bottom <= second.top)
  }

  @Test
  fun hundredsOfModelsAreListedInAtMostThreeFifthsOfTheWindow() {
    val many = AgentModels("m/0", List(HUNDREDS) { AgentModel("m/$it", "Model $it") })
    openModels(many)

    val list = compose.onNodeWithTag(MODEL_LIST).fetchSemanticsNode().size.height
    // Two thirds of the panel's root rather than the three fifths of the window the list is held to:
    // the root is the window less its insets. Unbounded, the menu took all of it.
    val root = compose.onAllNodes(isRoot())[0].fetchSemanticsNode().size.height
    assertTrue("the list is $list px of a $root px root", list <= root * 2 / 3)
  }

  /** VS Code's mode picker has no search: two or three values read faster than they are typed. */
  @Test
  fun theModeListHasNoSearchField() {
    panel.show(SettableAgentChat(transcript(MODELS)))

    compose.onNodeWithTag(MODE_CHIP).performClick()

    compose.onAllNodesWithTag(SEARCH).assertCountEquals(0)
  }

  private fun transcript(models: AgentModels) = ChatTranscript(agentModels = models, agentSettings = listOf(MODE))

  private companion object {
    const val MODEL_CHIP = "chat-model"
    const val MODEL_LIST = "chat-model-list"
    const val SEARCH = "chat-model-search"
    const val MODE_CHIP = "chat-setting-__mode__"
    const val HUNDREDS = 500

    val MODE = AgentSetting(
      id = "__mode__",
      name = "Session Mode",
      current = "build",
      offered = listOf(AgentSettingValue("build", "build"), AgentSettingValue("plan", "plan")),
    )

    val MODELS = AgentModels(
      current = "opencode/big-pickle",
      offered = listOf(
        AgentModel("opencode/gpt-5-nano", "GPT-5 Nano"),
        AgentModel("opencode/big-pickle", "Big Pickle"),
        AgentModel("openrouter/cohere/north-mini-code:free", "North Mini Code (free)"),
      ),
    )
  }
}
