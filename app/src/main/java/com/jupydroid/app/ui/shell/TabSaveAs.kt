package com.jupydroid.app.ui.shell

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.isValidWorkspacePath
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.theme.FocusRingTextButton
import com.jupydroid.app.ui.OverwriteFileDialog
import com.jupydroid.extension.api.RenameFileDialog
import com.jupydroid.extension.api.titleStemEnd
import kotlinx.coroutines.launch

/** How far a Save As's asking has got: the path, a taken one's overwrite, or why the disk refused. */
private sealed interface SaveAsAsk {
  class ForPath(val key: NavKey, val document: OpenDocument) : SaveAsAsk

  class ToOverwrite(val key: NavKey, val document: OpenDocument, val target: String) : SaveAsAsk

  class Refused(val message: String) : SaveAsAsk
}

/**
 * The Save As a tab's menu starts: asks the path, asks before a taken one is replaced, says why a
 * refused write failed, and hands a written copy to [onWritten] - which closes the tab and opens
 * the copy, as the reference's command does on the save's `fileChanged`
 * (jupyterlab@7f0e690 `packages/docmanager-extension/src/index.tsx`, `CommandIDs.saveAs`;
 * ADR-0099).
 *
 * Mounted once by the pane, like `rememberMainAreaCloseGate`, so the dialogs outlive the menu that
 * started them. Answers with the starter.
 *
 * ```
 * val saveAs = rememberTabSaveAs(workspaceRepository) { key, document, copy -> … }
 * saveAs(key, document)
 * ```
 */
@Composable
internal fun rememberTabSaveAs(
  workspaceRepository: WorkspaceRepository,
  onWritten: (key: NavKey, document: OpenDocument, copy: String) -> Unit,
): (NavKey, OpenDocument) -> Unit {
  var ask by remember { mutableStateOf<SaveAsAsk?>(null) }
  val scope = rememberCoroutineScope()
  val write = { key: NavKey, document: OpenDocument, target: String, overwrite: Boolean ->
    ask = null
    scope.launch {
      when (val outcome = saveDocumentAs(document, target, workspaceRepository, overwrite)) {
        SaveAsOutcome.Written -> onWritten(key, document, target)
        SaveAsOutcome.Taken -> ask = SaveAsAsk.ToOverwrite(key, document, target)
        is SaveAsOutcome.Refused -> ask = SaveAsAsk.Refused(outcome.message)
      }
    }
  }
  when (val pending = ask) {
    is SaveAsAsk.ForPath -> SaveAsPathDialog(
      path = pending.document.path,
      onConfirm = { write(pending.key, pending.document, it, false) },
      onDismiss = { ask = null },
    )
    is SaveAsAsk.ToOverwrite -> OverwriteFileDialog(
      name = pending.target,
      onOverwrite = { write(pending.key, pending.document, pending.target, true) },
      onDismiss = { ask = null },
    )
    is SaveAsAsk.Refused -> SaveAsRefusalDialog(pending.message) { ask = null }
    null -> Unit
  }
  return { key, document -> ask = SaveAsAsk.ForPath(key, document) }
}

/**
 * `Private.getSavePath`'s "Save File As…" over the whole path, so a copy can go into another
 * folder; the leaf's stem is selected, as the rename dialog selects a name's.
 */
@Composable
private fun SaveAsPathDialog(path: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
  RenameFileDialog(
    currentTitle = path,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    heading = "Save File As…",
    isValidName = ::isValidWorkspacePath,
    refusalMessage = INVALID_PATH_MESSAGE,
    initialSelection = { typed ->
      val leafStart = typed.length - notebookLeafOf(typed).length
      TextRange(leafStart, leafStart + titleStemEnd(notebookLeafOf(typed)))
    },
    confirmLabel = "Save",
    fieldLabel = "Path",
  )
}

/** `File Save Error for %1` - a refusal shown rather than lost, as the tab's rename shows its own. */
@Composable
private fun SaveAsRefusalDialog(message: String, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Save Error") },
    text = { Text(message) },
    confirmButton = { FocusRingTextButton(onClick = onDismiss) { Text("OK") } },
  )
}

private const val INVALID_PATH_MESSAGE =
  "Each name in the path must have nonzero length, cannot start with \".\", and cannot include \\ :"
