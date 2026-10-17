package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatTranscript
import kotlinx.coroutines.launch

/**
 * The Chat panel's body: the chat's messages, oldest first, over a field to ask the agent in. The
 * host draws the title bar, the surface and the inset around it.
 *
 * **Open while the panel is shown**: leaving the panel drops it from the composition, which stops
 * collecting [AgentChat.transcript] and closes the socket; opening it again reads the chat back from
 * its file. jupyterlab-chat keeps a chat open while any client is, and frees it after (`chat_manager.py`).
 *
 * ```
 * ChatPanel(chat = FakeAgentChat(replies = mapOf("hello?" to "hi")))
 * ```
 */
@Composable
fun ChatPanel(chat: AgentChat, modifier: Modifier = Modifier) {
  var reading by remember(chat) { mutableStateOf<ChatReading>(ChatReading.Opening) }
  LaunchedEffect(chat) {
    try {
      chat.transcript().collect { reading = ChatReading.Open(it) }
    } catch (failure: AgentChatException) {
      reading = ChatReading.Failed(failure.message.orEmpty())
    }
  }
  val scope = rememberCoroutineScope()
  Column(modifier = modifier.fillMaxSize()) {
    when (val shown = reading) {
      ChatReading.Opening -> Spacer(Modifier.weight(1f))
      is ChatReading.Failed -> Text(
        shown.message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.weight(1f),
      )
      is ChatReading.Open -> Messages(shown.transcript, Modifier.weight(1f))
    }
    QuestionField(enabled = reading is ChatReading.Open) { question ->
      scope.launch {
        try {
          chat.ask(question)
        } catch (failure: AgentChatException) {
          reading = ChatReading.Failed(failure.message.orEmpty())
        }
      }
    }
  }
}

@Composable
private fun Messages(transcript: ChatTranscript, modifier: Modifier) {
  LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items(transcript.messages, key = ChatMessage::id) { message -> MessageRow(message) }
  }
}

/** Your question tinted, so it reads apart from the answer beneath it. */
@Composable
private fun MessageRow(message: ChatMessage) {
  Surface(
    color = if (message.fromMe) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(message.body, modifier = Modifier.padding(8.dp))
  }
}

/** The question being written; Send clears it and hands it to [onAsk]. */
@Composable
private fun QuestionField(enabled: Boolean, onAsk: (String) -> Unit) {
  var question by rememberSaveable { mutableStateOf("") }
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
    OutlinedTextField(
      value = question,
      onValueChange = { question = it },
      placeholder = { Text("Ask the agent") },
      modifier = Modifier.weight(1f).testTag("chat-input"),
    )
    Button(
      onClick = {
        onAsk(question)
        question = ""
      },
      enabled = enabled && question.isNotBlank(),
      modifier = Modifier.testTag("chat-send"),
    ) { Text("Send") }
  }
}

/** Where the chat stands. */
private sealed interface ChatReading {
  data object Opening : ChatReading

  data class Open(val transcript: ChatTranscript) : ChatReading

  data class Failed(val message: String) : ChatReading
}
