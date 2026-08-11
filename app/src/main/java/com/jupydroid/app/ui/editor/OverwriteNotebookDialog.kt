package com.jupydroid.app.ui.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * "…already exists, overwrite?" - the question the reference asks before replacing a file.
 *
 * `shouldOverwrite` (`packages/docmanager/src/dialogs.ts`): title "Overwrite file?", body
 * `"%1" already exists, overwrite?`, a cancel button and a **warn**-styled Overwrite. Asked only
 * for a name already taken - `renameFile` rethrows anything that is not a 409 - so an invalid
 * name never reaches here.
 *
 * The warn colour is not decoration: this is the only confirm button in the app that destroys a
 * file, and it should not look like the one that merely renames it.
 *
 * ```
 * state.nameTaken?.let { OverwriteNotebookDialog(it, onOverwrite = …, onDismiss = …) }
 * ```
 */
@Composable
internal fun OverwriteNotebookDialog(name: String, onOverwrite: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Overwrite notebook?") },
    text = { Text("\"$name\" already exists, overwrite?") },
    confirmButton = {
      TextButton(onClick = onOverwrite) {
        Text("Overwrite", color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
