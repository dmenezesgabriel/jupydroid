package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.jupydroid.app.domain.model.ChatHarness
import com.jupydroid.app.domain.model.HarnessPresence
import com.jupydroid.app.domain.model.HarnessVersion
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.Twistie
import com.jupydroid.app.ui.editor.codeTextStyle
import com.jupydroid.app.ui.shell.MINIMUM_TAP_TARGET

/** The section's tag, for a test and - as a resource id - for a Maestro flow. */
const val SPACE_HARNESSES_TAG = "space-harnesses"

/**
 * Said once while any install runs: a cold install is measured in tens of seconds
 * (`ChatHarnessInstallProbeDeviceTest`), the section holds the screen on for it, and a stopped
 * run resumes on the next Install because the scripts are idempotent (`apk add`, `npm i -g`,
 * tarball overwrite under `app/src/main/assets/chat-harnesses/`). Pinned by
 * `SpaceSettingsDialogHarnessesTest.aRunningInstallShowsItsOutputAndSaysToKeepTheAppOpen`.
 */
internal const val HARNESS_KEEP_OPEN_HINT =
  "Keep Jupydroid open while an install runs - it is a download; run Install again to resume where it stopped"

/**
 * The Chat harnesses a stopped Space's Settings offers, one row per `CHAT_HARNESS_CATALOG` entry:
 * the live probe's answer beside the name, an expander that shows what the install adds and the
 * script's exact bytes - the same string [SpaceHarnessesViewModel] hands `SpaceShell`, since the
 * port's text is read once into the row's script state
 * (`SpaceHarnessesViewModelTest.anInstallRunsTheScriptBytesTheRowShows`).
 *
 * Install is offered only once the probe has answered that the harness is missing or unusable -
 * while the probe runs, or when it could not answer at all, there is nothing honest to install
 * over. A harness the probe finds installed offers nothing: the personas cache their classes per
 * server process, so a second install would change nothing Chat lists.
 *
 * ```
 * SpaceHarnessesSection(harnessesState) { harness -> harnessesViewModel.install(harness) }
 * ```
 */
@Composable
internal fun SpaceHarnessesSection(state: SpaceHarnessesState, onInstall: (ChatHarness) -> Unit) {
  val installing = state.harnesses.any { it.install is HarnessInstallState.Running }
  Column(
    Modifier
      .padding(top = Spacing.sm)
      .then(if (installing) Modifier.keepScreenOn() else Modifier)
      .semantics { testTagsAsResourceId = true }
      .testTag(SPACE_HARNESSES_TAG),
  ) {
    Text("Harnesses", style = MaterialTheme.typography.labelLarge)
    if (installing) Text(HARNESS_KEEP_OPEN_HINT, style = MaterialTheme.typography.bodySmall)
    state.harnesses.forEach { HarnessRow(it, onInstall) }
  }
}

@Composable
private fun HarnessRow(row: HarnessRowState, onInstall: (ChatHarness) -> Unit) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  Column(Modifier.padding(top = Spacing.xs)) {
    HarnessHeader(row, expanded, onToggle = { expanded = !expanded })
    if (expanded) HarnessDetails(row)
    HarnessInstallArea(row, onInstall)
  }
}

/**
 * The row's always-visible line: twistie, name, probe answer. One merged node with the expand and
 * collapse actions, the shape `app/src/main/java/com/jupydroid/app/ui/explorer/RecentsSection.kt`'s
 * heading keeps, so a flow has one string to address it by.
 */
@Composable
private fun HarnessHeader(row: HarnessRowState, expanded: Boolean, onToggle: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MINIMUM_TAP_TARGET)
      .semantics(mergeDescendants = true) {
        role = Role.Button
        stateDescription = if (expanded) EXPANDED else COLLAPSED
        if (expanded) {
          collapse {
            onToggle()
            true
          }
        } else {
          expand {
            onToggle()
            true
          }
        }
      }
      .clickable(onClick = onToggle),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Twistie(collapsed = !expanded, labelSize = MaterialTheme.typography.bodyMedium.fontSize)
    Text(
      text = row.harness.name,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f).padding(start = Spacing.sm),
    )
    Text(
      text = probeLabel(row.probe),
      style = MaterialTheme.typography.bodySmall,
      color = probeColor(row.probe),
    )
  }
}

/** What the probe said, in its own words where it could not answer - the port's message verbatim. */
private fun probeLabel(probe: HarnessProbeState): String = when (probe) {
  HarnessProbeState.Probing -> PROBING_LABEL
  is HarnessProbeState.Answered -> presenceLabel(probe.presence)
  is HarnessProbeState.Failed -> probe.message
}

private fun presenceLabel(presence: HarnessPresence): String = when (presence) {
  HarnessPresence.NotInstalled -> NOT_INSTALLED_LABEL
  is HarnessPresence.Installed -> presence.version?.let { "$INSTALLED_LABEL · $it" } ?: INSTALLED_LABEL
  is HarnessPresence.Incompatible -> incompatibleLabel(presence.version)
}

/**
 * An incompatible harness still runs, but the persona's own check would refuse it, so the row
 * says the Chat will not list it rather than repeating the version as if all were well.
 */
private fun incompatibleLabel(version: HarnessVersion?): String = when (version) {
  null -> "Installed, but the Chat cannot read its version"
  else -> "$INSTALLED_LABEL · $version - not a version the Chat accepts"
}

@Composable
private fun probeColor(probe: HarnessProbeState): Color {
  val unusable = probe is HarnessProbeState.Failed ||
    (probe is HarnessProbeState.Answered && probe.presence is HarnessPresence.Incompatible)
  return if (unusable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * The expander's body: what the install adds, then the script's bytes in the startup script's own
 * monospace - selectable, so a person can compare or copy them. Expanding runs nothing; the
 * script only ever runs through Install.
 */
@Composable
private fun HarnessDetails(row: HarnessRowState) {
  Column(Modifier.padding(top = Spacing.xs)) {
    Text(row.harness.whatItAdds, style = MaterialTheme.typography.bodySmall)
    when (val script = row.script) {
      HarnessScriptState.Loading -> Text(READING_SCRIPT_LABEL, style = MaterialTheme.typography.bodySmall)
      is HarnessScriptState.Failed -> {
        Text(script.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }
      is HarnessScriptState.Ready -> {
        Text(INSTALL_SCRIPT_LABEL, style = MaterialTheme.typography.labelMedium)
        SelectionContainer { Text(script.text, style = codeTextStyle()) }
      }
    }
  }
}

private const val PROBING_LABEL = "Checking…"
private const val NOT_INSTALLED_LABEL = "Not installed"
private const val INSTALLED_LABEL = "Installed"
private const val READING_SCRIPT_LABEL = "Reading the install script…"
private const val INSTALL_SCRIPT_LABEL = "Install script"
private const val EXPANDED = "Expanded"
private const val COLLAPSED = "Collapsed"
