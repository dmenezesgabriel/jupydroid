package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.jupydroid.extension.api.FocusRingIconButton
import com.jupydroid.extension.api.MarkdownRenderer
import com.jupydroid.extension.api.NotebookCells
import com.jupydroid.extension.api.WorkspaceFileOpener
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.McpSettingsFile
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStore

/**
 * The Chat panel's body: the workspace's chats to pick from, or the one picked, with a way back.
 *
 * ```
 * val files = FakeChatFiles(listOf("untitled.chat"))
 * ChatsPanel(
 *   files, OpenChatSelection(), FakeQueuedQuestionsStore(), host.markdown, host.notebookCells, host.fileOpener,
 * ) { FakeAgentChat() }
 * ```
 *
 * @param queues where each chat's waiting questions are kept, and moved or forgotten with its file.
 * @param markdown what an agent's answer is drawn with: the host's, so it reads as a markdown cell does.
 * @param notebookCells where Insert under a code block of an answer puts its code.
 * @param fileOpener what opens [McpSettingsFile] in the host's editor, and the open chat in a tab of its own.
 * @param chatFor the chat at a path relative to the workspace root.
 */
@Composable
fun ChatsPanel(
  files: ChatFiles,
  selection: OpenChatSelection,
  queues: QueuedQuestionsStore,
  markdown: MarkdownRenderer,
  notebookCells: NotebookCells,
  fileOpener: WorkspaceFileOpener,
  chatFor: (String) -> AgentChat,
  modifier: Modifier = Modifier,
) {
  val open = selection.path
  if (open == null) {
    ChatList(
      files,
      queues,
      onOpen = { selection.path = it.path },
      onOpenMcpSettings = { fileOpener.open(McpSettingsFile.PATH, McpSettingsFile.EMPTY) },
      modifier = modifier,
    )
    return
  }
  Column(modifier = modifier.fillMaxSize()) {
    OpenChatHeader(
      ChatFile(open).name,
      onBack = { selection.path = null },
      // jupyter-chat's `moveChat` from the side panel (jupyter-chat@2c224bc
      // `packages/jupyterlab-chat-extension/src/index.ts`): a move, not a copy, so the tab is asked for
      // and then the panel lets the chat go. The host opens a `.chat` as a chat tab, revealing one
      // already open. A listed chat exists, so the empty text, what `newUntitled` writes, is never used.
      onMoveToTab = {
        fileOpener.open(open, "")
        selection.path = null
      },
    )
    ChatPanel(
      chat = remember(open) { chatFor(open) },
      file = ChatFile(open),
      queues = queues,
      markdown = markdown,
      notebookCells = notebookCells,
      modifier = Modifier.weight(1f),
    )
  }
}

/**
 * The open chat's name, after the way back to the list, as jupyter-chat's side panel titles a chat, and
 * the way to a tab at its end, wearing jupyter-chat's `launchIcon` for `moveChat`.
 */
@Composable
private fun OpenChatHeader(name: String, onBack: () -> Unit, onMoveToTab: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    FocusRingIconButton(onClick = onBack) {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All chats")
    }
    Text(
      name,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    FocusRingIconButton(onClick = onMoveToTab) {
      Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Move to tab")
    }
  }
}
