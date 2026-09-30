package com.jupydroid.app.ui.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Everything [KernelMenu] asks of a notebook - wired **once**, for both of its anchors.
 *
 * A type rather than ten parameters repeated at two call sites, and that is the point: the menu is
 * opened from the bottom bar's `⋮` ([NotebookBar]) *and* from the kernel
 * indicator on the action row ([KernelIndicator]). Spelled out at each, the two wirings would have
 * to be kept in step by hand, which is the drift `CLAUDE.md` gives one home to avoid.
 *
 * `@Immutable` so a bar holding one stays skippable, which is [CellExecution]'s call for the same
 * reason.
 *
 * ```
 * KernelMenu(expanded, onDismiss = {…}, commands = notebookKernelCommands(state, viewModel, onBack))
 * ```
 */
@Immutable
internal data class NotebookKernelCommands(
  /** Named in the close confirmation, the way the reference's dialog names the file it will close. */
  val notebookTitle: String = "",
  val canRunAllAbove: Boolean = false,
  val canRunSelectedAndBelow: Boolean = false,
  /** `notebook:restart-and-run-to-selected` is greyed with no head to run up to. */
  val canRestartAndRunTo: Boolean = false,
  val onRun: (RunRange) -> Unit = {},
  /** `notebook:clear-all-cell-outputs` - a document operation, not a kernel one. */
  val onClearAllOutputs: () -> Unit = {},
  /** `notebook:restart-clear-output`: the restart's continuation, after the user has agreed. */
  val onRestartAndClear: () -> Unit = {},
  val onRestartAndRunTo: () -> Unit = {},
  /** `notebook:change-kernel` - the same `selectKernel` the kernel-name button's tap is. */
  val onPickKernel: () -> Unit = {},
  val onShutdown: () -> Unit = {},
  val onCloseAndShutdown: () -> Unit = {},
  /** `notebook:create-console` - a Console sharing this notebook's own kernel. */
  val onOpenConsole: () -> Unit = {},
)

/**
 * This notebook's answers to [NotebookKernelCommands] - the wiring [EditorBottomBars] and
 * [EditorTopBars] both need and neither should own.
 *
 * @param onBack `notebook:close-and-shutdown`'s second half; the shutdown is the first.
 */
internal fun notebookKernelCommands(
  state: EditorUiState,
  viewModel: EditorViewModel,
  onBack: () -> Unit,
  /** Where a Console session `attachConsole()` mints goes next - see `MainNavigation`. */
  onOpenConsole: (NotebookKernelSession) -> Unit = {},
): NotebookKernelCommands = NotebookKernelCommands(
  notebookTitle = state.title,
  // Greyed rather than absent, so the menu's shape does not change under the thumb as the head
  // moves - and each says why by being disabled exactly where the reference disables it.
  canRunAllAbove = state.canRunAllAbove,
  canRunSelectedAndBelow = state.canRunSelectedAndBelow,
  // Greyed with nothing selected: `restart-and-run-to-selected` is `isEnabledAndSingleSelected`
  // there, and with no head there is no "up to" to run.
  canRestartAndRunTo = state.selectedCellId != null,
  onRun = viewModel.kernel::run,
  // In this menu although it asks the kernel nothing, because it is where the reference files it
  // too - the Kernel menu, beside the restart it composes with.
  onClearAllOutputs = viewModel.edits::clearAllOutputs,
  // Composed here rather than on the kernel, and the asymmetry is the ownership: the two restarts
  // that run cells need the runner `NotebookKernel` keeps private, while this one's continuation
  // is a document operation `NotebookCellEdits` owns.
  onRestartAndClear = { viewModel.kernel.restart(then = { viewModel.edits.clearAllOutputs() }) },
  onRestartAndRunTo = viewModel.kernel::restartAndRunTo,
  onPickKernel = viewModel.kernel.picker::open,
  onShutdown = { viewModel.kernel.shutdown() },
  // The close is the shutdown's continuation rather than a second act, so it runs after the
  // session has actually ended - `notebook:close-and-shutdown` awaits it too.
  onCloseAndShutdown = { viewModel.kernel.shutdown(then = onBack) },
  onOpenConsole = { onOpenConsole(viewModel.kernel.attachConsole()) },
)

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
 * **A file of its own, because it has two anchors.** It was private to [NotebookBar]
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
internal fun KernelMenu(expanded: Boolean, onDismiss: () -> Unit, commands: NotebookKernelCommands) {
  var confirming by remember { mutableStateOf<RestartRequest?>(null) }
  var closeConfirming by remember { mutableStateOf(false) }

  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    // The reference's own labels throughout, ellipsis included - it is what says a row will ask.
    //
    // Greyed at a notebook end rather than hidden, so the menu's shape does not change under the
    // thumb as the head moves. **A flow cannot see that greying**, and no amount of semantics here
    // makes it: Compose's merged test tree carries the disabled state onto the label, and Android's
    // accessibility tree - which is what Maestro reads - keeps it on a clickable node that has no
    // label, one level above. Measured on device three ways. So the
    // state is asserted by `NotebookBarRangedRunsTest` and the flow proves it by *behaviour*: a
    // dead row leaves this menu open, because [onRun] is what closes it.
    DropdownMenuItem(
      text = { Text(RUN_ALL_ABOVE) },
      enabled = commands.canRunAllAbove,
      onClick = {
        onDismiss()
        commands.onRun(RunRange.ABOVE)
      },
    )
    DropdownMenuItem(
      text = { Text(RUN_SELECTED_AND_BELOW) },
      enabled = commands.canRunSelectedAndBelow,
      onClick = {
        onDismiss()
        commands.onRun(RunRange.BELOW)
      },
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
      enabled = commands.canRestartAndRunTo,
      onClick = {
        onDismiss()
        confirming = RestartRequest.RESTART_AND_RUN_TO_SELECTED
      },
    )
    HorizontalDivider()
    // Its own group: it throws results away rather than producing them, and it is the only row here
    // that asks the kernel nothing at all - `clearAllOutputs` is a model operation
    // (`actions.tsx:1851`). Grouped apart so a thumb reaching for Run All Above does not find it.
    DropdownMenuItem(
      text = { Text(CLEAR_ALL_OUTPUTS) },
      onClick = {
        onDismiss()
        commands.onClearAllOutputs()
      },
    )
    HorizontalDivider()
    // `mainMenu.fileMenu.consoleCreators` (`notebook-extension/src/index.ts:1413-1417`) - its own
    // group in the reference, filed apart from the ranged runs and apart from the two shutdowns.
    DropdownMenuItem(
      text = { Text(NEW_CONSOLE_FOR_NOTEBOOK) },
      onClick = {
        onDismiss()
        commands.onOpenConsole()
      },
    )
    HorizontalDivider()
    // `kernelUser.changeKernel`, filed with the kernel's ends as the reference files it - its
    // Kernel menu's order is the restarts, then this, then the two shutdowns
    // (`mainmenu-extension` `kernelUser` group). It asks the same question the indicator's tap
    // does, so the ellipsis the reference gives it is kept.
    DropdownMenuItem(
      text = { Text("Change Kernel…") },
      onClick = {
        onDismiss()
        commands.onPickKernel()
      },
    )
    DropdownMenuItem(
      text = { Text("Shut Down Kernel") },
      onClick = {
        onDismiss()
        commands.onShutdown()
      },
    )
    DropdownMenuItem(
      text = { Text("Close and Shut Down Notebook…") },
      onClick = {
        onDismiss()
        closeConfirming = true
      },
    )
  }

  // Outside the menu, because a dialog is its own window and the `DropdownMenu` above is about to
  // be dismissed. **Every row dismisses the menu first**, which is the menu's own job rather than
  // each anchor's - it was the anchor's while [NotebookBar] was the only one, and two anchors
  // spelling it out is two chances to forget. A row that raises a dialog needs it most: a menu left
  // expanded behind a modal one is still there when the dialog is cancelled. A *disabled* row fires
  // no click at all, so the ranged runs still leave the menu open exactly as their comment says.
  confirming?.let { request ->
    RestartDialog(
      request = request,
      onDismiss = { confirming = null },
      onConfirm = {
        confirming = null
        when (request) {
          RestartRequest.RESTART_AND_CLEAR -> commands.onRestartAndClear()
          RestartRequest.RESTART_AND_RUN_TO_SELECTED -> commands.onRestartAndRunTo()
          // The bar's own two buttons ask these, and they never reach this menu.
          RestartRequest.RESTART, RestartRequest.RESTART_AND_RUN_ALL -> Unit
        }
      },
    )
  }

  if (closeConfirming) {
    CloseAndShutDownDialog(
      notebookTitle = commands.notebookTitle,
      onDismiss = { closeConfirming = false },
      onConfirm = {
        closeConfirming = false
        commands.onCloseAndShutdown()
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

/** `notebook:create-console`'s own label (`notebook-extension/src/index.ts:1841`). */
internal const val NEW_CONSOLE_FOR_NOTEBOOK = "New Console for Notebook"

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
