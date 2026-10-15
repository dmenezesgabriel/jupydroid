package com.jupydroid.app.ui.shell

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.port.WorkspaceFiles
import kotlinx.coroutines.launch

/**
 * The Duplicate a tab's menu starts: copies the document's file beside itself under the server's
 * `-Copy<n>` name and opens nothing - `docmanager:duplicate`, whose `DocumentManager.duplicate` is
 * `contents.copy(path, dirname)` (jupyterlab@7f0e690 `packages/docmanager-extension/src/index.tsx`
 * `CommandIDs.duplicate`, `packages/docmanager/src/manager.ts`). What is copied is the file as saved
 * (`WorkspaceFiles.copy`). A refusal says why, where the reference leaves it to the console.
 *
 * Mounted once by the pane, like `rememberTabDelete`, so the refusal outlives the menu that started
 * it. Answers with the starter.
 *
 * ```
 * val duplicate = rememberTabDuplicate(workspaceFiles)
 * duplicate(document)
 * ```
 */
@Composable
internal fun rememberTabDuplicate(workspaceFiles: WorkspaceFiles): (OpenDocument) -> Unit {
  var refusal by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  refusal?.let { message ->
    AlertDialog(
      onDismissRequest = { refusal = null },
      title = { Text("Duplicate Error") },
      text = { Text(message) },
      confirmButton = { TextButton(onClick = { refusal = null }) { Text("OK") } },
    )
  }
  return { document ->
    scope.launch {
      runCatching { workspaceFiles.copy(document.path) }
        .onFailure { refusal = it.message ?: "could not duplicate '${document.path}'" }
    }
  }
}
