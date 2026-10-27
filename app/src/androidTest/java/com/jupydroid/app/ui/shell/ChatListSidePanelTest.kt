package com.jupydroid.app.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.RecordingNotebookCells
import com.jupydroid.app.RecordingWorkspaceFileOpener
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.shell.UntitledChatPanel.Companion.TIMEOUT_MILLIS
import com.jupydroid.extension.api.RENAME_FIELD_TAG
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extension.api.SidePanelHost
import com.jupydroid.extensions.ai.AiExtension
import com.jupydroid.extensions.ai.data.fake.FakeAgentChat
import com.jupydroid.extensions.ai.data.fake.FakeChatFiles
import com.jupydroid.extensions.ai.data.fake.FakeQueuedQuestionsStore
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.McpSettingsFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Chat panel's list of the workspace's chats, as the shell draws it: a row to press for each,
 * which opens that chat, a long press that renames or deletes it, and a way back to the list.
 */
@RunWith(AndroidJUnit4::class)
class ChatListSidePanelTest {
  @get:Rule val compose = createComposeRule()

  /** Each chat holds one message naming its own file, so what is shown says which chat opened. */
  private fun panel(vararg chats: String): SidePanelContribution = AiExtension(
    chatFor = { _, path -> FakeAgentChat(history = listOf(ChatMessage(id = "m", body = "in $path", fromMe = false))) },
    chatFilesFor = { FakeChatFiles(chats.toList()) },
    queuesFor = { kept },
  ).sidePanels.single()

  /** What every panel this test shows keeps its chats' waiting questions in. */
  private val kept = FakeQueuedQuestionsStore()

  /** Where every panel this test shows asks for a workspace file to be opened. */
  private val fileOpener = RecordingWorkspaceFileOpener()

  @Test
  fun theChatsAreListedByNameWithoutTheirExtension() {
    show(panel("untitled.chat", "Notes.chat"))

    val notes = compose.onNodeWithText("Notes").fetchSemanticsNode().boundsInRoot
    val untitled = compose.onNodeWithText("untitled").fetchSemanticsNode().boundsInRoot
    assertTrue("Notes $notes is not above untitled $untitled", notes.bottom <= untitled.top)
  }

  /**
   * A row, not a line of loose text: as tall as Material's one-line list item, which is VS Code's
   * 54 px session row on a touchscreen (vscode@3f3380c `agentSessionsViewer.ts`, `ITEM_HEIGHT`), with a
   * line between each row and the next.
   */
  @Test
  fun eachChatIsARowAtLeastAListItemTallWithALineBetween() {
    show(panel("a.chat", "b.chat", "c.chat"))

    val rows = compose.onAllNodesWithTag("chat-row").fetchSemanticsNodes()
    assertEquals(3, rows.size)
    with(compose.density) { rows.forEach { assertTrue("${it.size.height.toDp()}", it.size.height.toDp() >= 56.dp) } }
    compose.onAllNodesWithTag("chat-row-divider").assertCountEquals(2)
  }

  /** "No chat opened" is the empty state; above a list of chats it is one more line to read past. */
  @Test
  fun aWorkspaceWithChatsListsThemWithoutTheEmptyHeading() {
    show(panel("untitled.chat"))

    compose.onNodeWithText("untitled").assertIsDisplayed()
    compose.onNodeWithText("No chat opened").assertDoesNotExist()
  }

  @Test
  fun aWorkspaceWithNoChatSaysNoneIsOpen() {
    show(panel())

    compose.onNodeWithText("No chat opened").assertIsDisplayed()
  }

  /**
   * MCP servers opens jupyter-ai's settings file in the host's editor, as VS Code's command opens
   * `mcp.json` (vscode@3f3380c `src/vs/workbench/contrib/mcp/browser/mcpCommands.ts`), with the
   * empty list a missing file starts as.
   */
  @Test
  fun mcpServersOpensTheMcpSettingsFile() {
    show(panel("untitled.chat"))

    compose.onNodeWithText("MCP servers").performClick()

    assertEquals(listOf(McpSettingsFile.PATH to McpSettingsFile.EMPTY), fileOpener.opened)
  }

  /** The file is the workspace's, not a chat's, so it needs no chat to exist. */
  @Test
  fun mcpServersIsOfferedWithNoChat() {
    show(panel())

    compose.onNodeWithText("MCP servers").assertIsDisplayed()
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

  /**
   * jupyter-chat's placeholder offers New chat under "No chat opened" (`placeholder.tsx`). What a
   * finger can press is measured, not what is drawn: Material draws a button 40 dp tall and widens
   * its touch bounds to 48 dp.
   */
  @Test
  fun anEmptyWorkspaceOffersNewChatAtLeastATouchTargetTall() {
    show(panel())

    compose.onNodeWithText("No chat opened").assertIsDisplayed()
    val pressable = compose.onNodeWithText("New chat").assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
    with(compose.density) { assertTrue("${pressable.height.toDp()}", pressable.height.toDp() >= 48.dp) }
  }

  @Test
  fun newChatOpensTheChatItCreated() {
    show(panel())

    compose.onNodeWithText("New chat").performClick()

    compose.onNodeWithText("in untitled.chat").assertIsDisplayed()
    compose.onNodeWithText("untitled").assertIsDisplayed()
  }

  @Test
  fun backFromANewChatListsItBesideTheOthers() {
    show(panel("notes.chat"))
    compose.onNodeWithText("New chat").performClick()

    compose.onNodeWithContentDescription("All chats").performClick()

    compose.onNodeWithText("untitled").assertIsDisplayed()
    compose.onNodeWithText("notes").assertIsDisplayed()
  }

  @Test
  fun aSecondNewChatIsAnotherUntitledChat() {
    show(panel())
    compose.onNodeWithText("New chat").performClick()
    compose.onNodeWithContentDescription("All chats").performClick()

    compose.onNodeWithText("New chat").performClick()

    compose.onNodeWithText("in untitled1.chat").assertIsDisplayed()
  }

  /**
   * A long press opens a row's own actions, as it does on a row of the explorer, which lists these
   * same files; VS Code keeps Rename... in a session's context menu (vscode@3f3380c
   * `agentSessionsActions.ts`).
   */
  @Test
  fun aLongPressOnAChatOffersRename() {
    show(panel("untitled.chat", "untitled1.chat"))

    compose.onNodeWithText("untitled1").performTouchInput { longClick() }

    compose.onNodeWithText("Rename").assertIsDisplayed()
  }

  /**
   * jupyter-chat's dialog holds the name without `.chat` (`renameChat`), and all of it is selected,
   * so typing replaces it: no extension is shown for the selection to stop short of.
   */
  @Test
  fun renameAsksInADialogHoldingTheWholeNameSelected() {
    show(panel("my.notes.chat"))

    openRename("my.notes")

    compose.onNodeWithText("Rename chat").assertIsDisplayed()
    compose.onNodeWithTag(RENAME_FIELD_TAG).assert(
      SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, "my.notes".length)),
    )
  }

  @Test
  fun aRenamedChatIsListedUnderItsNewNameAndNotItsOld() {
    show(panel("untitled.chat", "untitled1.chat"))

    rename("untitled1", to = "notes")

    compose.onNodeWithText("notes").assertIsDisplayed()
    compose.onNodeWithText("untitled1").assertDoesNotExist()
    compose.onNodeWithText("untitled").assertIsDisplayed()
  }

  /** The list is not traded for the error: every chat is still there to open, under the reason. */
  @Test
  fun renamingOntoATakenNameKeepsTheListAndSaysWhy() {
    show(panel("untitled.chat", "notes.chat"))

    rename("untitled", to = "notes")

    compose.onNodeWithText("notes.chat already exists", substring = true).assertIsDisplayed()
    compose.onNodeWithText("untitled").assertIsDisplayed()
    compose.onNodeWithText("notes").assertIsDisplayed()
  }

  /** Its waiting questions go with it, so they are asked in it and not in the next chat given its old name. */
  @Test
  fun aRenamedChatsKeptQuestionsAreKeptUnderItsNewName() {
    runBlocking { kept.write(ChatFile("untitled.chat"), listOf("What is 2+2?")) }
    show(panel("untitled.chat"))

    rename("untitled", to = "notes")

    compose.waitUntil(TIMEOUT_MILLIS) { keptFor("notes.chat") == listOf("What is 2+2?") }
    assertEquals(emptyList<String>(), keptFor("untitled.chat"))
  }

  @Test
  fun aRenameOntoATakenNameLeavesTheKeptQuestionsWhereTheyWere() {
    runBlocking { kept.write(ChatFile("untitled.chat"), listOf("What is 2+2?")) }
    show(panel("untitled.chat", "notes.chat"))

    rename("untitled", to = "notes")

    compose.onNodeWithText("notes.chat already exists", substring = true).assertIsDisplayed()
    assertEquals(listOf("What is 2+2?"), keptFor("untitled.chat"))
    assertEquals(emptyList<String>(), keptFor("notes.chat"))
  }

  /** A long press is out of a screen reader's reach; the row's own actions say Rename instead. */
  @Test
  fun aScreenReaderReachesRenameWithoutALongPress() {
    show(panel("untitled.chat"))

    val actions = compose.onNodeWithText("untitled").fetchSemanticsNode().config[SemanticsActions.CustomActions]
    compose.runOnIdle { actions.single { it.label == "Rename" }.action() }

    compose.onNodeWithText("Rename chat").assertIsDisplayed()
  }

  /**
   * Delete... sits beside Rename in the row's actions, as VS Code's sits beside Rename... in a
   * session's context menu (vscode@3f3380c `agentSessionsActions.ts`, `DeleteAgentSessionAction`).
   */
  @Test
  fun aLongPressOnAChatOffersDelete() {
    show(panel("untitled.chat"))

    compose.onNodeWithText("untitled").performTouchInput { longClick() }

    compose.onNodeWithText("Delete").assertIsDisplayed()
  }

  /** Nothing is removed before the answer, and Cancel, what a tap outside also picks, removes nothing. */
  @Test
  fun deleteAsksBeforeDeletingAndCancelKeepsTheChat() {
    show(panel("untitled.chat", "notes.chat"))

    openDelete("notes")
    compose.onNodeWithText("Are you sure you want to permanently delete: notes?").assertIsDisplayed()
    compose.onNodeWithText("Cancel").performClick()

    compose.onNodeWithText("notes").assertIsDisplayed()
  }

  @Test
  fun aDeletedChatIsNoLongerListed() {
    show(panel("untitled.chat", "notes.chat"))

    openDelete("notes")
    compose.onNode(hasText("Delete") and hasClickAction()).performClick()

    compose.onNodeWithText("notes").assertDoesNotExist()
    compose.onNodeWithText("untitled").assertIsDisplayed()
  }

  /** Or a new chat given its name would ask questions nobody sent in it. */
  @Test
  fun aDeletedChatsKeptQuestionsAreNoLongerKept() {
    runBlocking { kept.write(ChatFile("notes.chat"), listOf("What is 2+2?")) }
    show(panel("untitled.chat", "notes.chat"))

    openDelete("notes")
    compose.onNode(hasText("Delete") and hasClickAction()).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { keptFor("notes.chat").isEmpty() }
  }

  @Test
  fun aScreenReaderReachesDeleteWithoutALongPress() {
    show(panel("untitled.chat"))

    val actions = compose.onNodeWithText("untitled").fetchSemanticsNode().config[SemanticsActions.CustomActions]
    compose.runOnIdle { actions.single { it.label == "Delete" }.action() }

    compose.onNodeWithText("Are you sure you want to permanently delete: untitled?").assertIsDisplayed()
  }

  private fun keptFor(path: String): List<String> = runBlocking { kept.read(ChatFile(path)) }

  private fun openDelete(chat: String) {
    compose.onNodeWithText(chat).performTouchInput { longClick() }
    compose.onNodeWithText("Delete").performClick()
  }

  private fun openRename(chat: String) {
    compose.onNodeWithText(chat).performTouchInput { longClick() }
    compose.onNodeWithText("Rename").performClick()
  }

  private fun rename(chat: String, to: String) {
    openRename(chat)
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput(to)
    compose.onNodeWithText("Rename").performClick()
  }

  private fun show(panel: SidePanelContribution, shown: () -> Boolean = { true }) {
    compose.setContent {
      JupyterDroidTheme {
        if (shown()) {
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
}
