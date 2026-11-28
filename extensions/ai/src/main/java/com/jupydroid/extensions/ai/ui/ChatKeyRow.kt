package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.SymbolRowKeys
import kotlin.math.max
import kotlin.math.min

/**
 * The row of keys a question to the agent needs and the soft keyboard hides, drawn by the window
 * above the keyboard while the question has the caret (`KeyRowPublished`). The symbols are the
 * shared set (`SymbolRowKeys`) the notebook and terminal rows draw, so the three cannot drift - no
 * Tab or kernel-history pair, which the cell's row adds: a question is not indented and no kernel's
 * history is behind it. A chat is where `@` mentions someone in the room
 * (jupyter-chat@2c224bc `packages/jupyterlab-chat-extension/src/chat-commands/providers/user-mention.tsx`),
 * `/` starts an agent's slash command (jupyter-ai-acp-client@77016f6
 * `jupyter_ai_acp_client/default_acp_client.py`), a backtick opens code (`isCursorInsideCodeBlock`
 * in `packages/jupyter-chat/src/components/input/use-chat-commands.tsx`), and `:` an emoji
 * (`providers/emoji.ts`).
 *
 * Tagged as a resource id because Gboard draws its own keys with the same text, and a flow has to
 * tell this row's `@` from the keyboard's long-press one.
 *
 * ```
 * if (focused) KeyRowPublished { ChatKeyRow(onType = { question = question.typedFromKeyRow(it) }, onMoveCaret = …) }
 * ```
 */
@Composable
internal fun ChatKeyRow(onType: (String) -> Unit, onMoveCaret: (Int) -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag(CHAT_KEY_ROW_TAG),
  ) {
    // Scrolls rather than wraps, as the symbol row does: every key stays in the semantics tree.
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).navigationBarsPadding()) {
      SymbolRowKeys.forEach { key -> ChatKey(label = key) { onType(key) } }
      ChatKey(label = "←") { onMoveCaret(-1) }
      ChatKey(label = "→") { onMoveCaret(1) }
    }
  }
}

/**
 * Drawn in the question's own `bodyLarge`, so a key looks like the character it puts in the field.
 * A `TextButton` for Material's 48 dp touch target.
 */
@Composable
private fun ChatKey(label: String, onClick: () -> Unit) {
  FocusRingTextButton(onClick = onClick) { Text(text = label, style = MaterialTheme.typography.bodyLarge) }
}

/** [key] in place of the selection, or at the caret, with the caret after it - what a keyboard key does. */
internal fun TextFieldValue.typedFromKeyRow(key: String): TextFieldValue {
  val start = min(selection.start, selection.end)
  val end = max(selection.start, selection.end)
  return TextFieldValue(text.replaceRange(start, end, key), selection = TextRange(start + key.length))
}

/** The caret [by] characters along, stopping at either end of the question. */
internal fun TextFieldValue.caretMovedBy(by: Int): TextFieldValue =
  copy(selection = TextRange((selection.end + by).coerceIn(0, text.length)))

internal const val CHAT_KEY_ROW_TAG = "chat_key_row"
