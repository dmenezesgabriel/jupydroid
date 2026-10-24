package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ui.shell.UntitledChatPanel.Companion.TIMEOUT_MILLIS
import com.jupydroid.extensions.ai.data.fake.FakeQueuedQuestionsStore
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatTranscript
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The questions a chat holds waiting are kept for it (`QueuedQuestionsStore`), so a chat opened again,
 * as after the app was killed, still holds them and asks them in turn.
 */
@RunWith(AndroidJUnit4::class)
class ChatKeptQuestionsTest {
  @get:Rule val compose = createComposeRule()

  private val panel = UntitledChatPanel(compose)
  private val kept = FakeQueuedQuestionsStore()
  private val untitled = ChatFile("untitled.chat")

  @Test
  fun aQuestionKeptForTheChatIsAskedOnOpeningWhenNoReplyArrives() {
    runBlocking { kept.write(untitled, listOf("What is 2+2?")) }
    val chat = SettableAgentChat(ChatTranscript())

    panel.show(chat, kept)

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked == listOf("What is 2+2?") }
    compose.waitUntil(TIMEOUT_MILLIS) { keptFor(untitled).isEmpty() }
  }

  @Test
  fun aQuestionKeptForTheChatWaitsAsAPendingRowWhileAReplyArrives() {
    runBlocking { kept.write(untitled, listOf("What is 2+2?")) }
    val chat = SettableAgentChat(ChatTranscript(agentWriting = true))

    panel.show(chat, kept)

    compose.onNodeWithText("1 queued").assertIsDisplayed()
    assertTrue("asked ${chat.asked} while the agent wrote", chat.asked.isEmpty())
  }

  @Test
  fun aQuestionSentWhileAReplyArrivesIsKeptForTheChat() {
    panel.show(SettableAgentChat(ChatTranscript(agentWriting = true)), kept)

    panel.send("What is 2+2?")

    compose.waitUntil(TIMEOUT_MILLIS) { keptFor(untitled) == listOf("What is 2+2?") }
  }

  @Test
  fun aQuestionTakenOffWithItsCrossIsNoLongerKept() {
    panel.show(SettableAgentChat(ChatTranscript(agentWriting = true)), kept)
    panel.send("first")
    panel.send("second")

    // The rows are oldest first, so the first × is the first question's.
    compose.onAllNodes(hasContentDescription("Remove from queue"))[0].performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { keptFor(untitled) == listOf("second") }
  }

  private fun keptFor(chat: ChatFile): List<String> = runBlocking { kept.read(chat) }
}
