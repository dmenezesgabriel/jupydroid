package com.jupydroid.extensions.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.jupydroid.extension.api.Extension
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extension.api.SpaceServerEndpoint
import com.jupydroid.extensions.ai.data.fake.FakeAgentChat
import com.jupydroid.extensions.ai.data.fake.FakeChatFiles
import com.jupydroid.extensions.ai.data.fake.FakeQueuedQuestionsStore
import com.jupydroid.extensions.ai.data.http.OkHttpAgentChat
import com.jupydroid.extensions.ai.data.http.OkHttpChatFiles
import com.jupydroid.extensions.ai.data.logcat.LogcatChatSocketLog
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStore
import com.jupydroid.extensions.ai.ui.ChatsPanel
import com.jupydroid.extensions.ai.ui.OpenChatSelection

/**
 * jupyter-ai's chat frontend: one Chat panel on the side rail, listing the workspace's chats and
 * answered by an agent installed in the Space (ADR-0104, ADR-0106).
 *
 * This class is the module's composition root, the one place it builds an adapter - `:app`'s
 * `di/AppContainer` cannot, since it does not see this module's insides.
 *
 * ```
 * AppContainer(extensions = listOf(GitExtension(), AiExtension()))
 * ```
 *
 * @param chatFor the chat at a path for a Space's server; null is a Space with no server, which talks
 *   to [FakeAgentChat] so the panel still opens and claims no answer nobody gave.
 * @param chatFilesFor the workspace's chats for a Space's server; null lists [FakeChatFiles], none.
 * @param queuesFor where the questions waiting in a Space's chats are kept, by the Space's id. The
 *   default keeps them in memory only; the app passes a `DiskQueuedQuestionsStore` in its own storage.
 */
class AiExtension(
  private val chatFor: (SpaceServerEndpoint?, String) -> AgentChat = { server, path ->
    server?.let { OkHttpAgentChat(it, chatPath = path, log = LogcatChatSocketLog) } ?: FakeAgentChat()
  },
  private val chatFilesFor: (SpaceServerEndpoint?) -> ChatFiles = { server ->
    server?.let(::OkHttpChatFiles) ?: FakeChatFiles()
  },
  private val queuesFor: (String) -> QueuedQuestionsStore = { FakeQueuedQuestionsStore() },
) : Extension {
  private val selection = OpenChatSelection()

  override val sidePanels: List<SidePanelContribution> = listOf(
    // VS Code's own name for the view, and the nearest Material icon to its `Codicon.chatSparkle`
    // (vscode@3f3380c `src/vs/workbench/contrib/chat/browser/chatParticipant.contribution.ts`).
    SidePanelContribution(id = CHAT_PANEL_ID, label = "Chat", icon = Icons.AutoMirrored.Filled.Chat) { host ->
      // Keyed on the server and the Space, so a chat opened against one Space's server is not asked
      // through another's, and its questions are not kept with another Space's.
      key(host.server, host.spaceId) {
        val files = remember { chatFilesFor(host.server) }
        val queues = remember { queuesFor(host.spaceId) }
        ChatsPanel(
          files,
          selection,
          queues,
          host.markdown,
          host.activeCell,
          host.fileOpener,
          chatFor = { path -> chatFor(host.server, path) },
        )
      }
    },
  )

  private companion object {
    const val CHAT_PANEL_ID = "ai.chat"
  }
}
