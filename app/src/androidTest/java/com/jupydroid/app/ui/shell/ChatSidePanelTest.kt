package com.jupydroid.app.ui.shell

import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.extension.api.SidePanelHost
import com.jupydroid.extensions.ai.AiExtension
import com.jupydroid.extensions.ai.data.fake.FakeAgentChat
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The AI extension's Chat panel as the shell draws it (ADR-0104): something to type a question in,
 * something to press to send it, and the agent's answer beneath it.
 *
 * The extension itself over its fake chat: what is in question is that the panel's controls are
 * there to press, which no JVM test can answer.
 */
@RunWith(AndroidJUnit4::class)
class ChatSidePanelTest {
  @get:Rule val compose = createComposeRule()

  private val chat = AiExtension(
    chatFor = { FakeAgentChat(replies = mapOf("What is a Space?" to "A Linux userland the app runs.")) },
  ).sidePanels.single()

  @Test
  fun theHostTitlesThePanelChat() {
    show()

    compose.onNodeWithText("Chat").assertIsDisplayed()
  }

  @Test
  fun aQuestionSentAppearsWithTheAgentsAnswerBeneathIt() {
    show()

    compose.onNodeWithTag("chat-input").performTextInput("What is a Space?")
    compose.onNodeWithTag("chat-send").performClick()

    // The keyboard left up takes most of a phone-sized window, and the panel follows the newest line,
    // so what fits in the rest depends on when it is read. A reader sees the exchange with it down.
    closeSoftKeyboard()

    compose.onNodeWithText("What is a Space?").assertIsDisplayed()
    compose.onNodeWithText("A Linux userland the app runs.").assertIsDisplayed()
  }

  @Test
  fun nothingIsSentUntilAQuestionIsWritten() {
    show()

    compose.onNodeWithTag("chat-send").assertIsNotEnabled()
  }

  @Test
  fun aLongAnswerIsFollowedToItsLastLine() {
    show(FakeAgentChat(replies = mapOf("Count" to LONG_ANSWER)))

    compose.onNodeWithTag("chat-input").performTextInput("Count")
    compose.onNodeWithTag("chat-send").performClick()

    compose.waitForIdle()
    assertEquals(scrollRange().maxValue(), scrollRange().value())
  }

  @Test
  fun aChatReadBackOpensAtItsNewestLine() {
    show(FakeAgentChat(history = listOf(ChatMessage(id = "old", body = LONG_ANSWER, fromMe = false))))

    compose.waitForIdle()
    assertEquals(scrollRange().maxValue(), scrollRange().value())
  }

  /** jupyter-chat stops following once the reader scrolls away from the end (`messages.tsx`). */
  @Test
  fun aReaderScrolledUpIsLeftWhereTheyAre() {
    val history = listOf(ChatMessage(id = "old", body = LONG_ANSWER, fromMe = false))
    show(FakeAgentChat(history = history, replies = mapOf("Again" to LONG_ANSWER)))
    compose.onNodeWithTag("chat-messages").performScrollToIndex(0)

    compose.onNodeWithTag("chat-input").performTextInput("Again")
    compose.onNodeWithTag("chat-send").performClick()

    compose.waitForIdle()
    assertEquals(0f, scrollRange().value())
  }

  /** How far the messages are scrolled, and how far they can be - what a screen reader reads. */
  private fun scrollRange(): ScrollAxisRange = compose.onNodeWithTag("chat-messages").fetchSemanticsNode()
    .config[SemanticsProperties.VerticalScrollAxisRange]

  private fun show(answering: AgentChat? = null) {
    val panel = answering?.let { fake -> AiExtension(chatFor = { fake }).sidePanels.single() } ?: chat
    compose.setContent {
      JupyterDroidTheme {
        ContributedSidePanel(contribution = panel, host = SidePanelHost(browsedDirectory = "", server = null))
      }
    }
  }

  private companion object {
    /** Taller than any screen the tests run on, one number per line. */
    val LONG_ANSWER = (1..200).joinToString("\n")
  }
}
