package com.jupydroid.app.ui.terminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.MarginedAlertDialog

/**
 * "Open Link?" - asked before a web address the terminal printed leaves the app.
 *
 * Word for word JupyterLab's `Private.activateLink` (jupyterlab@7f0e6906
 * packages/terminal/src/widget.ts): a program, not the user, chose what was printed, so the address
 * is named in full before it is followed. Open is the reference's warn button, drawn in `error` as
 * `DeleteFileDialog`'s Delete is; Cancel is the dismiss button, which a tap outside and a back press
 * choose too.
 *
 * ```
 * tappedLink?.let { TerminalOpenLinkDialog(it, onOpen = { … }, onDismiss = { tappedLink = null }) }
 * ```
 */
@Composable
fun TerminalOpenLinkDialog(destination: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
  MarginedAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Open Link?") },
    text = { Text("Do you want to navigate to $destination? This link could potentially be dangerous.") },
    confirmButton = {
      FocusRingTextButton(onClick = onOpen) {
        Text("Open", color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = { FocusRingTextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
