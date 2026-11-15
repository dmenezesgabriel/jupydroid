package com.jupydroid.app.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.jupydroid.app.OpenDocument
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.MarginedAlertDialog

/**
 * `docmanager:reload` as something to press: the document on top is read back from its file.
 *
 * **Beside [ActiveTabSave], for its reason**: the file editor has no bar of its own, and the strip
 * is the chrome a notebook and a file share (ADR-0096). IntelliJ draws the same act as a Refresh
 * icon (intellij-community@406a27a8da `platform/platform-resources/src/idea/PlatformActions.xml`,
 * `Synchronize`).
 *
 * **Live for any document**, jupyterlab's `isEnabled` - a widget with a context - rather than VS
 * Code's "only while dirty": VS Code can afford that because a watcher re-reads a clean model for
 * it, and here nothing does, so a clean document is exactly the stale one. Greyed for a tab that
 * is not a document, so the strip keeps its shape.
 *
 * **A document with unsaved work is asked about first**, with the command's own dialog and words
 * (jupyterlab@7f0e690 `packages/docmanager-extension/src/index.tsx`, `CommandIDs.reload`): the
 * reload throws that work away, so Reload carries the warning colour.
 *
 * ```
 * ActiveTabReload(title = "notes.py", document = openDocuments.openedAs(DocumentOpening("notes.py")), height = 34.dp)
 * ```
 */
@Composable
internal fun ActiveTabReload(title: String, document: OpenDocument?, height: Dp, modifier: Modifier = Modifier) {
  var asking by remember(document) { mutableStateOf(false) }
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(width = TRAILING_SLOT_WIDTH, height = height)
      .semantics { contentDescription = "Reload tab: $title" }
      .clickable(enabled = document != null, role = Role.Button) {
        if (document?.dirty?.value == true) asking = true else document?.reloadFromDisk()
      },
  ) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Icon(
      imageVector = Icons.Filled.Refresh,
      contentDescription = null,
      tint = if (document != null) tint else tint.copy(alpha = DISABLED_ICON_ALPHA),
      modifier = Modifier.size(TRAILING_ICON),
    )
  }
  if (asking && document != null) {
    ReloadFromDiskDialog(
      documentType = documentTypeOf(document.path),
      onReload = {
        asking = false
        document.reloadFromDisk()
      },
      onCancel = { asking = false },
    )
  }
}

/** The command's dirty guard: Cancel, and the warning Reload, in its own order. */
@Composable
private fun ReloadFromDiskDialog(documentType: String, onReload: () -> Unit, onCancel: () -> Unit) {
  MarginedAlertDialog(
    onDismissRequest = onCancel,
    title = { Text("Reload $documentType from Disk") },
    text = { Text("Are you sure you want to reload the $documentType from the disk?") },
    confirmButton = {
      FocusRingTextButton(onClick = onReload) { Text("Reload", color = MaterialTheme.colorScheme.error) }
    },
    dismissButton = { FocusRingTextButton(onClick = onCancel) { Text("Cancel") } },
  )
}

/**
 * jupyterlab's `fileType` names the first registered file type's `displayName`; with no registry
 * here, the editor is chosen by `.ipynb` alone, so that is the one distinction there is to name.
 */
internal fun documentTypeOf(path: String): String = if (path.endsWith(".ipynb")) "Notebook" else "File"
