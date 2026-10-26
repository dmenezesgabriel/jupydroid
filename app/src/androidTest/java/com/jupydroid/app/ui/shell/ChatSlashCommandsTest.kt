package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.extensions.ai.domain.AgentCommand
import com.jupydroid.extensions.ai.domain.ChatTranscript
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The agent's slash commands listed over the Chat's field while what is typed is one word starting with
 * `/`, as jupyter-chat opens its command menu (jupyter-chat@2c224bc
 * `packages/jupyter-chat/src/components/input/use-chat-commands.tsx`): whether there is a row to press,
 * and what pressing it leaves in the field.
 */
@RunWith(AndroidJUnit4::class)
class ChatSlashCommandsTest {
  @get:Rule val compose = createComposeRule()

  private val panel = UntitledChatPanel(compose)

  @Test
  fun aSlashListsEachCommandWithWhatItDoes() {
    panel.show(SettableAgentChat(ChatTranscript(agentCommands = COMMANDS)))

    type("/")

    compose.onNodeWithText("/compact").assertExists()
    compose.onNodeWithText("compact the session").assertExists()
    compose.onNodeWithText("/init").assertExists()
  }

  @Test
  fun whatIsTypedNarrowsTheList() {
    panel.show(SettableAgentChat(ChatTranscript(agentCommands = COMMANDS)))

    type("/co")

    compose.onNodeWithText("/compact").assertExists()
    compose.onNodeWithText("/init").assertDoesNotExist()
  }

  @Test
  fun aSpaceClosesTheList() {
    panel.show(SettableAgentChat(ChatTranscript(agentCommands = COMMANDS)))

    type("/compact now")

    compose.onNodeWithTag(LIST).assertDoesNotExist()
  }

  /** jupyter-chat replaces the word with the command and a space (`spaceOnAccept`), and closes the menu. */
  @Test
  fun aCommandPickedIsPutInTheFieldWithASpace() {
    panel.show(SettableAgentChat(ChatTranscript(agentCommands = COMMANDS)))
    type("/co")

    compose.onNodeWithText("/compact").performClick()

    compose.onNodeWithTag("chat-input").assertTextEquals("/compact ")
    compose.onNodeWithTag(LIST).assertDoesNotExist()
  }

  /** What is typed after the pick goes after the space: the caret is left at the end. */
  @Test
  fun typingAfterAPickGoesAfterTheCommand() {
    panel.show(SettableAgentChat(ChatTranscript(agentCommands = COMMANDS)))
    type("/")
    compose.onNodeWithText("/init").performClick()

    compose.onNodeWithTag("chat-input").performTextInput("now")

    compose.onNodeWithTag("chat-input").assertTextEquals("/init now")
  }

  /**
   * jupyter-chat's menu is MUI's Autocomplete listbox, at most 40% of the viewport tall and scrolled
   * past that: opencode lists its skills among its commands, and one description ran to four lines on
   * SM-X216B, so a list drawn whole would push the field off the screen.
   */
  @Test
  fun aLongListLeavesTheFieldInViewAndScrollsToItsLast() {
    val many = (1..30).map { AgentCommand("/command-$it", "what command $it does") }
    panel.show(SettableAgentChat(ChatTranscript(agentCommands = many)))

    type("/")

    val list = panel.bounds(LIST)
    // Half the root rather than the 40% the list is held to: the root is the window less its insets.
    val root = compose.onRoot().fetchSemanticsNode().size.height
    assertTrue("the list is ${list.height} px of a $root px root", list.height <= root / 2f)
    compose.onNodeWithTag("chat-input").assertIsDisplayed()
    compose.onNodeWithText("/command-30").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun thereIsNoListWhileTheAgentOffersNoCommands() {
    panel.show(SettableAgentChat(ChatTranscript()))

    type("/")

    compose.onNodeWithTag(LIST).assertDoesNotExist()
  }

  private fun type(question: String) {
    compose.onNodeWithTag("chat-input").performTextReplacement(question)
  }

  private companion object {
    const val LIST = "chat-commands"

    val COMMANDS = listOf(AgentCommand("/compact", "compact the session"), AgentCommand("/init", null))
  }
}
