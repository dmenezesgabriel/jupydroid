package com.jupydroid.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jupydroid.app.theme.FocusRingTextButton

/**
 * "Save changes in X before closing?" - what a dirty document asks on its way out.
 *
 * Word for word `saveBeforeClose` (`packages/docmanager/src/dialogs.ts:155-180`): title
 * *"Save your work"*, body `Save changes in "%1" before closing?`, and the three buttons in the
 * reference's own order - Cancel, the **warn** Discard, the accepting Save. The caller's close
 * maps to `onClose` (`widgetmanager.ts:342-388`): `onSave` must write first and only dismiss the
 * screen if nothing is left owed - a save that cannot be made keeps the document open, exactly as
 * a `context.save()` rejection does there. `writable` is always true here - every document lives
 * on the app's own files - so the label is "Save", never the "Save as" arm.
 *
 * ```
 * if (asking) SaveBeforeCloseDialog(state.title, onSave = { … }, onDiscard = { … }, onCancel = { … })
 * ```
 */
@Composable
fun SaveBeforeCloseDialog(name: String, onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit) {
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text("Save your work") },
    text = { Text("Save changes in \"$name\" before closing?") },
    // The reference's own order: Cancel and the warn Discard on the dismiss side, the accepting
    // Save last - `Dialog.cancelButton()`, `Dialog.warnButton(Discard)`, `Dialog.okButton(Save)`.
    confirmButton = { FocusRingTextButton(onClick = onSave) { Text("Save") } },
    dismissButton = {
      Row {
        FocusRingTextButton(onClick = onCancel) { Text("Cancel") }
        FocusRingTextButton(onClick = onDiscard) {
          Text("Discard", color = MaterialTheme.colorScheme.error)
        }
      }
    },
  )
}
