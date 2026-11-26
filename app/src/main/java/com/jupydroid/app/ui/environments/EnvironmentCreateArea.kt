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
 * Where the panel's one create stands: the run's streamed tail while it works, or how it failed.
 * There is no retry button - the script refuses a project whose `.venv` already exists, and a
 * stopped run leaves exactly that behind, so the terminal's `rm -rf` is the retry
 * (`SpaceEnvironmentCreation`'s script asset).
 */
@Composable
internal fun EnvironmentCreateArea(create: EnvironmentCreateState) {
  when (create) {
    is EnvironmentCreateState.Running -> RunningCreate(create)
    is EnvironmentCreateState.Failed -> FailedCreate(create)
    is EnvironmentCreateState.Refused -> RefusedCreate(create)
  }
}

@Composable
private fun RunningCreate(create: EnvironmentCreateState.Running) {
  Column(Modifier.padding(top = Spacing.sm)) {
    LinearProgressIndicator(Modifier.fillMaxWidth())
    Text("Creating environment for ${create.project}", style = MaterialTheme.typography.bodySmall)
    if (create.lines.isEmpty()) return@Column
    val tail = create.lines.takeLast(CREATE_TAIL_LINES)
    if (create.lines.size > tail.size) {
      Text("… ${create.lines.size - tail.size} earlier lines", style = MaterialTheme.typography.bodySmall)
    }
    SelectionContainer { Text(tail.joinToString("\n"), style = codeTextStyle()) }
  }
}

@Composable
private fun FailedCreate(create: EnvironmentCreateState.Failed) {
  Column(Modifier.padding(top = Spacing.sm)) {
    Text(
      "Create failed for ${create.project} (exit ${create.exitCode})",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
    )
    if (create.excerpt.isNotEmpty()) {
      SelectionContainer { Text(create.excerpt, style = codeTextStyle()) }
    }
  }
}

@Composable
private fun RefusedCreate(create: EnvironmentCreateState.Refused) {
  Text(
    create.message,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.padding(top = Spacing.sm),
  )
}

/**
 * How many of a running create's newest lines stay on screen: enough tail to read an error in,
 * few enough that the newest line is still inside the panel's fold - `INSTALL_TAIL_LINES`'s own
 * bound in `app/src/main/java/com/jupydroid/app/ui/spaces/HarnessInstallArea.kt`.
 */
private const val CREATE_TAIL_LINES = 10
