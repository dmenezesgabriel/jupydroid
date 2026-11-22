package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.RecordingNotebookCells
import com.jupydroid.app.RecordingWorkspaceFileOpener
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.extension.api.SidePanelHost
import com.jupydroid.extensions.ai.AiExtension
import com.jupydroid.extensions.ai.data.fake.FakeAgentChat
import com.jupydroid.extensions.ai.data.fake.FakeChatFiles
import com.jupydroid.extensions.ai.domain.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The chat open in the side panel moves to a main-area tab from its header, as jupyter-chat's
 * `moveChat` button does (jupyter-chat@2c224bc `packages/jupyterlab-chat-extension/src/index.ts`):
 * the chat's file is opened in the host, and the panel goes back to its list.
 */
@RunWith(AndroidJUnit4::class)
class ChatTabTest {
  @get:Rule val compose = createComposeRule()

  /** Where the panel asks for the chat's file to be opened. */
  private val fileOpener = RecordingWorkspaceFileOpener()

  /**
   * What a finger can press is measured, not what is drawn: an icon button's touch bounds are
   * 48 dp.
   */
  @Test
  fun anOpenChatOffersMoveToTabAtLeastATouchTargetTall() {
    showOpenOnUntitled()

    val pressable = compose.onNodeWithContentDescription("Move to tab").assertIsDisplayed()
      .fetchSemanticsNode().touchBoundsInRoot
    with(compose.density) { assertTrue("${pressable.height.toDp()}", pressable.height.toDp() >= 48.dp) }
  }

  /** A move, not a copy: the tab is asked for, then the panel lets the chat go. */
  @Test
  fun moveToTabOpensTheChatsFileInTheHostAndListsTheChatsAgain() {
    showOpenOnUntitled()

    compose.onNodeWithContentDescription("Move to tab").performClick()

    assertEquals(listOf("untitled.chat" to ""), fileOpener.opened)
    compose.onNodeWithText("untitled").assertIsDisplayed()
    compose.onNodeWithText("in untitled.chat").assertDoesNotExist()
  }

  /** The list has no chat open to move. */
  @Test
  fun moveToTabIsNotOfferedOnTheList() {
    show()

    compose.onNodeWithContentDescription("Move to tab").assertDoesNotExist()
  }

  private fun showOpenOnUntitled() {
    show()
    compose.onNodeWithText("untitled").performClick()
    compose.onNodeWithText("in untitled.chat").assertIsDisplayed()
  }

  /** The Chat panel over a workspace whose one chat holds one message naming its file. */
  private fun show() {
    val panel = AiExtension(
      chatFor = { _, path, _ ->
        FakeAgentChat(history = listOf(ChatMessage(id = "m", body = "in $path", fromMe = false)))
      },
      chatFilesFor = { FakeChatFiles(listOf("untitled.chat")) },
    ).sidePanels.single()
    compose.setContent {
      JupydroidTheme {
        ContributedSidePanel(
          contribution = panel,
          host = SidePanelHost(
            browsedDirectory = "",
            server = null,
            spaceId = "base",
            RichTextMarkdownRenderer,
            RecordingNotebookCells(available = false),
            fileOpener,
          ),
        )
      }
    }
  }
}
