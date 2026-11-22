package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.RecordingNotebookCells
import com.jupydroid.app.RecordingWorkspaceFileOpener
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.AtFontScale
import com.jupydroid.app.ui.PHONE_WIDTH
import com.jupydroid.app.ui.breaksInsideAWord
import com.jupydroid.app.ui.isCut
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extension.api.SidePanelHost
import com.jupydroid.extensions.ai.AiExtension
import com.jupydroid.extensions.ai.data.fake.FakeAgentChat
import com.jupydroid.extensions.ai.data.fake.FakeChatFiles
import com.jupydroid.extensions.ai.data.fake.FakeQueuedQuestionsStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the Chat list's two buttons do at the narrowest phone under a large font scale: each label
 * keeps its whole words and both buttons stay inside the screen. Their behaviour at the default size
 * is `ChatListSidePanelTest`'s.
 *
 * New chat kept its full width and MCP servers got what was left, so at twice the font scale its
 * label was drawn one or two letters to a line.
 */
@RunWith(AndroidJUnit4::class)
class ChatListFontScaleTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun mcpServersKeepsItsWholeWordsOnAPhoneAtTwiceTheFontScale() {
    showOnAPhoneAt(fontScale = 2f, chats = emptyList())

    assertLabelKeepsWhole(MCP_SERVERS)
  }

  @Test
  fun newChatKeepsItsWholeWordsOnAPhoneAtTwiceTheFontScale() {
    showOnAPhoneAt(fontScale = 2f, chats = emptyList())

    assertLabelKeepsWhole(NEW_CHAT)
  }

  @Test
  fun bothButtonsStayInsideAPhoneAtTwiceTheFontScale() {
    showOnAPhoneAt(fontScale = 2f, chats = listOf(A_CHAT))

    val screenWidth = with(compose.density) { PHONE_WIDTH.toPx() }
    listOf(NEW_CHAT, MCP_SERVERS).forEach { label ->
      val bounds = boundsOfButton(label)
      val inside = bounds.left >= 0f && bounds.right <= screenWidth
      assertTrue("$label at $bounds leaves the 0..$screenWidth px screen", inside)
    }
  }

  @Test
  fun theTwoButtonsShareALineOnACommonPhoneAtTheDefaultFontScale() {
    showOnAPhoneAt(fontScale = 1f, chats = listOf(A_CHAT), width = COMMON_PHONE_WIDTH)

    val newChat = boundsOfButton(NEW_CHAT)
    val mcpServers = boundsOfButton(MCP_SERVERS)
    // Centred on one line, so a taller button's top sits above a shorter one's: they overlap, not align.
    val onOneLine = mcpServers.top < newChat.bottom && newChat.top < mcpServers.bottom
    val beside = mcpServers.left >= newChat.right && onOneLine
    assertTrue("$MCP_SERVERS at $mcpServers should sit beside $NEW_CHAT at $newChat", beside)
  }

  private fun showOnAPhoneAt(fontScale: Float, chats: List<String>, width: Dp = PHONE_WIDTH) {
    val panel: SidePanelContribution = AiExtension(
      chatFor = { _, _, _ -> FakeAgentChat(history = emptyList()) },
      chatFilesFor = { FakeChatFiles(chats) },
      queuesFor = { FakeQueuedQuestionsStore() },
    ).sidePanels.single()
    compose.setContent {
      JupydroidTheme {
        Box(Modifier.requiredWidth(width)) {
          AtFontScale(fontScale) { ContributedSidePanel(panel, host()) }
        }
      }
    }
    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText(NEW_CHAT)).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun host(): SidePanelHost = SidePanelHost(
    browsedDirectory = "",
    server = null,
    spaceId = "base",
    RichTextMarkdownRenderer,
    RecordingNotebookCells(available = false),
    RecordingWorkspaceFileOpener(),
  )

  /** The text node of the unmerged tree: a button merges its descendants, so only the node itself carries a layout. */
  private fun assertLabelKeepsWhole(label: String) {
    val text = compose.onNodeWithText(label, useUnmergedTree = true)
    assertFalse("$label is cut", text.isCut())
    assertFalse("$label breaks inside a word", text.breaksInsideAWord())
  }

  private fun boundsOfButton(label: String): Rect =
    compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
    val COMMON_PHONE_WIDTH = 412.dp
    const val NEW_CHAT = "New chat"
    const val MCP_SERVERS = "MCP servers"
    const val A_CHAT = "untitled.chat"
  }
}
