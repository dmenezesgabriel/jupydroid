package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.ChatFilesException
import com.jupydroid.extensions.ai.domain.byName

/** Where reading the workspace's chats stands. */
private sealed interface ChatListing {
  data object Pending : ChatListing

  data class Listed(val chats: List<ChatFile>) : ChatListing

  data class Failed(val message: String) : ChatListing
}

/**
 * What the panel shows with no chat open: jupyter-chat's placeholder, its heading and then every
 * chat by name (jupyter-chat@2c224bc `packages/jupyter-chat/src/widgets/placeholder.tsx`).
 *
 * **Read when the list is shown**: opening a chat and coming back reads the workspace again, so a
 * chat made or removed elsewhere meanwhile is listed as it now stands.
 */
@Composable
internal fun ChatList(files: ChatFiles, onOpen: (ChatFile) -> Unit, modifier: Modifier = Modifier) {
  var listing by remember(files) { mutableStateOf<ChatListing>(ChatListing.Pending) }
  LaunchedEffect(files) {
    listing = try {
      ChatListing.Listed(files.list().byName())
    } catch (failure: ChatFilesException) {
      ChatListing.Failed(failure.message.orEmpty())
    }
  }
  Column(modifier = modifier.fillMaxSize().testTag("chat-list")) {
    Text("No chat opened", style = MaterialTheme.typography.titleMedium)
    when (val shown = listing) {
      ChatListing.Pending -> Unit
      is ChatListing.Failed -> Text(shown.message, color = MaterialTheme.colorScheme.error)
      is ChatListing.Listed -> ChatNames(shown.chats, onOpen)
    }
  }
}

/** "Open an existing chat:" and a row per chat, only when there is one to open, as jupyter-chat words it. */
@Composable
private fun ChatNames(chats: List<ChatFile>, onOpen: (ChatFile) -> Unit) {
  if (chats.isEmpty()) return
  Text("Open an existing chat:", modifier = Modifier.padding(top = 8.dp))
  LazyColumn {
    items(chats, key = ChatFile::path) { chat ->
      Text(
        chat.name,
        modifier = Modifier.fillMaxWidth().clickable { onOpen(chat) }.padding(vertical = 12.dp),
      )
    }
  }
}
