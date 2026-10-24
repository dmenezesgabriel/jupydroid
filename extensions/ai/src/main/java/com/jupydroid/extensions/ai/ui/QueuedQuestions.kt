package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The questions waiting their turn, above the input: how many, then one pill each, right-aligned, one
 * line each, as jupyter-chat-components draws its queue (jupyter-chat-components@992c6fb
 * `src/components/message-queue.tsx`, `style/base.css` `.jp-chat-message-queue-bubble`). Nothing while
 * none waits. A pill's × hands its question to [onRemove].
 *
 * ```
 * QueuedQuestions(listOf("What is 2+2?"), onRemove = { queue = queue.removed(it) })
 * ```
 */
@Composable
internal fun QueuedQuestions(pending: List<String>, onRemove: (String) -> Unit) {
  if (pending.isEmpty()) return
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
    pending.forEach { QueuedQuestion(it, onRemove) }
  }
}

/**
 * A pill: the reference's 16 px radius and its 4 px by 12 px padding, and its remove button at the end,
 * titled as the reference titles it. The reference shows the × only while the pointer hovers the pill
 * (`.jp-chat-message-queue-remove`); a finger does not hover, so it is always there.
 */
@Composable
private fun QueuedQuestion(question: String, onRemove: (String) -> Unit) {
  val shape = RoundedCornerShape(16.dp)
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = shape,
    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape).testTag("chat-queued-row"),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
      Text(
        question,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false).padding(vertical = 4.dp),
      )
      // Drawn at the reference's 16 px; Compose still takes a touch 48 dp around it.
      IconButton(onClick = { onRemove(question) }, modifier = Modifier.padding(start = 4.dp).size(16.dp)) {
        Icon(Icons.Filled.Close, contentDescription = "Remove from queue", modifier = Modifier.size(12.dp))
      }
    }
  }
}
