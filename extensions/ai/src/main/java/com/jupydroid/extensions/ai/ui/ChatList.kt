package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupydroid.extension.api.RenameFileDialog
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.ChatFilesException
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
 * A long press on a chat offers Rename, in the host's own [RenameFileDialog], as a long press on a
 * row of the explorer does for the same files.
 *
 * **Read when the list is shown**: opening a chat and coming back reads the workspace again, so a
 * chat made or removed elsewhere meanwhile is listed as it now stands. A rename reads it again too.
 */
@Composable
internal fun ChatList(files: ChatFiles, onOpen: (ChatFile) -> Unit, modifier: Modifier = Modifier) {
  var listing by remember(files) { mutableStateOf<ChatListing>(ChatListing.Pending) }
  var reads by remember(files) { mutableIntStateOf(0) }
  var renaming by remember(files) { mutableStateOf<ChatFile?>(null) }
  LaunchedEffect(files, reads) {
    listing = try {
      ChatListing.Listed(files.list().byName())
    } catch (failure: ChatFilesException) {
      ChatListing.Failed(failure.message.orEmpty())
    }
  }
  val scope = rememberCoroutineScope()
  val onRenamed: (ChatFile, String) -> Unit = { chat, name ->
    renaming = null
    scope.launch {
      try {
        files.rename(chat, name)
        reads++
      } catch (failure: ChatFilesException) {
        listing = (listing as? ChatListing.Listed)?.copy(notice = failure.message.orEmpty())
          ?: ChatListing.Failed(failure.message.orEmpty())
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
        NewChatButton(onNew)
      }
      is ChatListing.Listed -> {
        NewChatButton(onNew)
        shown.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ChatRows(shown.chats, onOpen, onRename = { renaming = it })
      }
    }
  }
  renaming?.let { chat ->
    RenameFileDialog(
      currentTitle = chat.name,
      onConfirm = { onRenamed(chat, it) },
      onDismiss = { renaming = null },
      heading = "Rename chat",
      // The whole name, since `.chat` is not shown for the selection to stop short of.
      initialSelection = { TextRange(0, it.length) },
    )
  }
}

/** jupyter-chat's New chat, its `addIcon` before the label (jupyter-chat@2c224bc `placeholder.tsx`). */
@Composable
private fun NewChatButton(onClick: () -> Unit) {
  OutlinedButton(onClick = onClick, modifier = Modifier.padding(vertical = 8.dp)) {
    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Text("New chat", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
  }
}

/** The chats in one bordered box, a line between each and the next, as the question's box is drawn. */
@Composable
private fun ChatRows(chats: List<ChatFile>, onOpen: (ChatFile) -> Unit, onRename: (ChatFile) -> Unit) {
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
      ChatRow(chat, onOpen = { onOpen(chat) }, onRename = { onRename(chat) })
    }
  }
}

/**
 * A Material one-line list item: 56 dp, the whole width pressable, an icon before the name.
 *
 * A long press opens its actions, as a row of the explorer does
 * (`app/src/main/java/com/jupydroid/app/ui/explorer/ExplorerScreen.kt`, `EntryRow`), and the same
 * actions are the row's custom actions, since a screen reader cannot long-press.
 */
@Composable
private fun ChatRow(chat: ChatFile, onOpen: () -> Unit, onRename: () -> Unit) {
  var menuOpen by remember { mutableStateOf(false) }
  val actions = remember(onRename) {
    listOf(
      CustomAccessibilityAction("Rename") {
        onRename()
        true
      },
    )
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .semantics(mergeDescendants = true) { customActions = actions }
      .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
      .padding(horizontal = 16.dp)
      .testTag("chat-row"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      chat.name,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(start = 16.dp),
    )
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
        text = { Text("Rename") },
        onClick = {
          menuOpen = false
          onRename()
        },
      )
    }
  }
}
