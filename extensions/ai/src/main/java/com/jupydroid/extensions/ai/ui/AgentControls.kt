package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.jupydroid.extensions.ai.domain.AgentChoice
import com.jupydroid.extensions.ai.domain.AgentSelection
import com.jupydroid.extensions.ai.domain.ChatTranscript

/**
 * What the agent answers the next question with: a chip for its model, then one for each of its general
 * settings (its mode among them), in the order jupyter-ai builds its controls
 * (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`, `buildControls`). Each chip shrinks
 * to an ellipsis before the row overflows, so on a phone's width they still share it with Send.
 *
 * ```
 * AgentControls(transcript, selection) { selection = it }
 * ```
 *
 * @param selection what was picked for the next question; what it leaves out keeps the agent's.
 */
@Composable
internal fun AgentControls(transcript: ChatTranscript, selection: AgentSelection, onSelect: (AgentSelection) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    val shrinking = Modifier.weight(1f, fill = false)
    AgentChoiceChip(transcript.agentModels, selection.model, "chat-model", shrinking) {
      onSelect(selection.copy(model = it))
    }
    transcript.agentSettings.forEach { setting ->
      AgentChoiceChip(setting, selection.settings[setting.id], "chat-setting-${setting.id}", shrinking) {
        onSelect(selection.copy(settings = selection.settings + (setting.id to it)))
      }
    }
  }
}

/**
 * One [choice] named on a chip that opens the values it offers, the one in use checked. jupyter-ai labels
 * a control with its value's name (`currentControlLabel`), and draws nothing while it offers no values.
 *
 * Its "Default (<current>)" row is not drawn: the persona applies a pick and keeps it with the chat
 * (jupyter_ai_acp_client 0.3.3 `base_acp_persona.py`, `_record_config_choice`, in the shipped rootfs),
 * so after one question "default" would name the pick.
 *
 * @param picked the value picked for the next question, or null to keep the agent's.
 */
@Composable
private fun AgentChoiceChip(
  choice: AgentChoice,
  picked: String?,
  tag: String,
  modifier: Modifier,
  onPick: (String) -> Unit,
) {
  if (!choice.choosable) return
  var listed by remember { mutableStateOf(false) }
  val inUse = choice.inUse(picked)
  Box(modifier) {
    TextButton(onClick = { listed = true }, modifier = Modifier.testTag(tag)) {
      // Weighted, so a long name ellipsizes rather than pushing the arrow out of the button.
      Text(
        choice.nameInUse(picked).orEmpty(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
    DropdownMenu(expanded = listed, onDismissRequest = { listed = false }) {
      choice.offered.forEach { option ->
        DropdownMenuItem(
          text = { Text(option.name) },
          leadingIcon = { if (option.id == inUse) Icon(Icons.Filled.Check, contentDescription = "In use") },
          onClick = {
            listed = false
            onPick(option.id)
          },
        )
      }
    }
  }
}
