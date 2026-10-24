package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ui.shell.UntitledChatPanel.Companion.TIMEOUT_MILLIS
import com.jupydroid.extensions.ai.domain.ChatTranscript
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The questions the Chat panel holds while the agent writes (`QuestionQueue`): the box above the input
 * that draws them, and what each pending row offers to press, which no JVM test can answer.
 */
@RunWith(AndroidJUnit4::class)
class ChatQueuedQuestionsTest {
  @get:Rule val compose = createComposeRule()

  private val panel = UntitledChatPanel(compose)

  @Test
  fun aQuestionSentWhileNothingWritesIsAskedAtOnce() {
    val chat = SettableAgentChat(ChatTranscript())
    panel.show(chat)

    panel.send("What is 2+2?")

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked == listOf("What is 2+2?") }
    compose.onNodeWithTag("chat-queued").assertDoesNotExist()
  }

  /**
   * Asked now, it would cut the reply short (`QuestionQueue`). It waits in a box above the input, as
   * jupyter-chat-components draws its queue (jupyter-chat-components@992c6fb `src/components/message-queue.tsx`).
   */
  @Test
  fun aQuestionSentWhileTheAgentWritesWaitsAsAPendingRow() {
    val chat = SettableAgentChat(ChatTranscript(agentWriting = true))
    panel.show(chat)

    panel.send("What is 2+2?")

    compose.onNodeWithText("1 queued").assertIsDisplayed()
    compose.onNode(hasText("What is 2+2?") and hasAnyAncestor(hasTestTag("chat-queued"))).assertIsDisplayed()
    compose.onNodeWithTag("chat-stop").assertIsDisplayed()
    assertTrue("asked ${chat.asked} while the agent wrote", chat.asked.isEmpty())
    assertTrue(
      "the queue is not above the input",
      panel.bounds("chat-queued").bottom <= panel.bounds("chat-input-box").top,
    )
  }

  @Test
  fun theOldestPendingQuestionIsAskedWhenTheReplyEnds() {
    val chat = SettableAgentChat(ChatTranscript(agentWriting = true))
    panel.show(chat)
    panel.send("first")
    panel.send("second")

    chat.shown.value = ChatTranscript()

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked == listOf("first") }
    compose.onNodeWithText("1 queued").assertIsDisplayed()
  }

  /**
   * Its × takes a pending question off the queue, as the remove button does in
   * jupyter-chat-components@992c6fb `src/components/message-queue.tsx`.
   */
  @Test
  fun aPendingRowsCrossRemovesOnlyThatQuestionAndItIsNeverAsked() {
    val chat = SettableAgentChat(ChatTranscript(agentWriting = true))
    panel.show(chat)
    panel.send("first")
    panel.send("second")

    removeQueued("second")
    compose.onNodeWithText("1 queued").assertIsDisplayed()
    compose.onNode(hasText("first") and hasAnyAncestor(hasTestTag("chat-queued"))).assertIsDisplayed()
    removeQueued("first")
    compose.onNodeWithTag("chat-queued").assertDoesNotExist()
    chat.shown.value = ChatTranscript()

    compose.waitForIdle()
    assertTrue("asked ${chat.asked} after both were removed", chat.asked.isEmpty())
  }

  @Test
  fun aPendingRowsCrossIsAFingerWide() {
    panel.show(SettableAgentChat(ChatTranscript(agentWriting = true)))
    panel.send("first")

    val touch = compose.onNodeWithContentDescription(REMOVE_QUEUED).fetchSemanticsNode().touchBoundsInRoot
    val finger = with(compose.density) { 48.dp.toPx() }
    assertTrue("the × is touched within $touch, under 48 dp", touch.width >= finger && touch.height >= finger)
  }

  /**
   * Its pencil opens the question in a field in its place, and Save puts what was written there, as the
   * edit button does in jupyter-chat-components@992c6fb `src/components/message-queue.tsx`.
   */
  @Test
  fun aPendingRowsPencilEditsItInPlaceAndTheEditIsAsked() {
    val chat = SettableAgentChat(ChatTranscript(agentWriting = true))
    panel.show(chat)
    panel.send("first")

    editQueued("first", into = "edited")
    compose.onNode(hasText("edited") and hasAnyAncestor(hasTestTag("chat-queued-row"))).assertIsDisplayed()
    chat.shown.value = ChatTranscript()

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked == listOf("edited") }
  }

  /** What is asked is what was saved, so a question is held while its edit is open (`QuestionQueue.held`). */
  @Test
  fun aQuestionBeingEditedIsNotAskedWhenTheReplyEnds() {
    val chat = SettableAgentChat(ChatTranscript(agentWriting = true))
    panel.show(chat)
    panel.send("first")
    queuedButton("first", EDIT_QUEUED).performClick()

    chat.shown.value = ChatTranscript()
    compose.waitForIdle()
    assertTrue("asked ${chat.asked} while it was edited", chat.asked.isEmpty())
    compose.onNodeWithContentDescription(SAVE_QUEUED).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked == listOf("first") }
  }

  /** The reference hides the × while a row is edited; Save is off while blank, as Send is. */
  @Test
  fun anEditedRowHidesItsCrossAndCannotBeSavedBlank() {
    panel.show(SettableAgentChat(ChatTranscript(agentWriting = true)))
    panel.send("first")
    queuedButton("first", EDIT_QUEUED).performClick()

    compose.onNodeWithContentDescription(REMOVE_QUEUED).assertDoesNotExist()
    compose.onNodeWithTag("chat-queued-edit").performTextClearance()
    compose.onNodeWithContentDescription(SAVE_QUEUED).assertIsNotEnabled()
  }

  @Test
  fun aPendingRowsPencilIsAFingerWide() {
    panel.show(SettableAgentChat(ChatTranscript(agentWriting = true)))
    panel.send("first")

    val touch = compose.onNodeWithContentDescription(EDIT_QUEUED).fetchSemanticsNode().touchBoundsInRoot
    val finger = with(compose.density) { 48.dp.toPx() }
    assertTrue("the pencil is touched within $touch, under 48 dp", touch.width >= finger && touch.height >= finger)
  }

  /** Taps the × of the pending row that holds [question]. */
  private fun removeQueued(question: String) {
    queuedButton(question, REMOVE_QUEUED).performClick()
  }

  /** Writes [into] over the pending [question] with its pencil, and saves it. */
  private fun editQueued(question: String, into: String) {
    queuedButton(question, EDIT_QUEUED).performClick()
    compose.onNodeWithTag("chat-queued-edit").performTextReplacement(into)
    compose.onNodeWithContentDescription(SAVE_QUEUED).performClick()
  }

  /** The button described [description] on the pending row that holds [question]. */
  private fun queuedButton(question: String, description: String): SemanticsNodeInteraction {
    val row = hasTestTag("chat-queued-row") and hasAnyDescendant(hasText(question))
    return compose.onNode(hasContentDescription(description) and hasAnyAncestor(row))
  }

  private companion object {
    const val REMOVE_QUEUED = "Remove from queue"
    const val EDIT_QUEUED = "Edit message"
    const val SAVE_QUEUED = "Save"
  }
}
