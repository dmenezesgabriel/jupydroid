package com.jupydroid.extensions.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.runtime.remember
import com.jupydroid.extension.api.Extension
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extension.api.SpaceServerEndpoint
import com.jupydroid.extensions.ai.data.fake.FakeAgentChat
import com.jupydroid.extensions.ai.data.http.OkHttpAgentChat
import com.jupydroid.extensions.ai.data.logcat.LogcatChatSocketLog
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.ui.ChatPanel

/**
 * jupyter-ai's chat frontend: one Chat panel on the side rail, answered by an agent installed in the
 * Space (ADR-0104, ADR-0106).
 *
 * This class is the module's composition root, the one place it builds an adapter - `:app`'s
 * `di/AppContainer` cannot, since it does not see this module's insides.
 *
 * ```
 * AppContainer(extensions = listOf(GitExtension(), AiExtension()))
 * ```
 *
 * @param chatFor the chat for a Space's server; null is a Space with no server, which talks to
 *   [FakeAgentChat] so the panel still opens and claims no answer nobody gave.
 */
class AiExtension(
  private val chatFor: (SpaceServerEndpoint?) -> AgentChat = { server ->
    server?.let { OkHttpAgentChat(it, log = LogcatChatSocketLog) } ?: FakeAgentChat()
  },
) : Extension {
  override val sidePanels: List<SidePanelContribution> = listOf(
    // VS Code's own name for the view, and the nearest Material icon to its `Codicon.chatSparkle`
    // (vscode@3f3380c `src/vs/workbench/contrib/chat/browser/chatParticipant.contribution.ts`).
    SidePanelContribution(id = CHAT_PANEL_ID, label = "Chat", icon = Icons.AutoMirrored.Filled.Chat) { host ->
      val chat = remember(host.server) { chatFor(host.server) }
      ChatPanel(chat = chat)
    },
  )

  private companion object {
    const val CHAT_PANEL_ID = "ai.chat"
  }
}
