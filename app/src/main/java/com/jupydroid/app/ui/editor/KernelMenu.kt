package com.jupydroid.app.ui.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * What is asked of the kernel but does not earn a button: the ranged runs, and the two ways of
 * ending it - **with the confirmations those rows need**.
 *
 * Interrupt, restart and the two whole-notebook runs are on [NotebookBar] because they are what a
 * running notebook needs at a thumb's reach; everything here is either rarer or a decision, and
 * the reference files those under menus for the same reason.
 *
 * **Two of the reference's menus, in one.** `run-all-above` and `run-all-below` live in its Run
 * menu and the ends of a kernel in its Kernel menu
 * (`packages/notebook-extension/schema/tracker.json:176-226`, `mainmenu-extension/src/index.ts:690`).
 * A phone's bottom bar has room for one overflow, not two - a sixth 48dp target is where a thumb
 * stops telling them apart - so the two menus become one list separated the way the reference's own
 * schema separates its groups, with `"type": "separator"`.
 *
 * **A file of its own since P2w-2b, because it has two anchors.** It was private to [NotebookBar]
 * while the bar's `⋮` was the only way in; the kernel indicator on the notebook's action row is
 * the second ([KernelIndicator]), and `CLAUDE.md`'s greppable test gives one kind of logic one
 * home. What moved with it is the state the *rows* need and nothing else: this owns the pending
 * confirmation for its own two restarts and for the close, so an anchor supplies callbacks and no
 * state at all. [NotebookBar] keeps its own for the two restart **buttons** on the bar, which are
 * different controls asking a different question - and the two can never be up together, since a
 * menu row and a bar button cannot both be tapped.
 *
 * ```
 * Box {
 *   IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Kernel actions") }
 *   KernelMenu(expanded = open, onDismiss = { open = false }, notebookTitle = state.title, …)
 * }
 * ```
 */
@Composable
internal fun KernelMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  /** Named in the close confirmation, the way the reference's dialog names the file it will close. */
  notebookTitle: String,
  canRunAllAbove: Boolean,
  canRunSelectedAndBelow: Boolean,
  canRestartAndRunTo: Boolean,
  onRun: (RunRange) -> Unit,
  /** `notebook:clear-all-cell-outputs` - a document operation, not a kernel one. */
  onClearAllOutputs: () -> Unit,
  /** `notebook:restart-clear-output`: the restart's continuation, after the user has agreed. */
  onRestartAndClear: () -> Unit,
  /** `notebook:restart-and-run-to-selected`, greyed with no head to run up to. */
  onRestartAndRunTo: () -> Unit,
  onShutdown: () -> Unit,
  onCloseAndShutdown: () -> Unit,
) {
  var confirming by remember { mutableStateOf<RestartRequest?>(null) }
  var closeConfirming by remember { mutableStateOf(false) }

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
      onClick = {
        onDismiss()
        confirming = RestartRequest.RESTART_AND_CLEAR
      },
    )
    DropdownMenuItem(
      text = { Text(RESTART_AND_RUN_TO_SELECTED) },
      enabled = canRestartAndRunTo,
      onClick = {
        onDismiss()
        confirming = RestartRequest.RESTART_AND_RUN_TO_SELECTED
      },
    )
    HorizontalDivider()
    // Its own group: it throws results away rather than producing them, and it is the only row here
    // that asks the kernel nothing at all - `clearAllOutputs` is a model operation
    // (`actions.tsx:1851`). Grouped apart so a thumb reaching for Run All Above does not find it.
    DropdownMenuItem(text = { Text(CLEAR_ALL_OUTPUTS) }, onClick = onClearAllOutputs)
    HorizontalDivider()
    DropdownMenuItem(text = { Text("Shut Down Kernel") }, onClick = onShutdown)
    DropdownMenuItem(
      text = { Text("Close and Shut Down Notebook…") },
      onClick = {
        onDismiss()
        closeConfirming = true
      },
    )
  }

  // Outside the menu, because a dialog is its own window and the `DropdownMenu` above is about to
  // be dismissed. Every row that raises one dismisses the menu first: a menu left expanded behind a
  // modal dialog is still there when the dialog is cancelled, which is not what the row asked for.
  // That is what the three `onDismiss()` calls above are, and it is what the old shape got for free
  // by keeping this state in the anchor.
  confirming?.let { request ->
    RestartDialog(
      request = request,
      onDismiss = { confirming = null },
      onConfirm = {
        confirming = null
        when (request) {
          RestartRequest.RESTART_AND_CLEAR -> onRestartAndClear()
          RestartRequest.RESTART_AND_RUN_TO_SELECTED -> onRestartAndRunTo()
          // The bar's own two buttons ask these, and they never reach this menu.
          RestartRequest.RESTART, RestartRequest.RESTART_AND_RUN_ALL -> Unit
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
 * Which restart is waiting on the user.
 *
 * Four, because every command the reference builds on a restart asks the same question first -
 * `await sessionDialogs.restart(…)` in each of `restart-run-all`, `restart-clear-output` and
 * `restart-and-run-to-selected` (`packages/notebook-extension/src/index.ts:3245-3345`). One dialog
 * with four titles rather than four dialogs, since the sentence under it is the same either way and
 * it is the sentence that matters: every variable is going.
 *
 * The first two are [NotebookBar]'s buttons and the last two are [KernelMenu]'s rows; they share
 * this type and [RestartDialog] because they are one question asked about four commands.
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
internal fun RestartDialog(request: RestartRequest, onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
