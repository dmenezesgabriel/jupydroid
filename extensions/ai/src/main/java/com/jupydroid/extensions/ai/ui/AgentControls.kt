package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChoice
import com.jupydroid.extensions.ai.domain.AgentPick
import com.jupydroid.extensions.ai.domain.ChatTranscript

/**
 * Which agent answers the next question, and with what: a chip for the agent, then, once there is one, a
 * chip for its model and one for each of its general settings (its mode among them), in the order
 * jupyter-ai builds its controls, its persona menu first (jupyter-ai-persona-manager@d5ed04b
 * `src/persona-controls.tsx`, `buildControls`). Each chip is as wide as its name while the row has room,
 * and only when it has not does the longest give way to an ellipsis, so on a phone's width they still share
 * the row with Send ([ChipRow]).
 *
 * ```
 * AgentControls(transcript, pick) { pick = it }
 * ```
 *
 * @param pick what was picked for the next question; what it leaves out keeps the chat's and the agent's.
 */
@Composable
internal fun AgentControls(transcript: ChatTranscript, pick: AgentPick, onPick: (AgentPick) -> Unit) {
  ChipRow {
    // With several agents and nothing to go by, the chip asks rather than guessing. jupyter-ai shows "No one"
    // there, but a question to no one is answered by none (jupyter-ai-persona-manager@d5ed04b
    // `jupyter_ai_persona_manager/persona_manager.py`, `on_chat_message`), so nothing is sent until a pick.
    val agents = transcript.agents
    val agentChip = ChoiceChip("chat-agent", searchable = false, unchosen = "Choose agent")
    AgentChoiceChip(agents, pick.agent, agentChip) { onPick(pick.withAgent(it)) }
    if (pick.agentIn(transcript) == null) return@ChipRow
    val selection = pick.selection
    // Only the models are searched, as VS Code searches its model picker and not its mode picker: an agent
    // signed in to OpenRouter offers hundreds of models, and a setting two or three values.
    AgentChoiceChip(transcript.agentModels, selection.model, ChoiceChip("chat-model", searchable = true)) {
      onPick(pick.copy(selection = selection.copy(model = it)))
    }
    transcript.agentSettings.forEach { setting ->
      val chip = ChoiceChip("chat-setting-${setting.id}", searchable = false)
      AgentChoiceChip(setting, selection.settings[setting.id], chip) {
        onPick(pick.copy(selection = selection.copy(settings = selection.settings + (setting.id to it))))
      }
    }
  }
}

/**
 * [chips] side by side, centred on the row's height, each as wide as [chipWidths] lets it be. A weighted
 * `Row` held each chip to an equal part of the row, so a long name was cut beside room the others left.
 * jupyter-ai and VS Code keep a control whole while it fits (jupyter-ai-persona-manager@d5ed04b
 * `src/persona-controls.tsx`, `ControlsRow`; vscode@3f3380c
 * `src/vs/workbench/contrib/chat/browser/widget/input/chatInputPickerResponsiveLayout.ts`).
 *
 * Fixes: 7aa8cfb98a80 ("feat(ai): a mode chip beside the model chip names opencode's mode and picks the
 * next question's")
 */
@Composable
private fun ChipRow(chips: @Composable () -> Unit) {
  Layout(chips) { measurables, constraints ->
    val widths = chipWidths(measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }, constraints.maxWidth)
    val placeables = measurables.mapIndexed { chip, measurable ->
      measurable.measure(Constraints(maxWidth = widths[chip], maxHeight = constraints.maxHeight))
    }
    val height = placeables.maxOfOrNull { it.height } ?: 0
    layout(placeables.sumOf { it.width }, height) {
      var left = 0
      placeables.forEach { chip ->
        chip.placeRelative(left, (height - chip.height) / 2)
        left += chip.width
      }
    }
  }
}

/**
 * The agent [open] asks with [pick], told to [chat] whenever it changes and again on every opening: a chat
 * opened again shows nobody's state until it is told whose. Null while the chat is not open, or asks no one.
 */
@Composable
internal fun selectedAgent(
  chat: AgentChat,
  open: ChatTranscript?,
  pick: AgentPick,
  attempt: (suspend () -> Unit) -> Unit,
): String? {
  val agent = open?.let(pick::agentIn)
  LaunchedEffect(open != null, agent) { agent?.let { attempt { chat.select(it) } } }
  return agent
}

/**
 * A chip's [tag], which its list and search field extend, whether its list opens on a search field, and
 * what it says while nothing is in use.
 */
private data class ChoiceChip(val tag: String, val searchable: Boolean, val unchosen: String = "")

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
private fun AgentChoiceChip(choice: AgentChoice, picked: String?, chip: ChoiceChip, onPick: (String) -> Unit) {
  if (!choice.choosable) return
  var listed by remember { mutableStateOf(false) }
  // Read here, not in the menu: the menu is a popup window of its own.
  val tallest = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 3 / 5).toDp() }
  Box {
    TextButton(onClick = { listed = true }, modifier = Modifier.testTag(chip.tag)) {
      // Weighted, so a long name ellipsizes rather than pushing the arrow out of the button.
      Text(
        choice.nameInUse(picked) ?: chip.unchosen,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
    DropdownMenu(expanded = listed, onDismissRequest = { listed = false }) {
      ChoiceList(choice, picked, chip, tallest) {
        listed = false
        onPick(it)
      }
    }
  }
}

/**
 * The values [choice] offers, at most [tallest], the one in use first and checked: under a search field
 * when the [chip] is searchable, which stays put while the values scroll beneath it. jupyter-ai holds its
 * list to `60vh` (jupyter-ai-persona-manager@d5ed04b `style/base.css`) and opens on its search field
 * focused (`ControlMenu`), as VS Code's phone sheet does (vscode@3f3380c
 * `src/vs/sessions/browser/parts/mobile/mobilePickerSheet.ts`). Its query is gone once the list closes.
 */
@Composable
private fun ChoiceList(choice: AgentChoice, picked: String?, chip: ChoiceChip, tallest: Dp, onPick: (String) -> Unit) {
  var query by remember { mutableStateOf("") }
  val inUse = choice.inUse(picked)
  val listing = choice.listing(picked, query)
  Column(Modifier.heightIn(max = tallest).testTag("${chip.tag}-list")) {
    if (chip.searchable) ChoiceSearch(query, "${chip.tag}-search") { query = it }
    if (listing.isEmpty()) DropdownMenuItem(text = { Text("No matches") }, onClick = {}, enabled = false)
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
      listing.forEach { option ->
        DropdownMenuItem(
          text = { Text(option.name) },
          leadingIcon = { if (option.id == inUse) Icon(Icons.Filled.Check, contentDescription = "In use") },
          onClick = { onPick(option.id) },
        )
      }
    }
  }
}

/** The field a searchable list opens on, focused so the keyboard is up to type the first letter. */
@Composable
private fun ChoiceSearch(query: String, tag: String, onQuery: (String) -> Unit) {
  val focus = remember { FocusRequester() }
  TextField(
    value = query,
    onValueChange = onQuery,
    singleLine = true,
    placeholder = { Text("Search models") },
    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(tag),
  )
  LaunchedEffect(focus) { focus.requestFocus() }
}
