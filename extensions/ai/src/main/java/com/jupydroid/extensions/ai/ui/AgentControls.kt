package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.jupydroid.extension.api.FocusRingDropdownMenuItem
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentPick
import com.jupydroid.extensions.ai.domain.ChatTranscript

/**
 * Which agent answers the next question, and with what ([chatControls]): a chip each while their whole names
 * fit beside Send, else one chip naming the model that opens them all ([ControlsChip]), as VS Code folds its
 * pickers into one on a phone (vscode@3f3380c
 * `src/vs/workbench/contrib/chat/browser/widget/input/chatPhoneInputPresenter.ts`).
 *
 * ```
 * AgentControls(transcript, pick) { pick = it }
 * ```
 *
 * @param pick what was picked for the next question; what it leaves out keeps the chat's and the agent's.
 */
@Composable
internal fun AgentControls(transcript: ChatTranscript, pick: AgentPick, onPick: (AgentPick) -> Unit) {
  val controls = chatControls(transcript, pick, onPick)
  if (controls.isEmpty()) return
  ChipRow(chips = { controls.forEach { AgentChoiceChip(it) } }, folded = { ControlsChip(controls) })
}

/**
 * [chips] side by side at the width their names need, centred on the row's height, while they fit; else
 * [folded] alone, so no chip is ever cut. A weighted `Row` held each chip to an equal part of the row, so a
 * long name was cut beside room the others left. jupyter-ai and VS Code keep a control whole while it fits
 * (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`, `ControlsRow`; vscode@3f3380c
 * `src/vs/workbench/contrib/chat/browser/widget/input/chatInputPickerResponsiveLayout.ts`).
 *
 * Fixes: 714399219c40 ("feat(ai): a mode chip beside the model chip names opencode's mode and picks the
 * next question's")
 */
@Composable
private fun ChipRow(chips: @Composable () -> Unit, folded: @Composable () -> Unit) {
  SubcomposeLayout { constraints ->
    // Measured, never placed, and kept out of the semantics tree: an unplaced chip still answers a screen
    // reader and a test.
    val whole = subcompose(ChipRowSlot.Measured) { Row(Modifier.clearAndSetSemantics {}) { chips() } }.single()
    val fits = whole.maxIntrinsicWidth(constraints.maxHeight) <= constraints.maxWidth
    val shown = if (fits) subcompose(ChipRowSlot.Separate, chips) else subcompose(ChipRowSlot.Folded, folded)
    val placeables = shown.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
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

/** What [ChipRow] composes: the chips to measure, then the chips or the one chip they fold into, to draw. */
private enum class ChipRowSlot { Measured, Separate, Folded }

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
 * One [control] named on a chip that opens the values it offers, the one in use checked. jupyter-ai labels
 * a control with its value's name (`currentControlLabel`).
 *
 * Its "Default (<current>)" row is not drawn: the persona applies a pick and keeps it with the chat
 * (jupyter_ai_acp_client 0.3.3 `base_acp_persona.py`, `_record_config_choice`, in the shipped rootfs),
 * so after one question "default" would name the pick.
 */
@Composable
private fun AgentChoiceChip(control: ChatControl) {
  var listed by remember { mutableStateOf(false) }
  val tallest = tallestList()
  Box {
    ChipButton(control.valueName, Modifier.testTag(control.chip.tag)) { listed = true }
    DropdownMenu(expanded = listed, onDismissRequest = { listed = false }) {
      ChoiceList(control, tallest) {
        listed = false
        control.onPick(it)
      }
    }
  }
}

/** A toolbar chip reading [name], ellipsized when the row is narrower, with the arrow that says it opens. */
@Composable
internal fun ChipButton(name: String, modifier: Modifier, onClick: () -> Unit) {
  FocusRingTextButton(onClick = onClick, modifier = modifier) {
    // Weighted, so a long name ellipsizes rather than pushing the arrow out of the button.
    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
  }
}

/**
 * How tall a chip's list may grow: jupyter-ai holds its list to `60vh` (jupyter-ai-persona-manager@d5ed04b
 * `style/base.css`). Read by the chip, not in the menu: the menu is a popup window of its own.
 */
@Composable
internal fun tallestList(): Dp = with(LocalDensity.current) {
  (LocalWindowInfo.current.containerSize.height * TALLEST_LIST_SHARE).toDp()
}

/** The share of the window's height a chip's list may take, as jupyter-ai's `60vh`. */
private const val TALLEST_LIST_SHARE = 0.6f

/**
 * The values [control] offers, at most [tallest], the one in use first and checked: under a search field
 * when its chip is searchable, which stays put while the values scroll beneath it. jupyter-ai opens on its
 * search field focused (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`, `ControlMenu`), as
 * VS Code's phone sheet does (vscode@3f3380c `src/vs/sessions/browser/parts/mobile/mobilePickerSheet.ts`).
 * Its query is gone once the list closes.
 */
@Composable
private fun ChoiceList(control: ChatControl, tallest: Dp, onPick: (String) -> Unit) {
  var query by remember { mutableStateOf("") }
  val chip = control.chip
  Column(Modifier.heightIn(max = tallest).testTag("${chip.tag}-list")) {
    if (chip.searchable) ChoiceSearch(query, "${chip.tag}-search", focused = true) { query = it }
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
      ChoiceRows(control, query, onPick)
    }
  }
}

/** A row for each value [control] offers that [query] matches (`AgentChoice.listing`), the one in use checked. */
@Composable
internal fun ChoiceRows(control: ChatControl, query: String, onPick: (String) -> Unit) {
  val inUse = control.choice.inUse(control.picked)
  val listing = control.choice.listing(control.picked, query)
  if (listing.isEmpty()) FocusRingDropdownMenuItem(text = { Text("No matches") }, onClick = {}, enabled = false)
  listing.forEach { option ->
    FocusRingDropdownMenuItem(
      text = { Text(option.name) },
      leadingIcon = { if (option.id == inUse) Icon(Icons.Filled.Check, contentDescription = "In use") },
      onClick = { onPick(option.id) },
    )
  }
}

/**
 * The field a searchable list opens on, [focused] so the keyboard is up to type the first letter where the
 * list is the model's alone.
 */
@Composable
internal fun ChoiceSearch(query: String, tag: String, focused: Boolean, onQuery: (String) -> Unit) {
  val focus = remember { FocusRequester() }
  TextField(
    value = query,
    onValueChange = onQuery,
    singleLine = true,
    placeholder = { Text("Search models") },
    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(tag),
  )
  if (focused) LaunchedEffect(focus) { focus.requestFocus() }
}
