package com.jupydroid.extensions.git.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jupydroid.extensions.git.domain.GitStatusException
import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.domain.RepositoryStatus

/**
 * The Source Control panel's body: the branch of the directory the explorer shows, or why there is
 * none. The host draws the title bar, the surface and the inset around it.
 *
 * **Read when the panel opens**, keyed on [path]: leaving the panel drops it from the composition,
 * so opening it again reads again. jupyterlab-git polls on an interval instead (`refreshInterval`,
 * jupyterlab-git@b62a87b7 `src/model.ts`); a read per opening is what a panel nobody watches costs
 * nothing for.
 *
 * ```
 * SourceControlPanel(reader = FakeGitStatusReader(mapOf("demo" to "master")), path = "demo")
 * ```
 */
@Composable
fun SourceControlPanel(reader: GitStatusReader, path: String, modifier: Modifier = Modifier) {
  var reading by remember(reader, path) { mutableStateOf<StatusReading>(StatusReading.Pending) }
  LaunchedEffect(reader, path) {
    reading = try {
      StatusReading.Read(reader.status(path))
    } catch (failure: GitStatusException) {
      StatusReading.Failed(failure.message.orEmpty())
    }
  }
  Column(modifier = modifier.fillMaxWidth()) {
    when (val shown = reading) {
      StatusReading.Pending -> Unit
      is StatusReading.Failed -> Text(shown.message, color = MaterialTheme.colorScheme.error)
      is StatusReading.Read -> StatusLines(shown.status, path)
    }
  }
}

@Composable
private fun StatusLines(status: RepositoryStatus, path: String) {
  when (status) {
    is RepositoryStatus.NotARepository -> Text(notARepositoryMessage(path))
    is RepositoryStatus.Repository -> {
      // jupyterlab-git's toolbar captions the name "Current branch" (`src/components/Toolbar.tsx`).
      Text("Current branch", style = MaterialTheme.typography.labelMedium)
      Text(status.branch, style = MaterialTheme.typography.titleMedium)
    }
  }
}

/** Where one read of the status stands. */
private sealed interface StatusReading {
  data object Pending : StatusReading

  data class Read(val status: RepositoryStatus) : StatusReading

  data class Failed(val message: String) : StatusReading
}
