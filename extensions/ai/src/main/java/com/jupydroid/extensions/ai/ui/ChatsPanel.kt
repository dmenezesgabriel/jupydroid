package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.jupydroid.extension.api.MarkdownRenderer
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStore

/**
 * The Chat panel's body: the workspace's chats to pick from, or the one picked, with a way back.
 *
 * ```
 * ChatsPanel(FakeChatFiles(listOf("untitled.chat")), OpenChatSelection(), FakeQueuedQuestionsStore(), host.markdown) {
 *   FakeAgentChat()
 * }
 * ```
 *
 * @param queues where each chat's waiting questions are kept, and moved or forgotten with its file.
 * @param markdown what an agent's answer is drawn with: the host's, so it reads as a markdown cell does.
 * @param chatFor the chat at a path relative to the workspace root.
 */
@Composable
fun ChatsPanel(
  files: ChatFiles,
  selection: OpenChatSelection,
  queues: QueuedQuestionsStore,
  markdown: MarkdownRenderer,
  chatFor: (String) -> AgentChat,
  modifier: Modifier = Modifier,
) {
  val open = selection.path
  if (open == null) {
    ChatList(files, queues, onOpen = { selection.path = it.path }, modifier = modifier)
    return
  }
  Column(modifier = modifier.fillMaxSize()) {
    OpenChatHeader(ChatFile(open).name, onBack = { selection.path = null })
    ChatPanel(
      chat = remember(open) { chatFor(open) },
      file = ChatFile(open),
      queues = queues,
      markdown = markdown,
      modifier = Modifier.weight(1f),
    )
  }
}

/** The open chat's name, after the way back to the list, as jupyter-chat's side panel titles a chat. */
@Composable
private fun OpenChatHeader(name: String, onBack: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All chats") }
    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
