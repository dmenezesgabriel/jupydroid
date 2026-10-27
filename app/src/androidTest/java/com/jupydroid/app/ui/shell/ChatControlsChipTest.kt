package com.jupydroid.app.ui.shell

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
 * The one chip the Chat's controls fold into where their whole names do not fit beside Send, as VS Code's
 * phone presenter folds its pickers into one (vscode@3f3380c
 * `src/vs/workbench/contrib/chat/browser/widget/input/chatPhoneInputPresenter.ts`, `_updateTrigger`), and the
 * one menu it opens. Laid out at a phone's 320 dp whatever the device's width.
 */
@RunWith(AndroidJUnit4::class)
class ChatControlsChipTest {
  @get:Rule val compose = createComposeRule()

  private val panel = UntitledChatPanel(compose, width = 320.dp)

  @Test
  fun atAPhonesWidthTheControlsFoldIntoOneChipBesideSend() {
    panel.show(SettableAgentChat(LONG))

    compose.onNodeWithTag(MODEL_CHIP).assertDoesNotExist()
    compose.onNodeWithTag(MODE_CHIP).assertDoesNotExist()
    val (controls, send, box) = listOf(CONTROLS, SEND, "chat-input-box").map(panel::bounds)
    assertTrue("controls $controls, send $send", controls.right <= send.left && controls.overlapsVertically(send))
    assertTrue("send $send in the box $box", send.right <= box.right)
  }

  /** VS Code's chip reads the model, and its aria label "<mode>, <model>" names every value. */
  @Test
  fun theOneChipNamesTheModelAndTellsAScreenReaderEveryValue() {
    panel.show(SettableAgentChat(LONG))

    compose.onNodeWithTag(CONTROLS).assertTextEquals(LONG_NAME)
    compose.onNode(hasContentDescription("Agent: OpenCode, Model: $LONG_NAME, Session Mode: build")).assertExists()
  }

  /** The model's section is last, as VS Code's sheet puts "Model" after "Agent Mode": it can be hundreds long. */
  @Test
  fun itsMenuHasASectionPerControlEachValueInUseChecked() {
    panel.show(SettableAgentChat(LONG))

    compose.onNodeWithTag(CONTROLS).performClick()

    val (agent, mode, model) = listOf("Agent", "Session Mode", "Model").map {
      inTheMenu(it).fetchSemanticsNode().boundsInRoot
    }
    assertTrue("agent $agent, mode $mode, model $model", agent.top < mode.top && mode.top < model.top)
    compose.onAllNodesWithContentDescription("In use").assertCountEquals(3)
  }

  @Test
  fun aPickInItsMenuIsOneTapAndTheNextQuestionIsAskedWithIt() {
    val chat = SettableAgentChat(LONG)
    panel.show(chat)

    compose.onNodeWithTag(CONTROLS).performClick()
    compose.onNodeWithText("plan").performClick()
    compose.onNodeWithTag("$CONTROLS-list").assertDoesNotExist()
    panel.send("Plan it first")

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked.isNotEmpty() }
    assertEquals(listOf(AgentSelection(settings = mapOf("__mode__" to "plan"))), chat.selections)
  }

  @Test
  fun itsSearchFiltersTheModelsAndLeavesTheOtherSections() {
    panel.show(SettableAgentChat(LONG))

    compose.onNodeWithTag(CONTROLS).performClick()
    compose.onNodeWithTag("$CONTROLS-search").performTextInput("nano")

    inTheMenu("GPT-5 Nano").assertExists()
    inTheMenu(LONG_NAME).assertDoesNotExist()
    inTheMenu("plan").assertExists()
  }

  private fun inTheMenu(text: String): SemanticsNodeInteraction =
    compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("$CONTROLS-list")))

  private fun Rect.overlapsVertically(other: Rect): Boolean = top < other.bottom && other.top < bottom

  private companion object {
    const val CONTROLS = "chat-controls"
    const val MODEL_CHIP = "chat-model"
    const val MODE_CHIP = "chat-setting-__mode__"
    const val SEND = "chat-send"
    const val LONG_NAME = "Default / OpenCode/A Very Long Model Name Indeed"

    val MODE = AgentSetting(
      id = "__mode__",
      name = "Session Mode",
      current = "build",
      offered = listOf(AgentSettingValue("build", "build"), AgentSettingValue("plan", "plan")),
    )

    val LONG = ChatTranscript(
      agentModels = AgentModels(
        "x/long",
        listOf(AgentModel("x/long", LONG_NAME), AgentModel("opencode/gpt-5-nano", "GPT-5 Nano")),
      ),
      agentSettings = listOf(MODE),
    )
  }
}
