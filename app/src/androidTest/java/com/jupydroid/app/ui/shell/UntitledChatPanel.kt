package com.jupydroid.app.ui.shell

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extension.api.SidePanelHost
import com.jupydroid.extensions.ai.AiExtension
import com.jupydroid.extensions.ai.data.fake.FakeChatFiles
import com.jupydroid.extensions.ai.data.fake.FakeQueuedQuestionsStore
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStore

/**
 * The AI extension's Chat panel over a workspace whose one chat is `untitled.chat`, drawn by [compose]
 * and opened on that chat: where every test of an open chat starts.
 *
 * ```
 * val panel = UntitledChatPanel(compose)
 * panel.show(SettableAgentChat(ChatTranscript()))
 * panel.send("What is 2+2?")
 * ```
 */
class UntitledChatPanel(private val compose: ComposeContentTestRule) {
  /** The panel alone, answered by [answering] and keeping its waiting questions in [kept], open on the chat. */
  fun show(answering: AgentChat, kept: QueuedQuestionsStore = FakeQueuedQuestionsStore()) {
    compose.setContent {
      JupyterDroidTheme {
        ContributedSidePanel(contribution = contribution(answering, kept), host = HOST)
      }
    }
    open()
  }

  /** The panel's contribution, for a test that draws it inside something else, then calls [open]. */
  fun contribution(
    answering: AgentChat,
    kept: QueuedQuestionsStore = FakeQueuedQuestionsStore(),
  ): SidePanelContribution = AiExtension(
    chatFor = { _, _ -> answering },
    chatFilesFor = { FakeChatFiles(listOf("untitled.chat")) },
    queuesFor = { kept },
  ).sidePanels.single()

  /** The panel opens on its list of chats; the tests are about the chat once it is open. */
  fun open() {
    compose.onNodeWithText("untitled").performClick()
  }

  fun send(question: String) {
    compose.onNodeWithTag("chat-input").performTextInput(question)
    compose.onNodeWithTag("chat-send").performClick()
  }

  /** Where the node tagged [tag] is drawn, in the root's pixels. */
  fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

  companion object {
    /** How long a test waits for the panel to ask what it holds. */
    const val TIMEOUT_MILLIS = 5_000L

    /** No directory browsed and no server: the fake chat needs neither. The app's own renderer. */
    val HOST = SidePanelHost(browsedDirectory = "", server = null, spaceId = "base", RichTextMarkdownRenderer)
  }
}
