package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jupydroid.extension.api.focusRing
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import com.jupydroid.extensions.ai.domain.ToolCallStatus
import kotlinx.coroutines.launch

/**
 * An agent's tool calls, one row each: a mark for where the call stands, then its title, then the
 * option chosen if it was asked about. One that waits on the user offers the agent's options beneath.
 * This is jupyter-chat-components' row, the one jupyter-ai's own frontend draws
 * (jupyter-chat-components@992c6fb `src/components/grouped-tool-calls.tsx`, `ToolCallRow`).
 *
 * ```
 * ToolCallRows(listOf(ToolCall(id = "c1", title = "probe.txt", status = ToolCallStatus.Completed))) { _, _ -> true }
 * ```
 *
 * @param onChoose answers a waiting call with an option; false when that failed, so it can be chosen again.
 */
@Composable
internal fun ToolCallRows(calls: List<ToolCall>, onChoose: suspend (ToolCall, PermissionOption) -> Boolean) {
  Column(modifier = Modifier.fillMaxWidth()) {
    calls.forEach { call -> ToolCallRow(call, onChoose) }
  }
}

/** `style/base.css` pads the row 4 px by 8 px with an 8 px gap, the title at weight 500 in the second font colour. */
@Composable
private fun ToolCallRow(call: ToolCall, onChoose: suspend (ToolCall, PermissionOption) -> Boolean) {
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("chat-tool-call")) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(call.shownStatus.mark, color = call.shownStatus.markColor(muted))
      Text(call.title, color = muted, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
      call.chosenName?.let { Text("- $it", color = muted, style = MaterialTheme.typography.bodyMedium) }
    }
    (call.permission as? ToolCallPermission.Pending)?.let { PermissionButtons(call, it.options, onChoose) }
  }
}

/**
 * `PermissionButtons`: "Allow?" after a tree branch, then a button per option labelled with its name,
 * every one disabled once one is pressed until the choice fails. Wrapped, since a side pane is
 * narrower than the three opencode sends.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionButtons(
  call: ToolCall,
  options: List<PermissionOption>,
  onChoose: suspend (ToolCall, PermissionOption) -> Boolean,
) {
  var submitting by remember(call.id) { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
    Text("\u2514\u2500 Allow?", color = MaterialTheme.colorScheme.onSurfaceVariant)
    options.forEach { option ->
      OutlinedButton(
        onClick = {
          submitting = true
          scope.launch { submitting = onChoose(call, option) }
        },
        enabled = !submitting,
        modifier = Modifier.testTag("chat-tool-call-option").focusRing(),
      ) { Text(option.name) }
    }
  }
}

/** `PermissionLabel`: the name of the option a resolved call was answered with. */
private val ToolCall.chosenName: String?
  get() = (permission as? ToolCallPermission.Resolved)?.chosen?.name

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
