package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
 * **Four buttons and a menu**, not six buttons. The two ways of ending a kernel are where the
 * reference keeps them - the Kernel menu (`packages/mainmenu-extension/src/index.ts:690`,
 * `kernelUsers.shutdownKernel`), never the notebook toolbar - and a fifth and sixth icon on a
 * phone's bottom bar is where the thumb stops being able to tell them apart.
 *
 * ```
 * NotebookBar(busy = state.runningCellId != null, notebookTitle = state.title, …)
 * ```
 */
@Composable
internal fun NotebookBar(
  busy: Boolean,
  /** Named in the close confirmation, the way the reference's dialog names the file it will close. */
  notebookTitle: String,
  onRunAll: () -> Unit,
  onInterrupt: () -> Unit,
  onRestart: () -> Unit,
  onRestartAndRunAll: () -> Unit,
  onShutdown: () -> Unit,
  onCloseAndShutdown: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var confirming by remember { mutableStateOf<RestartRequest?>(null) }
  var closeConfirming by remember { mutableStateOf(false) }
  var kernelMenuOpen by remember { mutableStateOf(false) }

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
    // Boxed with its menu, which anchors to its parent layout node: left in the bar's own Row a
    // DropdownMenu opens at the far left, a whole bar away from the button that was tapped. The
    // same lesson `CellHeader` records.
    Box {
      IconButton(onClick = { kernelMenuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Kernel actions")
      }
      KernelActions(
        expanded = kernelMenuOpen,
        onDismiss = { kernelMenuOpen = false },
        onShutdown = {
          kernelMenuOpen = false
          onShutdown()
        },
        onCloseAndShutdown = {
          kernelMenuOpen = false
          closeConfirming = true
        },
      )
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

  if (closeConfirming) {
    CloseAndShutDownDialog(
      notebookTitle = notebookTitle,
      onDismiss = { closeConfirming = false },
      onConfirm = {
        closeConfirming = false
        onCloseAndShutdown()
      },
    )
  }
}

/**
 * The two ways of ending a kernel, as menu items rather than buttons.
 *
 * Only shutdown is here. Interrupt and restart are on the bar because they are what a running
 * notebook needs at a thumb's reach; ending the kernel is a decision, and the reference files it
 * under a menu for the same reason.
 */
@Composable
private fun KernelActions(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onShutdown: () -> Unit,
  onCloseAndShutdown: () -> Unit,
) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    // The reference's own labels, ellipsis included - it is what says the second one will ask.
    DropdownMenuItem(text = { Text("Shut Down Kernel") }, onClick = onShutdown)
    DropdownMenuItem(text = { Text("Close and Shut Down Notebook…") }, onClick = onCloseAndShutdown)
  }
}

/**
 * The confirmation, in the reference's own words: *"Shut down the notebook?"* over *"Are you sure
 * you want to close %1?"* (`packages/notebook-extension/src/index.ts:3196-3205`).
 *
 * Only this half of the pair asks. `notebook:shutdown-kernel` executes on the spot, and the
 * difference is not an oversight there: shutting the kernel down leaves the notebook on screen
 * with its outputs, where closing takes it away as well.
 */
@Composable
private fun CloseAndShutDownDialog(notebookTitle: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Shut down the notebook?") },
    text = { Text("Are you sure you want to close \"$notebookTitle\"?") },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("Shut Down", color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
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
