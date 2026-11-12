package com.jupydroid.app.ui.shell

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.port.WorkspaceFiles
import com.jupydroid.app.theme.FocusRingTextButton
import com.jupydroid.extension.api.DeleteFileDialog
import kotlinx.coroutines.launch

/** How far a Delete's asking has got: the ask over one tab's document, or why the disk refused it. */
private sealed interface DeleteAsk {
  class ToDelete(val key: NavKey, val document: OpenDocument) : DeleteAsk

  class Refused(val message: String) : DeleteAsk
}

/**
 * The Delete a tab's menu starts: asks in the browser's words, deletes the file, and hands the tab
 * to [onDeleted] to close - `docmanager:delete`, whose `deleteFile` disposes every widget on the
 * file without the unsaved-work ask (jupyterlab@7f0e690 `packages/docmanager-extension/src/index.tsx`
 * `CommandIDs.del`, `packages/docmanager/src/manager.ts` `deleteFile`; ADR-0102). A refusal says
 * why, where the reference leaves it to the console.
 *
 * The ask is `DeleteFileDialog`'s "permanently delete", not docmanager's plainer "delete": the
 * delete is permanent (`WorkspaceFiles.delete`), and the explorer asks it in those words.
 *
 * Mounted once by the pane, like `rememberTabSaveAs`, so the dialogs outlive the menu that started
 * them. Answers with the starter.
 *
 * ```
 * val delete = rememberTabDelete(workspaceFiles) { key, document -> … }
 * delete(key, document)
 * ```
 */
@Composable
internal fun rememberTabDelete(
  workspaceFiles: WorkspaceFiles,
  onDeleted: (key: NavKey, document: OpenDocument) -> Unit,
): (NavKey, OpenDocument) -> Unit {
  var ask by remember { mutableStateOf<DeleteAsk?>(null) }
  val scope = rememberCoroutineScope()
  when (val open = ask) {
    is DeleteAsk.ToDelete -> DeleteFileDialog(
      name = open.document.path.substringAfterLast('/'),
      onDelete = {
        ask = null
        scope.launch {
          runCatching { workspaceFiles.delete(open.document.path) }
            .onSuccess { onDeleted(open.key, open.document) }
            .onFailure { ask = DeleteAsk.Refused(it.message ?: "could not delete '${open.document.path}'") }
        }
      },
      onDismiss = { ask = null },
    )
    is DeleteAsk.Refused -> AlertDialog(
      onDismissRequest = { ask = null },
      title = { Text("Delete Error") },
      text = { Text(open.message) },
      confirmButton = { FocusRingTextButton(onClick = { ask = null }) { Text("OK") } },
    )
    null -> Unit
  }
  return { key, document -> ask = DeleteAsk.ToDelete(key, document) }
}
