package com.jupydroid.extension.api

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * "Are you sure you want to permanently delete: X?" - asked before anything is removed.
 *
 * Word for word the reference's (`packages/filebrowser/src/listing.ts:451-500`), including
 * **permanently**: `DirListing.delete` picks that wording when `delete_to_trash` is off, and an
 * app's private files on Android have no trash to go to. Nor does a file the Space's server
 * deletes: it starts with `delete_to_trash` off
 * (`app/src/main/java/com/jupydroid/app/data/space/JupyterServerLauncher.kt`). Winlator asks too
 * (`ContainerFileManagerFragment:353`, `ContentDialog.confirm`), so both references agree that
 * this is a question and not a menu item.
 *
 * The reference protects the answer with focus - `defaultButton: 0`, commented "By default focus
 * on 'Cancel' to protect from accidental deletion". A Compose dialog has no focused default, so
 * that protection is carried by colour instead: Delete is drawn in `error`, and Cancel is the
 * dismiss button, which is also what a tap outside and a back press choose.
 *
 * ```
 * deleting?.let { DeleteFileDialog(it.name, onDelete = { … }, onDismiss = { deleting = null }) }
 * ```
 */
@Composable
public fun DeleteFileDialog(name: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Delete") },
    text = { Text("Are you sure you want to permanently delete: $name?") },
    confirmButton = {
      FocusRingTextButton(onClick = onDelete) {
        Text("Delete", color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = { FocusRingTextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
