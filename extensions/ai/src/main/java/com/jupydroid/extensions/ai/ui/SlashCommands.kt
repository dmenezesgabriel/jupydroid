package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jupydroid.extensions.ai.domain.AgentCommand
import com.jupydroid.extensions.ai.domain.completing

/**
 * The agent's commands [question] could still become, each named over what it does, above the field:
 * jupyter-chat opens its command menu at the input with each command's name and description
 * (jupyter-chat@2c224bc `packages/jupyter-chat/src/components/input/use-chat-commands.tsx`). Draws
 * nothing while none matches.
 *
 * At most two fifths of the window tall, and scrolled past that, as the MUI Autocomplete that menu is
 * drawn in caps its listbox at `40vh` (`@mui/material` `Autocomplete.js`, `AutocompleteListbox`):
 * opencode lists its skills among its commands, and on SM-X216B one description alone ran to four lines.
 *
 * ```
 * SlashCommands(transcript.agentCommands, question.text) { question = TextFieldValue(it.typed) }
 * ```
 */
@Composable
internal fun SlashCommands(offered: List<AgentCommand>, question: String, onPick: (AgentCommand) -> Unit) {
  val matching = offered.completing(question)
  if (matching.isEmpty()) return
  val tallest = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 2 / 5).toDp() }
  Column(Modifier.fillMaxWidth().testTag("chat-commands")) {
    Column(Modifier.heightIn(max = tallest).verticalScroll(rememberScrollState())) {
      matching.forEach { command -> SlashCommandRow(command) { onPick(command) } }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
  }
}

/** One command, a finger tall, its description beneath its name when the agent gives one. */
@Composable
private fun SlashCommandRow(command: AgentCommand, onClick: () -> Unit) {
  Column(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 6.dp),
  ) {
    Text(command.name, style = MaterialTheme.typography.bodyLarge)
    command.description?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
