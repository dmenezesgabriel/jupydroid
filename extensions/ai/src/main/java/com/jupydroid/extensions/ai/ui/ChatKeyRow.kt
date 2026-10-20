package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.max
import kotlin.math.min

/**
 * The row of keys a question to the agent needs and the soft keyboard hides, drawn by the window
 * above the keyboard while the question has the caret (`KeyRowPublished`).
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
      CHAT_KEYS.forEach { key -> ChatKey(label = key) { onType(key) } }
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
  TextButton(onClick = onClick) { Text(text = label, style = MaterialTheme.typography.bodyLarge) }
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

/**
 * What a question to an agent gives meaning to, less what Gboard's first page already types in one tap.
 *
 * Meaning, from the references: `@` mentions someone in the chat
 * (jupyter-chat@2c224bc `packages/jupyterlab-chat-extension/src/chat-commands/providers/user-mention.tsx`),
 * `/` starts an agent's slash command (jupyter-ai-acp-client@77016f6
 * `jupyter_ai_acp_client/default_acp_client.py`), a backtick opens code (`isCursorInsideCodeBlock`
 * in `packages/jupyter-chat/src/components/input/use-chat-commands.tsx`), and `:` an emoji
 * (`providers/emoji.ts`). `_ ( ) =` are how a question names code: `df.groupby()`, `x = 1`.
 *
 * Measured on the SM-X216B's Gboard, held either way up: one tap reaches only letters, digits, `,`
 * and `.`, so `.` is left to the keyboard. `@ : _ ( ) =` are long-presses there, and `/` and the
 * backtick are not on the first page at all.
 *
 * No Tab and no kernel-history pair, which the symbol row carries: a question is not indented, and
 * no kernel's history is behind it.
 */
private val CHAT_KEYS = listOf("@", "/", "`", ":", "_", "(", ")", "=")
