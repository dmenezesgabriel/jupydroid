package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupydroid.extension.api.DeleteFileDialog
import com.jupydroid.extension.api.FocusRingDropdownMenuItem
import com.jupydroid.extension.api.RenameFileDialog
import com.jupydroid.extension.api.focusRing
import com.jupydroid.extension.api.glyphBesideText
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.ChatFilesException
import com.jupydroid.extensions.ai.domain.McpSettingsFile
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStore
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStoreException
import com.jupydroid.extensions.ai.domain.byName
import kotlinx.coroutines.launch

/** Where reading the workspace's chats stands. */
private sealed interface ChatListing {
  data object Pending : ChatListing

  /** [notice] is what an act on the list could not do, said above the chats rather than instead of them. */
  data class Listed(val chats: List<ChatFile>, val notice: String? = null) : ChatListing

  data class Failed(val message: String) : ChatListing
}

/**
 * What the panel shows with no chat open: every chat by name, after jupyter-chat's own words
 * (jupyter-chat@2c224bc `packages/jupyter-chat/src/widgets/placeholder.tsx`), or its "No chat opened"
 * when there is none, under jupyter-chat's New chat, which makes an untitled chat and opens it.
 *
 * jupyter-chat draws each name as a bare link-styled button (`style/placeholder.css`); here each is a
 * row, as VS Code draws its sessions (vscode@3f3380c
 * `src/vs/workbench/contrib/chat/browser/agentSessions/agentSessionsViewer.ts`), since a touchscreen
 * has no hover to say that a line of text can be pressed.
 *
 * A long press on a chat offers Rename, in the host's own [RenameFileDialog], and Delete, after the
 * host's own [DeleteFileDialog], as a long press on a row of the explorer does for the same files.
 * No chat is open while the list is shown, so none holds a socket on the file Delete removes
 * ([ChatFiles.delete]). The questions a chat has waiting in [queues] go with it: a rename moves them
 * to its new path, and a delete forgets them, so a later chat given its old name asks none of them.
 *
 * **Read when the list is shown**: opening a chat and coming back reads the workspace again, so a
 * chat made or removed elsewhere meanwhile is listed as it now stands. A rename or a delete reads it
 * again too.
 *
 * Beside New chat, MCP servers opens [McpSettingsFile] through [onOpenMcpSettings]: the servers are
 * the workspace's, handed to the agent of every chat, so they are reached from the list rather than
 * from one chat.
 */
@Composable
internal fun ChatList(
  files: ChatFiles,
  queues: QueuedQuestionsStore,
  onOpen: (ChatFile) -> Unit,
  onOpenMcpSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var listing by remember(files) { mutableStateOf<ChatListing>(ChatListing.Pending) }
  var reads by remember(files) { mutableIntStateOf(0) }
  var renaming by remember(files) { mutableStateOf<ChatFile?>(null) }
  var deleting by remember(files) { mutableStateOf<ChatFile?>(null) }
  LaunchedEffect(files, reads) {
    listing = try {
      ChatListing.Listed(files.list().byName())
    } catch (failure: ChatFilesException) {
      ChatListing.Failed(failure.message.orEmpty())
    }
  }
  val scope = rememberCoroutineScope()
  // What an act on a chat could not do is said above the list, so every chat is still there to open.
  val actThenRead: (suspend () -> Unit) -> Unit = { act ->
    scope.launch {
      try {
        act()
        reads++
      } catch (failure: ChatFilesException) {
        listing = listing.withNotice(failure.message.orEmpty())
      } catch (failure: QueuedQuestionsStoreException) {
        listing = listing.withNotice(failure.message.orEmpty())
      }
    }
  }
  val onNew: () -> Unit = {
    scope.launch {
      try {
        onOpen(files.create())
      } catch (failure: ChatFilesException) {
        listing = ChatListing.Failed(failure.message.orEmpty())
      }
    }
  }
  Column(modifier = modifier.fillMaxSize().testTag("chat-list")) {
    when (val shown = listing) {
      ChatListing.Pending -> Unit
      is ChatListing.Failed -> Text(shown.message, color = MaterialTheme.colorScheme.error)
      is ChatListing.Listed if shown.chats.isEmpty() -> {
        Text("No chat opened", style = MaterialTheme.typography.titleMedium)
        ChatListActions(onNew, onOpenMcpSettings)
      }
      is ChatListing.Listed -> {
        ChatListActions(onNew, onOpenMcpSettings)
        shown.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ChatRows(shown.chats, onOpen) { chat ->
          listOf(ChatRowAction("Rename") { renaming = chat }, ChatRowAction("Delete") { deleting = chat })
        }
      }
    }
  }
  renaming?.let { chat ->
    RenameFileDialog(
      currentTitle = chat.name,
      onConfirm = { name ->
        renaming = null
        actThenRead { queues.move(chat, to = files.rename(chat, name)) }
      },
      onDismiss = { renaming = null },
      heading = "Rename chat",
      // The whole name, since `.chat` is not shown for the selection to stop short of.
      initialSelection = { TextRange(0, it.length) },
    )
  }
  deleting?.let { chat ->
    DeleteFileDialog(
      chat.name,
      onDelete = {
        deleting = null
        actThenRead {
          files.delete(chat)
          queues.write(chat, emptyList())
        }
      },
      onDismiss = { deleting = null },
    )
  }
}

/** This listing with [notice] said above its chats, or [notice] instead when none were listed. */
private fun ChatListing.withNotice(notice: String): ChatListing =
  (this as? ChatListing.Listed)?.copy(notice = notice) ?: ChatListing.Failed(notice)

/** One of a chat row's actions: an item of its long-press menu, and a custom action of its own. */
private class ChatRowAction(val label: String, val run: () -> Unit)

/**
 * What the list offers above its chats: a new chat, and the MCP servers every chat's agent gets.
 *
 * The buttons share a line while they fit and the second drops under the first when they do not. In
 * a `Row` the first kept its full width and the second got the remainder, so at twice the font scale
 * on a 320 dp phone "MCP servers" was drawn a letter or two to a line
 * (`ChatListFontScaleTest.mcpServersKeepsItsWholeWordsOnAPhoneAtTwiceTheFontScale`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatListActions(onNew: () -> Unit, onOpenMcpSettings: () -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    // jupyter-chat's New chat, its `addIcon` before the label (jupyter-chat@2c224bc `placeholder.tsx`).
    ChatListButton("New chat", Icons.Filled.Add, onNew)
    // VS Code's MCP entries carry `Codicon.server` (vscode@3f3380c
    // `src/vs/workbench/contrib/mcp/browser/mcpCommands.ts`); `Dns` is Material's server rack.
    ChatListButton("MCP servers", Icons.Outlined.Dns, onOpenMcpSettings)
  }
}

@Composable
private fun ChatListButton(label: String, icon: ImageVector, onClick: () -> Unit) {
  val labelSize = MaterialTheme.typography.labelLarge.fontSize
  val glyph = LocalDensity.current.glyphBesideText(ButtonDefaults.IconSize, labelSize)
  OutlinedButton(
    onClick = onClick,
    modifier = Modifier.padding(vertical = 8.dp).focusRing().testTag("chat-list-button-$label"),
  ) {
    Icon(
      icon,
      contentDescription = null,
      modifier = Modifier.size(glyph).testTag("chat-list-glyph-$label"),
    )
    Text(label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
  }
}

/** The chats in one bordered box, a line between each and the next, as the question's box is drawn. */
@Composable
private fun ChatRows(chats: List<ChatFile>, onOpen: (ChatFile) -> Unit, actionsFor: (ChatFile) -> List<ChatRowAction>) {
  Text(
    "Open an existing chat:",
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 8.dp),
  )
  val shape = RoundedCornerShape(8.dp)
  LazyColumn(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, shape).clip(shape)) {
    itemsIndexed(chats, key = { _, chat -> chat.path }) { index, chat ->
      if (index >
        0
      ) {
        HorizontalDivider(
          color = MaterialTheme.colorScheme.outlineVariant,
          modifier = Modifier.testTag("chat-row-divider"),
        )
      }
      ChatRow(chat, onOpen = { onOpen(chat) }, actions = actionsFor(chat))
    }
  }
}

/** The size of a Material list item's leading icon, from which the icon grows with the name beside it. */
private val CHAT_ROW_ICON = 24.dp

/**
 * A Material one-line list item: 56 dp, the whole width pressable, an icon before the name.
 *
 * A long press opens its actions, as a row of the explorer does
 * (`app/src/main/java/com/jupydroid/app/ui/explorer/ExplorerScreen.kt`, `EntryRow`), and the same
 * actions are the row's custom actions, since a screen reader cannot long-press.
 */
@Composable
private fun ChatRow(chat: ChatFile, onOpen: () -> Unit, actions: List<ChatRowAction>) {
  val glyph = LocalDensity.current.glyphBesideText(CHAT_ROW_ICON, MaterialTheme.typography.bodyLarge.fontSize)
  var menuOpen by remember { mutableStateOf(false) }
  val spoken = actions.map { action ->
    CustomAccessibilityAction(action.label) {
      action.run()
      true
    }
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .semantics(mergeDescendants = true) { customActions = spoken }
      .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
      .padding(horizontal = 16.dp)
      .testTag("chat-row"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.AutoMirrored.Outlined.Chat,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(glyph).testTag("chat-row-glyph"),
    )
    Text(
      chat.name,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(start = 16.dp),
    )
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      actions.forEach { action ->
        FocusRingDropdownMenuItem(
          text = { Text(action.label) },
          onClick = {
            menuOpen = false
            action.run()
          },
        )
      }
    }
  }
}
