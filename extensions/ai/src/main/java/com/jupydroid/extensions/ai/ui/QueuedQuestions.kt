package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupydroid.extension.api.FocusRingIconButton

/**
 * The questions waiting their turn, above the input: how many, then one pill each, right-aligned, one
 * line each, as jupyter-chat-components draws its queue (jupyter-chat-components@992c6fb
 * `src/components/message-queue.tsx`, `style/base.css` `.jp-chat-message-queue-bubble`). Nothing while
 * none waits. A pill's pencil hands its question to [onEdit], and the pill of the question [editing]
 * holds it in a field instead, whose Save hands the question and what it became to [onSave]. A pill's ×
 * hands its question to [onRemove].
 *
 * ```
 * QueuedQuestions(
 *   queue.pending,
 *   editing = queue.held,
 *   onEdit = { queue = queue.editing(it) },
 *   onSave = { question, into -> queue = queue.edited(question, into) },
 *   onRemove = { queue = queue.removed(it) },
 * )
 * ```
 */
@Composable
internal fun QueuedQuestions(
  pending: List<String>,
  editing: String?,
  onEdit: (String) -> Unit,
  onSave: (String, String) -> Unit,
  onRemove: (String) -> Unit,
) {
  if (pending.isEmpty()) return
  // By place, so of two equal questions only the one the edit opened on shows a field.
  val editedAt = pending.indexOf(editing)
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("chat-queued"),
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      "${pending.size} queued",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    pending.forEachIndexed { at, question ->
      if (at == editedAt) {
        QueuedQuestionEdit(question, onSave)
      } else {
        QueuedQuestion(question, onEdit, onRemove)
      }
    }
  }
}

/**
 * A pill: its text, then its edit and remove buttons at the end, titled as the reference titles them.
 * The reference shows both only while the pointer hovers the pill (`.jp-chat-message-queue-edit`,
 * `.jp-chat-message-queue-remove`); a finger does not hover, so they are always there.
 */
@Composable
private fun QueuedQuestion(question: String, onEdit: (String) -> Unit, onRemove: (String) -> Unit) {
  QueuedPill {
    Text(
      question,
      style = MaterialTheme.typography.bodySmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false).padding(vertical = 4.dp),
    )
    PillButton(Icons.Filled.Edit, "Edit message") { onEdit(question) }
    PillButton(Icons.Filled.Close, "Remove from queue") { onRemove(question) }
  }
}

/**
 * The pill being edited: a field across the queue's width, focused with the caret at the end, and a
 * check titled "Save", with no ×, as the reference edits in place. Save, or the keyboard's Done as the
 * reference's Enter, puts what was written; it is off while that is blank, as Send is.
 */
@Composable
private fun QueuedQuestionEdit(question: String, onSave: (String, String) -> Unit) {
  var draft by remember(question) { mutableStateOf(TextFieldValue(question, TextRange(question.length))) }
  val savable = draft.text.isNotBlank()
  val save = { if (savable) onSave(question, draft.text) }
  val field = remember { FocusRequester() }
  LaunchedEffect(Unit) { field.requestFocus() }
  QueuedPill(Modifier.fillMaxWidth()) {
    BasicTextField(
      value = draft,
      onValueChange = { draft = it },
      textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      maxLines = 4,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { save() }),
      modifier = Modifier
        .weight(1f)
        .padding(vertical = 4.dp)
        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        .padding(horizontal = 4.dp, vertical = 2.dp)
        .focusRequester(field)
        .testTag("chat-queued-edit"),
    )
    PillButton(Icons.Filled.Check, "Save", enabled = savable, onClick = save)
  }
}

/** The reference's 16 px radius and its 4 px by 12 px padding, around [content] in a row. */
@Composable
private fun QueuedPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  val shape = RoundedCornerShape(16.dp)
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = shape,
    modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape).testTag("chat-queued-row"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = 12.dp, end = 4.dp),
      content = content,
    )
  }
}

/** Drawn at the reference's 16 px; Compose still takes a touch 48 dp around it. */
@Composable
private fun PillButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
  FocusRingIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.padding(start = 4.dp).size(16.dp)) {
    Icon(icon, contentDescription = description, modifier = Modifier.size(12.dp))
  }
}
