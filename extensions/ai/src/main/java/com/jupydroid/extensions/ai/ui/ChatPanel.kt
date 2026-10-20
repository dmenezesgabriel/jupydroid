package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jupydroid.extension.api.KeyRowPublished
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatReading
import com.jupydroid.extensions.ai.domain.ChatTranscript
import com.jupydroid.extensions.ai.domain.readings
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The Chat panel's body: the chat's messages, oldest first, over a field to ask the agent in. The
 * host draws the title bar, the surface and the inset around it.
 *
 * **Open while the panel is shown**: leaving the panel drops it from the composition, which stops
 * collecting [AgentChat.transcript] and closes the socket; opening it again reads the chat back from
 * its file. jupyterlab-chat keeps a chat open while any client is, and frees it after (`chat_manager.py`).
 * A chat that drops while shown opens again on its own ([readings]), its last messages still drawn.
 *
 * ```
 * ChatPanel(chat = FakeAgentChat(replies = mapOf("hello?" to "hi")))
 * ```
 */
@Composable
fun ChatPanel(chat: AgentChat, modifier: Modifier = Modifier) {
  var reading by remember(chat) { mutableStateOf<ChatReading>(ChatReading.Opening) }
  LaunchedEffect(chat) { chat.readings().collect { reading = it } }
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
      is ChatReading.Reconnecting -> {
        Messages(shown.transcript, Modifier.weight(1f))
        Text(
          "Reconnecting: ${shown.reason}",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("chat-reconnecting"),
        )
      }
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
  val shown = transcript.messagesWithText
  val list = rememberLazyListState()
  FollowTheNewest(list, shown)
  LazyColumn(
    state = list,
    modifier = modifier.fillMaxWidth().testTag("chat-messages"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(shown, key = ChatMessage::id) { message -> MessageRow(message) }
  }
}

/**
 * Keeps the newest message's last line in view as it arrives and as an agent streams more of it,
 * until the reader scrolls away from the end, and again once they scroll back. That is jupyter-chat's
 * rule: it opens at the end and follows only while the reader is there
 * (jupyter-chat@9e02bb70 `packages/jupyter-chat/src/components/messages/messages.tsx`).
 */
@Composable
private fun FollowTheNewest(list: LazyListState, shown: List<ChatMessage>) {
  var following by remember(list) { mutableStateOf(true) }
  LaunchedEffect(list) {
    // Judged whenever the list moves, by the reader or by the scroll below. An answer growing beneath
    // the end does not move it, so it does not end the following. The first position is the one
    // before any layout, which says nothing about the reader.
    snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
      .drop(1)
      .collect { following = !list.canScrollForward }
  }
  val newest = shown.lastOrNull()
  LaunchedEffect(newest?.id, newest?.body?.length) {
    // An offset past the item's end is clamped to the list's end, which is the item's last line.
    if (following && newest != null) list.scrollToItem(shown.lastIndex, Int.MAX_VALUE)
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

/**
 * The question being written; Send clears it and hands it to [onAsk]. One bordered box, the field
 * above and Send in a row beneath it, as jupyter-chat draws its input
 * (jupyter-chat@2c224bc `packages/jupyter-chat/src/components/input/chat-input.tsx`).
 *
 * While the field has the caret it publishes [ChatKeyRow] to the window, as a cell publishes its
 * symbol row. The question is a [TextFieldValue] rather than a `String` so a key can go in at the caret.
 */
@Composable
private fun QuestionField(enabled: Boolean, onAsk: (String) -> Unit) {
  var question by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
  var focused by remember { mutableStateOf(false) }
  if (focused) {
    KeyRowPublished {
      ChatKeyRow(
        onType = { key -> question = question.typedFromKeyRow(key) },
        onMoveCaret = { by -> question = question.caretMovedBy(by) },
      )
    }
  }
  val shape = RoundedCornerShape(8.dp)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outline, shape)
      .clip(shape)
      .testTag("chat-input-box"),
  ) {
    QuestionText(question, onChange = { question = it }, onFocusChange = { focused = it })
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    // 8 dp, and the button's 48 dp touch target adds 4 dp around its 40 dp face: the face sits
    // 12 dp in, as jupyter-chat's toolbar pads its 24 px button.
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
      SendButton(enabled = enabled && question.text.isNotBlank()) {
        onAsk(question.text)
        question = TextFieldValue()
      }
    }
  }
}

/** The field itself: no underline and no fill, since the box around it is the border. */
@Composable
private fun QuestionText(
  question: TextFieldValue,
  onChange: (TextFieldValue) -> Unit,
  onFocusChange: (Boolean) -> Unit,
) {
  val interactions = remember { MutableInteractionSource() }
  BasicTextField(
    value = question,
    onValueChange = onChange,
    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    maxLines = 10,
    interactionSource = interactions,
    modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) }.testTag("chat-input"),
  ) { field ->
    TextFieldDefaults.DecorationBox(
      value = question.text,
      innerTextField = field,
      enabled = true,
      singleLine = false,
      visualTransformation = VisualTransformation.None,
      interactionSource = interactions,
      placeholder = { Text("Ask the agent") },
      contentPadding = PaddingValues(12.dp),
      container = {},
    )
  }
}

/**
 * jupyter-chat's send is an up arrow on the brand fill with a 2 px radius (`send-button.tsx`,
 * `theme-provider.ts`). The fill here is `primaryContainer`, the app's button fill, as `primary` is
 * its link colour (`app/src/main/java/com/jupydroid/app/theme/Theme.kt`).
 */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
  FilledIconButton(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(2.dp),
    colors = IconButtonDefaults.filledIconButtonColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
    modifier = Modifier.testTag("chat-send"),
  ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Send") }
}
