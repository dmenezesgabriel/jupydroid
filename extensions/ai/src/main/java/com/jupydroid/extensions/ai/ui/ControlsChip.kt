package com.jupydroid.extensions.ai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val CONTROLS_TAG = "chat-controls"

/**
 * The one chip [controls] fold into where their chips do not fit: it names the model ([controlsName]), tells a
 * screen reader every value ([controlsLabel]), and opens one menu with a section per control. That is VS
 * Code's phone chip and sheet (vscode@3f3380c
 * `src/vs/workbench/contrib/chat/browser/widget/input/chatPhoneInputPresenter.ts`, `_updateTrigger`;
 * `src/vs/sessions/contrib/providers/agentHost/browser/mobile/mobileChatPhoneInputPresenter.ts`,
 * `showCombinedModeAndModelSheet`) with jupyter-ai's overflow menu (jupyter-ai-persona-manager@d5ed04b
 * `src/persona-controls.tsx`, `OverflowControlsMenu`). A pick closes it, so it costs the two taps a chip's does.
 *
 * ```
 * ControlsChip(chatControls(transcript, pick) { pick = it })
 * ```
 */
@Composable
internal fun ControlsChip(controls: List<ChatControl>) {
  var listed by remember { mutableStateOf(false) }
  val tallest = tallestList()
  val label = controlsLabel(controls)
  Box {
    ChipButton(controlsName(controls), Modifier.testTag(CONTROLS_TAG).semantics { contentDescription = label }) {
      listed = true
    }
    DropdownMenu(expanded = listed, onDismissRequest = { listed = false }) {
      ControlsList(controls, tallest) { listed = false }
    }
  }
}

/**
 * A section per control, at most [tallest]. The model's is last, as VS Code's sheet puts "Model" after
 * "Agent Mode": it can run to hundreds, and would push a mode's two values out of reach.
 */
@Composable
private fun ControlsList(controls: List<ChatControl>, tallest: Dp, onPicked: () -> Unit) {
  var query by remember { mutableStateOf("") }
  Column(Modifier.heightIn(max = tallest).verticalScroll(rememberScrollState()).testTag("$CONTROLS_TAG-list")) {
    controls.sortedBy { it.chip.searchable }.forEach { control ->
      ControlSection(control, query, onQuery = { query = it }) {
        onPicked()
        control.onPick(it)
      }
    }
  }
}

/**
 * [control]'s values under its label; the model's under a search field too, which is not focused: the
 * keyboard it raised would cover the other sections.
 */
@Composable
private fun ControlSection(control: ChatControl, query: String, onQuery: (String) -> Unit, onPick: (String) -> Unit) {
  Text(
    control.label,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() },
  )
  if (!control.chip.searchable) return ChoiceRows(control, query = "", onPick)
  ChoiceSearch(query, "$CONTROLS_TAG-search", focused = false, onQuery)
  ChoiceRows(control, query, onPick)
}
