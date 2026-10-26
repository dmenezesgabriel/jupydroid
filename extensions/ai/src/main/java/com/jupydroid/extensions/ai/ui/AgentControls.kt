package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.jupydroid.extensions.ai.domain.AgentModels

/**
 * The model the agent answers the next question with, named on a chip that opens the models it offers,
 * the one in use checked. jupyter-ai's Model control sits in the chat input's toolbar and is labelled
 * with its value's name (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`,
 * `currentControlLabel`); it draws nothing while the agent offers no models.
 *
 * Its "Default (<current>)" row is not drawn: the persona applies a picked model and keeps it with the
 * chat (jupyter_ai_acp_client 0.3.3 `base_acp_persona.py`, `_record_config_choice`, in the shipped
 * rootfs), so after one question "default" would name the pick.
 *
 * ```
 * ModelChip(transcript.agentModels, picked) { picked = it }
 * ```
 *
 * @param picked the model picked for the next question, or null to keep the agent's.
 */
@Composable
internal fun ModelChip(models: AgentModels, picked: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
  if (!models.choosable) return
  var listed by remember { mutableStateOf(false) }
  val inUse = models.inUse(picked)
  Box(modifier) {
    TextButton(onClick = { listed = true }, modifier = Modifier.testTag("chat-model")) {
      Text(models.nameInUse(picked).orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
      Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
    DropdownMenu(expanded = listed, onDismissRequest = { listed = false }) {
      models.offered.forEach { model ->
        DropdownMenuItem(
          text = { Text(model.name) },
          leadingIcon = { if (model.id == inUse) Icon(Icons.Filled.Check, contentDescription = "In use") },
          onClick = {
            listed = false
            onPick(model.id)
          },
        )
      }
    }
  }
}
