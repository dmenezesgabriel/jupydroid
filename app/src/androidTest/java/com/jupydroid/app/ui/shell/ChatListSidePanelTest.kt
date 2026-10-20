package com.jupydroid.app.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extension.api.SidePanelHost
import com.jupydroid.extensions.ai.AiExtension
import com.jupydroid.extensions.ai.data.fake.FakeAgentChat
import com.jupydroid.extensions.ai.data.fake.FakeChatFiles
import com.jupydroid.extensions.ai.domain.ChatMessage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Chat panel's list of the workspace's chats, as the shell draws it: a row to press for each,
 * which opens that chat, and a way back to the list.
 */
@RunWith(AndroidJUnit4::class)
class ChatListSidePanelTest {
  @get:Rule val compose = createComposeRule()

  /** Each chat holds one message naming its own file, so what is shown says which chat opened. */
  private fun panel(vararg chats: String): SidePanelContribution = AiExtension(
    chatFor = { _, path -> FakeAgentChat(history = listOf(ChatMessage(id = "m", body = "in $path", fromMe = false))) },
    chatFilesFor = { FakeChatFiles(chats.toList()) },
  ).sidePanels.single()

  @Test
  fun theChatsAreListedByNameWithoutTheirExtension() {
    show(panel("untitled.chat", "Notes.chat"))

    val notes = compose.onNodeWithText("Notes").fetchSemanticsNode().boundsInRoot
    val untitled = compose.onNodeWithText("untitled").fetchSemanticsNode().boundsInRoot
    assertTrue("Notes $notes is not above untitled $untitled", notes.bottom <= untitled.top)
  }

  @Test
  fun aWorkspaceWithNoChatSaysNoneIsOpen() {
    show(panel())

    compose.onNodeWithText("No chat opened").assertIsDisplayed()
  }

  @Test
  fun tappingAChatOpensThatChat() {
    show(panel("untitled.chat", "notes.chat"))

    compose.onNodeWithText("notes").performClick()

    compose.onNodeWithText("in notes.chat").assertIsDisplayed()
  }

  @Test
  fun backFromAChatListsTheChatsAgainAndOpensAnother() {
    show(panel("untitled.chat", "notes.chat"))
    compose.onNodeWithText("notes").performClick()

    compose.onNodeWithContentDescription("All chats").performClick()
    compose.onNodeWithText("untitled").performClick()

    compose.onNodeWithText("in untitled.chat").assertIsDisplayed()
  }

  /** Picking another panel drops this one from the composition; coming back finds the same chat. */
  @Test
  fun theOpenChatIsStillOpenWhenThePanelIsShownAgain() {
    val chat = panel("untitled.chat", "notes.chat")
    var shown by mutableStateOf(true)
    show(chat) { shown }
    compose.onNodeWithText("notes").performClick()

    compose.runOnIdle { shown = false }
    compose.runOnIdle { shown = true }

    compose.onNodeWithText("in notes.chat").assertIsDisplayed()
  }

  private fun show(panel: SidePanelContribution, shown: () -> Boolean = { true }) {
    compose.setContent {
      JupyterDroidTheme {
        if (shown()) {
          ContributedSidePanel(
            contribution = panel,
            host = SidePanelHost(browsedDirectory = "", server = null),
          )
        }
      }
    }
  }
}
