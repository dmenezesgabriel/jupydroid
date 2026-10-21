package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.extension.api.SidePanelHost
import com.jupydroid.extensions.ai.AiExtension
import com.jupydroid.extensions.ai.data.fake.FakeAgentChat
import com.jupydroid.extensions.ai.data.fake.FakeChatFiles
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatTranscript
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import com.jupydroid.extensions.ai.domain.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  private val chat =
    openOnUntitled(FakeAgentChat(replies = mapOf("What is a Space?" to "A Linux userland the app runs.")))

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

  /**
   * One bordered box holds the field and, in a row beneath it, Send, the way jupyter-chat draws its
   * input (jupyter-chat@2c224bc `packages/jupyter-chat/src/components/input/chat-input.tsx`).
   */
  @Test
  fun sendSitsInsideTheInputsBoxBeneathTheField() {
    show()

    val box = bounds("chat-input-box")
    val send = bounds("chat-send")
    assertTrue("Send $send is not inside the input's box $box", box.intersect(send) == send)
    assertTrue("Send $send is not beneath the field ${bounds("chat-input")}", send.top >= bounds("chat-input").bottom)
  }

  @Test
  fun sendIsPaddedAlikeFromTheBoxsRightAndBottom() {
    show()

    val box = bounds("chat-input-box")
    val send = bounds("chat-send")
    assertEquals(box.right - send.right, box.bottom - send.bottom, 1f)
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

  /**
   * A call's message keeps its id and its empty body while its calls change, so a call that comes to
   * ask grows its row with nothing else to follow it by: on the tablet its options once sat below the
   * field, out of reach.
   */
  @Test
  fun aCallThatComesToAskIsFollowedToItsOptions() {
    val old = ChatMessage(id = "old", body = LONG_ANSWER, fromMe = false)
    val running = awaiting().copy(permission = null)
    val chat = SettableAgentChat(ChatTranscript(listOf(old, callsMessage(running))))
    show(chat)
    compose.waitForIdle()

    chat.shown.value = ChatTranscript(listOf(old, callsMessage(awaiting())))

    compose.waitForIdle()
    assertEquals(scrollRange().maxValue(), scrollRange().value())
  }

  /**
   * Only the reader's finger leaves the end. On SM-X216B the keyboard coming up for a fourth question
   * moved the list back an item with no drag, and the answer then streamed out of sight. Under
   * edge-to-edge a test host cannot make that layout move, so a scroll to the top that no finger made
   * stands in for it.
   */
  @Test
  fun aMoveNoDragMadeDoesNotEndTheFollowing() {
    val history = listOf(ChatMessage(id = "old", body = LONG_ANSWER, fromMe = false))
    show(FakeAgentChat(history = history, replies = mapOf("Again" to LONG_ANSWER)))
    compose.onNodeWithTag("chat-messages").performScrollToIndex(0)

    compose.onNodeWithTag("chat-input").performTextInput("Again")
    compose.onNodeWithTag("chat-send").performClick()

    compose.waitForIdle()
    assertEquals(scrollRange().maxValue(), scrollRange().value())
  }

  /** jupyter-chat stops following once the reader scrolls away from the end (`messages.tsx`). */
  @Test
  fun aReaderScrolledUpIsLeftWhereTheyAre() {
    val history = listOf(ChatMessage(id = "old", body = LONG_ANSWER, fromMe = false))
    show(FakeAgentChat(history = history, replies = mapOf("Again" to LONG_ANSWER)))
    compose.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
    compose.waitForIdle()
    val readerLeftItAt = scrollRange().value()

    compose.onNodeWithTag("chat-input").performTextInput("Again")
    compose.onNodeWithTag("chat-send").performClick()

    compose.waitForIdle()
    assertEquals(readerLeftItAt, scrollRange().value())
  }

  /**
   * A message that holds only an agent's tool calls has no text, and is drawn as one row per call, each
   * with its mark and title, between the question and the answer around it.
   */
  @Test
  fun eachToolCallIsARowWithItsMarkAndTitle() {
    val read = ToolCall(id = "c1", title = "probe.txt", status = ToolCallStatus.Completed)
    val run = ToolCall(id = "c2", title = "echo probe", status = ToolCallStatus.Failed)
    val calls = ChatMessage(id = "t", body = "", fromMe = false, toolCalls = listOf(read, run))
    show(FakeAgentChat(history = listOf(ChatMessage(id = "q", body = "read it", fromMe = true), calls)))

    compose.onNodeWithText("probe.txt").assertIsDisplayed()
    compose.onNodeWithText("\u2713").assertIsDisplayed()
    compose.onNodeWithText("echo probe").assertIsDisplayed()
    compose.onNodeWithText("\u2717").assertIsDisplayed()
    val firstRow = compose.onAllNodesWithTag("chat-tool-call")[0].fetchSemanticsNode().boundsInRoot
    assertTrue(firstRow.top >= compose.onNodeWithText("read it").fetchSemanticsNode().boundsInRoot.bottom)
  }

  /**
   * A call awaiting the user offers the agent's own options under "Allow?", and pressing one answers
   * it: the row then names the option chosen, as jupyter-chat-components@992c6fb draws it
   * (`src/components/grouped-tool-calls.tsx`, `PermissionButtons`, `PermissionLabel`).
   */
  @Test
  fun aCallAwaitingApprovalOffersTheAgentsOptionsAndTakesOne() {
    show(FakeAgentChat(history = listOf(callsMessage(awaiting()))))

    compose.onNodeWithText("Allow?", substring = true).assertIsDisplayed()
    listOf("Always allow", "Reject").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    compose.onNodeWithText("Allow once").performClick()

    compose.onNodeWithText("Allow?", substring = true).assertDoesNotExist()
    compose.onNodeWithText("- Allow once").assertIsDisplayed()
  }

  /** jupyter-chat enables Stop only while a bot writes (jupyter-chat@2c224bc `stop-button.tsx`). */
  @Test
  fun stopIsNotOfferedWhileNoAgentWrites() {
    show()

    compose.onNodeWithTag("chat-stop").assertDoesNotExist()
  }

  /** Stop sits left of Send, at 90 to Send's 100 in jupyter-chat's registration (`toolbar-registry.tsx`). */
  @Test
  fun stopBesideSendStopsTheReplyAndGoesAway() {
    show(FakeAgentChat(writing = true))

    val stop = bounds("chat-stop")
    val send = bounds("chat-send")
    assertTrue("Stop $stop is not left of Send $send", stop.right <= send.left)
    assertEquals(send.center.y, stop.center.y, 1f)
    compose.onNodeWithTag("chat-stop").performClick()

    compose.onNodeWithTag("chat-stop").assertDoesNotExist()
  }

  @Test
  fun aQuestionSentWhileNothingWritesIsAskedAtOnce() {
    val chat = SettableAgentChat(ChatTranscript())
    show(chat)

    send("What is 2+2?")

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
    show(chat)

    send("What is 2+2?")

    compose.onNodeWithText("1 queued").assertIsDisplayed()
    compose.onNode(hasText("What is 2+2?") and hasAnyAncestor(hasTestTag("chat-queued"))).assertIsDisplayed()
    compose.onNodeWithTag("chat-stop").assertIsDisplayed()
    assertTrue("asked ${chat.asked} while the agent wrote", chat.asked.isEmpty())
    assertTrue("the queue is not above the input", bounds("chat-queued").bottom <= bounds("chat-input-box").top)
  }

  @Test
  fun theOldestPendingQuestionIsAskedWhenTheReplyEnds() {
    val chat = SettableAgentChat(ChatTranscript(agentWriting = true))
    show(chat)
    send("first")
    send("second")

    chat.shown.value = ChatTranscript()

    compose.waitUntil(TIMEOUT_MILLIS) { chat.asked == listOf("first") }
    compose.onNodeWithText("1 queued").assertIsDisplayed()
  }

  /** A rejected call is a cross, whatever the agent then says of its status (`ToolCallRow`'s `isRejected`). */
  @Test
  fun aRejectedCallIsACrossBesideTheOptionChosen() {
    val rejected = awaiting().copy(status = ToolCallStatus.Completed, permission = ToolCallPermission.Resolved(REJECT))
    show(FakeAgentChat(history = listOf(callsMessage(rejected))))

    compose.onNodeWithText("\u2717").assertIsDisplayed()
    compose.onNodeWithText("- Reject").assertIsDisplayed()
  }

  /** A chat whose server stopped says so while it opens again, and is read back from the new one. */
  @Test
  fun aDroppedChatSaysItIsReconnectingThenReadsTheChatBack() {
    val asked = ChatTranscript(listOf(ChatMessage(id = "q", body = "before the drop", fromMe = true)))
    val chat = DroppingAgentChat(before = asked, after = asked.with(ChatMessage("a", "read back", fromMe = false)))
    show(chat)
    compose.onNodeWithTag("chat-input").performTextInput("again")

    compose.onNodeWithText("Reconnecting: ${DroppingAgentChat.DROPPED}").assertIsDisplayed()
    compose.onNodeWithText("before the drop").assertIsDisplayed()
    compose.onNodeWithTag("chat-send").assertIsNotEnabled()

    chat.reopen()

    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText("read back").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("chat-send").assertIsEnabled()
  }

  /** The question's keys are the window's row, as a cell's are, though Chat sits in a side panel. */
  @Test
  fun aFocusedQuestionPublishesItsKeysAcrossTheWindow() {
    showBesideAPane()

    compose.onNodeWithTag("chat-input").performClick()

    assertEquals(compose.onRoot().getBoundsInRoot().width, compose.onNodeWithTag(CHAT_KEY_ROW).getBoundsInRoot().width)
  }

  @Test
  fun theRowsKeysTypeIntoTheQuestion() {
    showBesideAPane()
    compose.onNodeWithTag("chat-input").performClick()

    rowKey("@").performClick()
    rowKey("/").performClick()

    compose.onNodeWithTag("chat-input").assert(hasText("@/"))
  }

  /** A question is prose: Tab would indent nothing, and there is no kernel history to walk. */
  @Test
  fun theQuestionsRowCarriesNoTabAndNoHistoryKeys() {
    showBesideAPane()

    compose.onNodeWithTag("chat-input").performClick()

    rowKey("@").assertIsDisplayed()
    rowKey("Tab").assertDoesNotExist()
    rowKey("Hist ↑").assertDoesNotExist()
  }

  @Test
  fun theRowLeavesWithTheCaret() {
    lateinit var focus: FocusManager
    showBesideAPane { focus = LocalFocusManager.current }
    compose.onNodeWithTag("chat-input").performClick()
    compose.onNodeWithTag(CHAT_KEY_ROW).assertIsDisplayed()

    compose.runOnIdle { focus.clearFocus() }

    compose.onNodeWithTag(CHAT_KEY_ROW).assertDoesNotExist()
  }

  private fun send(question: String) {
    compose.onNodeWithTag("chat-input").performTextInput(question)
    compose.onNodeWithTag("chat-send").performClick()
  }

  /** Where the node tagged [tag] is drawn, in the root's pixels. */
  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

  /** How far the messages are scrolled, and how far they can be - what a screen reader reads. */
  private fun scrollRange(): ScrollAxisRange = compose.onNodeWithTag("chat-messages").fetchSemanticsNode()
    .config[SemanticsProperties.VerticalScrollAxisRange]

  private fun awaiting() = ToolCall(
    id = "c1",
    title = "echo probe",
    status = ToolCallStatus.Running,
    permission = ToolCallPermission.Pending(sessionId = "s1", options = listOf(ALLOW_ONCE, ALWAYS_ALLOW, REJECT)),
  )

  private fun callsMessage(call: ToolCall) = ChatMessage(id = "t", body = "", fromMe = false, toolCalls = listOf(call))

  private fun show(answering: AgentChat? = null) {
    val panel = answering?.let(::openOnUntitled) ?: chat
    compose.setContent {
      JupyterDroidTheme {
        ContributedSidePanel(contribution = panel, host = SidePanelHost(browsedDirectory = "", server = null))
      }
    }
    openUntitled()
  }

  /** The panel at a side pane's width beside a wider one, under the window's key row as the app draws it. */
  private fun showBesideAPane(alsoCompose: @Composable () -> Unit = {}) {
    compose.setContent {
      alsoCompose()
      JupyterDroidTheme {
        WindowKeyRowHosted {
          Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(SIDE_PANE)) {
              ContributedSidePanel(contribution = chat, host = SidePanelHost(browsedDirectory = "", server = null))
            }
            Box(modifier = Modifier.weight(1f))
          }
        }
      }
    }
    openUntitled()
  }

  /** A workspace whose one chat is `untitled.chat`, answered by [answering]. */
  private fun openOnUntitled(answering: AgentChat) = AiExtension(
    chatFor = { _, _ -> answering },
    chatFilesFor = { FakeChatFiles(listOf("untitled.chat")) },
  ).sidePanels.single()

  /** The panel opens on its list of chats; every test here is about the chat once it is open. */
  private fun openUntitled() {
    compose.onNodeWithText("untitled").performClick()
  }

  /** A key on Chat's row, and not one with the same text elsewhere. */
  private fun rowKey(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(CHAT_KEY_ROW)))

  private companion object {
    /** Taller than any screen the tests run on, one number per line. */
    val LONG_ANSWER = (1..200).joinToString("\n")

    const val TIMEOUT_MILLIS = 5_000L

    const val CHAT_KEY_ROW = "chat_key_row"
    val SIDE_PANE = 360.dp

    /** opencode's three, as its `bash` request was logged on the tablet. */
    val ALLOW_ONCE = PermissionOption(id = "once", name = "Allow once", rejects = false)
    val ALWAYS_ALLOW = PermissionOption(id = "always", name = "Always allow", rejects = false)
    val REJECT = PermissionOption(id = "reject", name = "Reject", rejects = true)
  }
}
