package com.jupydroid.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.MarginedAlertDialog

/**
 * "X has changed on disk" - what a save asks when the file is no longer the one that was opened.
 *
 * `Context._raiseConflict` (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`): title
 * *"File Changed"*, its body, and its three buttons in its own order - Cancel, the accepting
 * Revert, the **warn** Overwrite. Overwrite is the one that destroys something nobody here has
 * seen, which is why it carries the warning and Revert does not. Dismissing is Cancel: the save is
 * not made and the document goes on owing it.
 *
 * ```
 * if (state.saveConflict) FileChangedDialog(state.title, onOverwrite = { … }, onRevert = { … }, onCancel = { … })
 * ```
 */
@Composable
fun FileChangedDialog(name: String, onOverwrite: () -> Unit, onRevert: () -> Unit, onCancel: () -> Unit) {
  MarginedAlertDialog(
    onDismissRequest = onCancel,
    title = { Text(FILE_CHANGED_TITLE) },
    text = {
      Text(
        "\"$name\" has changed on disk since the last time it was opened or saved. Overwrite the file on " +
          "disk with the version open here, or load the version on disk (revert)?",
      )
    },
    confirmButton = {
      FocusRingTextButton(onClick = onOverwrite) { Text("Overwrite", color = MaterialTheme.colorScheme.error) }
    },
    dismissButton = {
      Row {
        FocusRingTextButton(onClick = onCancel) { Text("Cancel") }
        FocusRingTextButton(onClick = onRevert) { Text("Revert") }
      }
    },
  )
}

/** Named so a test and the dialog agree on one string. */
const val FILE_CHANGED_TITLE = "File Changed"
