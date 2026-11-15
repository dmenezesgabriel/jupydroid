package com.jupydroid.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.MarginedAlertDialog

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
 * state.nameTaken?.let { OverwriteFileDialog(it, onOverwrite = …, onDismiss = …) }
 * ```
 */
@Composable
fun OverwriteFileDialog(name: String, onOverwrite: () -> Unit, onDismiss: () -> Unit) {
  MarginedAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Overwrite file?") },
    text = { Text("\"$name\" already exists, overwrite?") },
    confirmButton = {
      FocusRingTextButton(onClick = onOverwrite) {
        Text("Overwrite", color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = { FocusRingTextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
