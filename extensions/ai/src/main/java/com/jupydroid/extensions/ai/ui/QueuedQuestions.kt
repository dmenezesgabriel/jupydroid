package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * none waits.
 *
 * ```
 * QueuedQuestions(listOf("What is 2+2?"))
 * ```
 */
@Composable
internal fun QueuedQuestions(pending: List<String>) {
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
    pending.forEach { QueuedQuestion(it) }
  }
}

/** A pill: the reference's 16 px radius and its 4 px by 12 px padding. */
@Composable
private fun QueuedQuestion(question: String) {
  val shape = RoundedCornerShape(16.dp)
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = shape,
    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape).testTag("chat-queued-row"),
  ) {
    Text(
      question,
      style = MaterialTheme.typography.bodySmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
  }
}
