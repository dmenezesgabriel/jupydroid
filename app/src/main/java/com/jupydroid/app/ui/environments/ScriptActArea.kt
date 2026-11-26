package com.jupydroid.app.ui.environments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.codeTextStyle

/**
 * Where the panel's one streamed script act stands: the run's streamed tail while it works, or
 * how it failed - an environment's create or an interpreter's register, the same shape either
 * way. [runningLabel] and [failedLabel] name the act the way its button did ("Creating
 * environment for proj-a", "Register failed for /usr/bin/python3"), the exit code appended to
 * the second. There is no retry button: the script refuses what its own earlier run left, so the
 * terminal is the retry.
 */
@Composable
internal fun ScriptActArea(act: ScriptActState, runningLabel: String, failedLabel: String) {
  when (act) {
    is ScriptActState.Running -> RunningAct(act, runningLabel)
    is ScriptActState.Failed -> FailedAct(act, failedLabel)
    is ScriptActState.Refused -> RefusedAct(act)
  }
}

@Composable
private fun RunningAct(act: ScriptActState.Running, runningLabel: String) {
  Column(Modifier.padding(top = Spacing.sm)) {
    LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(runningLabel, style = MaterialTheme.typography.bodySmall)
    if (act.lines.isEmpty()) return@Column
    val tail = act.lines.takeLast(ACT_TAIL_LINES)
    if (act.lines.size > tail.size) {
      Text("… ${act.lines.size - tail.size} earlier lines", style = MaterialTheme.typography.bodySmall)
    }
    SelectionContainer { Text(tail.joinToString("\n"), style = codeTextStyle()) }
  }
}

@Composable
private fun FailedAct(act: ScriptActState.Failed, failedLabel: String) {
  Column(Modifier.padding(top = Spacing.sm)) {
    Text(
      "$failedLabel (exit ${act.exitCode})",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
    )
    if (act.excerpt.isNotEmpty()) {
      SelectionContainer { Text(act.excerpt, style = codeTextStyle()) }
    }
  }
}

@Composable
private fun RefusedAct(act: ScriptActState.Refused) {
  Text(
    act.message,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.padding(top = Spacing.sm),
  )
}

/**
 * How many of a running act's newest lines stay on screen: enough tail to read an error in,
 * few enough that the newest line is still inside the panel's fold - `INSTALL_TAIL_LINES`'s own
 * bound in `app/src/main/java/com/jupydroid/app/ui/spaces/HarnessInstallArea.kt`.
 */
private const val ACT_TAIL_LINES = 10
