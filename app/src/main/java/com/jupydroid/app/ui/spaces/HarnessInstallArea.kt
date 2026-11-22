package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.domain.model.ChatHarness
import com.jupydroid.app.domain.model.HarnessPresence
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.codeTextStyle
import com.jupydroid.extension.api.FocusRingTextButton

/** Install's accessible name on [name]'s row, for a test and a Maestro flow. */
internal fun installLabel(name: String): String = "Install $name"

/**
 * Where a harness row's install stands: the offer, the run's streamed tail, or how it failed -
 * with Install back for a retry, since the scripts are idempotent and a re-run resumes.
 * `SpaceHarnessesSection` draws it under every row, expanded or not, so a run's output is never
 * hidden behind the expander.
 */
@Composable
internal fun HarnessInstallArea(row: HarnessRowState, onInstall: (ChatHarness) -> Unit) {
  when (val install = row.install) {
    HarnessInstallState.Idle -> IdleInstall(row, onInstall)
    is HarnessInstallState.Running -> RunningInstall(install.lines)
    is HarnessInstallState.Failed -> FailedInstall(row.harness, install, onInstall)
    is HarnessInstallState.Refused -> RefusedInstall(row.harness, install.message, onInstall)
  }
}

/**
 * Install appears only for a probe that answered the harness is missing or unusable: `NotInstalled`
 * is the ordinary offer, `Incompatible` the repair (the script pins the version the persona
 * accepts). `Installed` offers nothing, and `Probing`/`Failed` offer nothing honest yet.
 */
@Composable
private fun IdleInstall(row: HarnessRowState, onInstall: (ChatHarness) -> Unit) {
  val presence = (row.probe as? HarnessProbeState.Answered)?.presence ?: return
  if (presence is HarnessPresence.Installed) return
  InstallButton(row.harness, onInstall)
}

@Composable
private fun RunningInstall(lines: List<String>) {
  Column(Modifier.padding(top = Spacing.xs)) {
    LinearProgressIndicator(Modifier.fillMaxWidth())
    if (lines.isEmpty()) return@Column
    val tail = lines.takeLast(INSTALL_TAIL_LINES)
    if (lines.size > tail.size) {
      Text("… ${lines.size - tail.size} earlier lines", style = MaterialTheme.typography.bodySmall)
    }
    SelectionContainer { Text(tail.joinToString("\n"), style = codeTextStyle()) }
  }
}

@Composable
private fun FailedInstall(harness: ChatHarness, failure: HarnessInstallState.Failed, onInstall: (ChatHarness) -> Unit) {
  Column(Modifier.padding(top = Spacing.xs)) {
    Text(
      "Install failed (exit ${failure.exitCode})",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
    )
    if (failure.excerpt.isNotEmpty()) {
      SelectionContainer { Text(failure.excerpt, style = codeTextStyle()) }
    }
    InstallButton(harness, onInstall)
  }
}

@Composable
private fun RefusedInstall(harness: ChatHarness, message: String, onInstall: (ChatHarness) -> Unit) {
  Column(Modifier.padding(top = Spacing.xs)) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    InstallButton(harness, onInstall)
  }
}

@Composable
private fun InstallButton(harness: ChatHarness, onInstall: (ChatHarness) -> Unit) {
  FocusRingTextButton(
    onClick = { onInstall(harness) },
    modifier = Modifier.semantics { contentDescription = installLabel(harness.name) },
  ) {
    Text("Install")
  }
}

/**
 * How many of a running install's newest lines stay on screen: enough tail to read an error in,
 * few enough that the newest line is still inside the dialog's fold - the dialog scrolls by hand,
 * so showing every line would leave the live end below it.
 */
private const val INSTALL_TAIL_LINES = 10
