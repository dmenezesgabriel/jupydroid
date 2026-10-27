package com.jupydroid.app.ui.shell

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each answer says which agent wrote it, as jupyter-chat names a message's sender above it and stacks
 * the next of the same sender's under it with no name (jupyter-chat@2c224bc
 * `packages/jupyter-chat/src/components/messages/header.tsx`). Whether a name is drawn, and where, is
 * what no JVM test can answer.
 */
@RunWith(AndroidJUnit4::class)
class ChatSenderNameTest {
  @get:Rule val compose = createComposeRule()

  private val panel = UntitledChatPanel(compose)

  @Test
  fun anAgentsNameIsDrawnAboveItsAnswer() {
    panel.show(SettableAgentChat(transcript))

    // By its tag: the agent chip names the agent to ask, and can read the same.
    val name = compose.onNode(hasTestTag("chat-sender") and hasText("OpenCode"))
      .assertIsDisplayed().getBoundsInRoot()
    val answer = compose.onNodeWithText("first").assertIsDisplayed().getBoundsInRoot()
    assertTrue("the name ends at ${name.bottom}, the answer starts at ${answer.top}", name.bottom <= answer.top)
  }

  @Test
  fun aQuestionAndAStackedAnswerHaveNoNameAbove() {
    panel.show(SettableAgentChat(transcript))

    val names = compose.onAllNodesWithTag("chat-sender").fetchSemanticsNodes()
      .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } }
    assertEquals(listOf("OpenCode", "Claude"), names)
  }

  private val transcript = ChatTranscript(
    messages = listOf(
      ChatMessage(id = "q", body = "Which agent are you?", fromMe = true),
      ChatMessage(id = "a1", body = "first", fromMe = false, sender = OPENCODE),
      ChatMessage(id = "a2", body = "second", fromMe = false, sender = OPENCODE),
      ChatMessage(id = "c", body = "third", fromMe = false, sender = CLAUDE),
    ),
    senderNames = mapOf(OPENCODE to "OpenCode", CLAUDE to "Claude"),
  )

  private companion object {
    const val OPENCODE = "jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"
    const val CLAUDE = "jupyter-ai-personas::jupyter_ai_acp_client::ClaudeAcpPersona"
  }
}
