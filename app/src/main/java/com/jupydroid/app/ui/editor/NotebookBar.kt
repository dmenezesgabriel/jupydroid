package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * The bar along the bottom: what you ask the *kernel*, as opposed to what you ask a cell.
 *
 * That split is the whole reason it is short. JupyterLab's notebook toolbar carries cut, copy,
 * paste and the cell-type picker as well (`packages/notebook-extension/schema/panel.json`), but
 * its *cell* toolbar has only six buttons - ours has thirteen actions on the cell already, so
 * repeating them here would be two homes for one thing on a screen with room for neither. What a
 * cell menu cannot offer is the kernel, and that is what this is.
 *
 * At the bottom rather than the top because it is reached with a thumb, and `imePadding` because
 * it would otherwise sit under the keyboard the moment a cell is being edited.
 *
 * ```
 * NotebookBar(busy = state.runningCellId != null, kernel = viewModel.kernel)
 * ```
 */
@Composable
internal fun NotebookBar(
  busy: Boolean,
  onRunAll: () -> Unit,
  onInterrupt: () -> Unit,
  onRestart: () -> Unit,
  onRestartAndRunAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var confirming by remember { mutableStateOf<RestartRequest?>(null) }

  BottomAppBar(modifier = modifier.imePadding()) {
    // Disabled while a cell runs: a second Run All would queue every cell again behind the one
    // already going.
    IconButton(onClick = onRunAll, enabled = !busy) {
      Icon(Icons.Filled.PlayArrow, contentDescription = "Run all cells")
    }
    // Only while something is running - interrupting an idle kernel is a no-op the user cannot
    // tell from a broken button.
    IconButton(onClick = onInterrupt, enabled = busy) {
      Icon(Icons.Filled.Clear, contentDescription = "Interrupt kernel")
    }
    IconButton(onClick = { confirming = RestartRequest.RESTART }) {
      Icon(Icons.Filled.Refresh, contentDescription = "Restart kernel")
    }
    IconButton(onClick = { confirming = RestartRequest.RESTART_AND_RUN_ALL }) {
      RestartAndRunAllIcon()
    }
  }

  confirming?.let { request ->
    RestartDialog(
      request = request,
      onDismiss = { confirming = null },
      onConfirm = {
        confirming = null
        if (request == RestartRequest.RESTART) onRestart() else onRestartAndRunAll()
      },
    )
  }
}

/**
 * Fast-forward, which is the icon the reference uses - not a composed one, and not another Refresh.
 *
 * `packages/notebook-extension/src/index.ts`: `restart-kernel` takes `refreshIcon` (line 3157) and
 * `restart-run-all` takes `fastForwardIcon` (line 3326). Its `fast-forward.svg` is the double
 * triangle `m4 18 8.5-6L4 6zm9-12v12l8.5-6z`, which is Material's `FastForward`.
 *
 * This was a `Refresh` and a `PlayArrow` drawn side by side while `material-icons-extended` was
 * not a dependency - an invention that read plausibly and was not what a JupyterLab user sees.
 * X2a added the dependency; reading the reference settled which icon it should have been.
 */
@Composable
private fun RestartAndRunAllIcon() {
  Icon(Icons.Filled.FastForward, contentDescription = "Restart kernel and run all cells")
}

/** Which of the two restarts is waiting on the user. */
private enum class RestartRequest { RESTART, RESTART_AND_RUN_ALL }

/**
 * The confirmation, because a restart is silent and irreversible: nothing on screen changes
 * except that every variable the notebook built is gone. JupyterLab guards it the same way and
 * with the same sentence - *"All variables will be lost."*
 * (`packages/apputils/src/sessioncontext.tsx:1505-1520`).
 */
@Composable
private fun RestartDialog(request: RestartRequest, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (request == RestartRequest.RESTART) "Restart kernel?" else "Restart and run all?") },
    text = { Text("All variables will be lost.") },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("Restart", color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
