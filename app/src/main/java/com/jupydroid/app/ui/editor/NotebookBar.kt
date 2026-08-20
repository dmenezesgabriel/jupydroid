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
import androidx.compose.material3.HorizontalDivider
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
  /** The two ranged runs, and whether the head's position leaves either with anything to do. */
  onRun: (RunRange) -> Unit = {},
  canRunAllAbove: Boolean = false,
  canRunSelectedAndBelow: Boolean = false,
  /** `notebook:clear-all-cell-outputs` - a document operation, not a kernel one. */
  onClearAllOutputs: () -> Unit = {},
  /** `notebook:restart-clear-output`: the restart's continuation, after the user has agreed. */
  onRestartAndClear: () -> Unit = {},
  /** `notebook:restart-and-run-to-selected`, greyed with no head to run up to. */
  onRestartAndRunTo: () -> Unit = {},
  canRestartAndRunTo: Boolean = false,
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
        canRunAllAbove = canRunAllAbove,
        canRunSelectedAndBelow = canRunSelectedAndBelow,
        onDismiss = { kernelMenuOpen = false },
        canRestartAndRunTo = canRestartAndRunTo,
        onConfirmRestart = { request ->
          kernelMenuOpen = false
          confirming = request
        },
        onClearAllOutputs = {
          kernelMenuOpen = false
          onClearAllOutputs()
        },
        onRun = { range ->
          kernelMenuOpen = false
          onRun(range)
        },
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
        when (request) {
          RestartRequest.RESTART -> onRestart()
          RestartRequest.RESTART_AND_RUN_ALL -> onRestartAndRunAll()
          RestartRequest.RESTART_AND_CLEAR -> onRestartAndClear()
          RestartRequest.RESTART_AND_RUN_TO_SELECTED -> onRestartAndRunTo()
        }
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
 * What is asked of the kernel but does not earn a button: the ranged runs, and the two ways of
 * ending it.
 *
 * Interrupt, restart and the two whole-notebook runs are on the bar because they are what a
 * running notebook needs at a thumb's reach; everything here is either rarer or a decision, and
 * the reference files those under menus for the same reason.
 *
 * **Two of the reference's menus, in one.** `run-all-above` and `run-all-below` live in its Run
 * menu and the ends of a kernel in its Kernel menu
 * (`packages/notebook-extension/schema/tracker.json:176-226`, `mainmenu-extension/src/index.ts:690`).
 * A phone's bottom bar has room for one overflow, not two - a sixth 48dp target is where a thumb
 * stops telling them apart - so the two menus become one list separated the way the reference's own
 * schema separates its groups, with `"type": "separator"`.
 */
@Composable
private fun KernelActions(
  expanded: Boolean,
  canRunAllAbove: Boolean,
  canRunSelectedAndBelow: Boolean,
  onDismiss: () -> Unit,
  onRun: (RunRange) -> Unit,
  onConfirmRestart: (RestartRequest) -> Unit,
  canRestartAndRunTo: Boolean,
  onClearAllOutputs: () -> Unit,
  onShutdown: () -> Unit,
  onCloseAndShutdown: () -> Unit,
) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    // The reference's own labels throughout, ellipsis included - it is what says a row will ask.
    //
    // Greyed at a notebook end rather than hidden, so the menu's shape does not change under the
    // thumb as the head moves. **A flow cannot see that greying**, and no amount of semantics here
    // makes it: Compose's merged test tree carries the disabled state onto the label, and Android's
    // accessibility tree - which is what Maestro reads - keeps it on a clickable node that has no
    // label, one level above. Measured on device three ways (`evidence/p2d-2-ranged-runs/`). So the
    // state is asserted by `NotebookBarRangedRunsTest` and the flow proves it by *behaviour*: a
    // dead row leaves this menu open, because [onRun] is what closes it.
    DropdownMenuItem(
      text = { Text(RUN_ALL_ABOVE) },
      enabled = canRunAllAbove,
      onClick = { onRun(RunRange.ABOVE) },
    )
    DropdownMenuItem(
      text = { Text(RUN_SELECTED_AND_BELOW) },
      enabled = canRunSelectedAndBelow,
      onClick = { onRun(RunRange.BELOW) },
    )
    HorizontalDivider()
    // The two compound restarts, in the group the reference files them under - its Kernel menu
    // carries `restart-and-run-to-selected` (`schema/tracker.json:220-226`) and its palette the
    // other. Both keep the reference's ellipsis, which is what says they will ask first.
    DropdownMenuItem(
      text = { Text(RESTART_AND_CLEAR) },
      onClick = { onConfirmRestart(RestartRequest.RESTART_AND_CLEAR) },
    )
    DropdownMenuItem(
      text = { Text(RESTART_AND_RUN_TO_SELECTED) },
      enabled = canRestartAndRunTo,
      onClick = { onConfirmRestart(RestartRequest.RESTART_AND_RUN_TO_SELECTED) },
    )
    HorizontalDivider()
    // Its own group: it throws results away rather than producing them, and it is the only row here
    // that asks the kernel nothing at all - `clearAllOutputs` is a model operation
    // (`actions.tsx:1851`). Grouped apart so a thumb reaching for Run All Above does not find it.
    DropdownMenuItem(text = { Text(CLEAR_ALL_OUTPUTS) }, onClick = onClearAllOutputs)
    HorizontalDivider()
    DropdownMenuItem(text = { Text("Shut Down Kernel") }, onClick = onShutdown)
    DropdownMenuItem(text = { Text("Close and Shut Down Notebook…") }, onClick = onCloseAndShutdown)
  }
}

/** `notebook:run-all-above`, in the reference's own words (`index.ts:3067`). */
internal const val RUN_ALL_ABOVE = "Run All Above Selected Cell"

/** `notebook:run-all-below` (`index.ts:3098`). Its label is what says the head is included. */
internal const val RUN_SELECTED_AND_BELOW = "Run Selected Cell and All Below"

/** `notebook:clear-all-cell-outputs` (`index.ts:3347`). */
internal const val CLEAR_ALL_OUTPUTS = "Clear Outputs of All Cells"

/** `notebook:restart-clear-output` (`index.ts:3246`). */
internal const val RESTART_AND_CLEAR = "Restart Kernel and Clear Outputs of All Cells…"

/** `notebook:restart-and-run-to-selected` (`index.ts:3265`). */
internal const val RESTART_AND_RUN_TO_SELECTED = "Restart Kernel and Run up to Selected Cell…"

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

/**
 * Which restart is waiting on the user.
 *
 * Four, because every command the reference builds on a restart asks the same question first -
 * `await sessionDialogs.restart(…)` in each of `restart-run-all`, `restart-clear-output` and
 * `restart-and-run-to-selected` (`packages/notebook-extension/src/index.ts:3245-3345`). One dialog
 * with four titles rather than four dialogs, since the sentence under it is the same either way and
 * it is the sentence that matters: every variable is going.
 */
internal enum class RestartRequest {
  RESTART,
  RESTART_AND_RUN_ALL,
  RESTART_AND_CLEAR,
  RESTART_AND_RUN_TO_SELECTED,
}

/** What the dialog asks, per request. The reference's own labels, minus their ellipsis. */
private fun RestartRequest.question(): String = when (this) {
  RestartRequest.RESTART -> "Restart kernel?"
  RestartRequest.RESTART_AND_RUN_ALL -> "Restart and run all?"
  RestartRequest.RESTART_AND_CLEAR -> "Restart and clear all outputs?"
  RestartRequest.RESTART_AND_RUN_TO_SELECTED -> "Restart and run up to the selected cell?"
}

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
    title = { Text(request.question()) },
    text = { Text("All variables will be lost.") },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("Restart", color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
