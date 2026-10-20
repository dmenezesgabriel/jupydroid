package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallStatus

/**
 * An agent's tool calls, one row each: a mark for where the call stands, then its title. This is
 * jupyter-chat-components' row, the one jupyter-ai's own frontend draws
 * (jupyter-chat-components@992c6fb `src/components/grouped-tool-calls.tsx`, `ToolCallRow`).
 *
 * ```
 * ToolCallRows(listOf(ToolCall(id = "c1", title = "probe.txt", status = ToolCallStatus.Completed)))
 * ```
 */
@Composable
internal fun ToolCallRows(calls: List<ToolCall>) {
  Column(modifier = Modifier.fillMaxWidth()) {
    calls.forEach { call -> ToolCallRow(call) }
  }
}

/** `style/base.css` pads the row 4 px by 8 px with an 8 px gap, the title at weight 500 in the second font colour. */
@Composable
private fun ToolCallRow(call: ToolCall) {
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("chat-tool-call"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(call.status.mark, color = call.status.markColor(muted))
    Text(call.title, color = muted, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
  }
}

/** `grouped-tool-calls.tsx`'s icons: a bullet while running, a check once completed, a cross once failed. */
private val ToolCallStatus.mark: String
  get() = when (this) {
    ToolCallStatus.Running -> "•"
    ToolCallStatus.Completed -> "✓"
    ToolCallStatus.Failed -> "✗"
  }

/**
 * A failed call's cross takes the error colour, as `base.css` gives it `--jp-error-color1`. The check
 * keeps the title's colour, where `base.css` makes it green: an extension draws from the Material
 * colour scheme, which has no success role.
 */
@Composable
private fun ToolCallStatus.markColor(muted: Color): Color =
  if (this == ToolCallStatus.Failed) MaterialTheme.colorScheme.error else muted
