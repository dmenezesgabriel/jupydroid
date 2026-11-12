package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jupydroid.app.theme.FocusRingIconButton

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
 * phone's bottom bar is where the thumb stops being able to tell them apart. The menu itself is
 * [KernelMenu], which lives next door because it has a second anchor.
 *
 * ```
 * NotebookBar(busy = state.runningCellId != null, notebookTitle = state.title, …)
 * ```
 */
@Composable
internal fun NotebookBar(
  busy: Boolean,
  onRunAll: () -> Unit,
  onInterrupt: () -> Unit,
  onRestart: () -> Unit,
  onRestartAndRunAll: () -> Unit,
  /** Everything behind the `⋮` - see [NotebookKernelCommands], which the indicator shares. */
  commands: NotebookKernelCommands = NotebookKernelCommands(),
  modifier: Modifier = Modifier,
) {
  // The two restarts **this bar's own buttons** ask for. [KernelMenu] holds the pending state for
  // its own two rows, which are different controls asking the same question about other commands.
  var confirming by remember { mutableStateOf<RestartRequest?>(null) }
  var kernelMenuOpen by remember { mutableStateOf(false) }

  BottomAppBar(modifier = modifier.imePadding()) {
    // Disabled while a cell runs: a second Run All would queue every cell again behind the one
    // already going.
    FocusRingIconButton(onClick = onRunAll, enabled = !busy) {
      Icon(Icons.Filled.PlayArrow, contentDescription = "Run all cells")
    }
    // Only while something is running - interrupting an idle kernel is a no-op the user cannot
    // tell from a broken button.
    FocusRingIconButton(onClick = onInterrupt, enabled = busy) {
      // **A stop square, which is what the reference draws**: `createInterruptButton` takes
      // `stopIcon` (`apputils/src/toolbar/widget.tsx:50`) and that icon is `M6 6h12v12H6z`
      // (`ui-components/style/icons/toolbar/stop.svg`) - a filled square, never a cross. This drew
      // `Icons.Filled.Clear` once, which was a parity gap on its own and became an ambiguity
      // the moment a *second* cross appeared on the same screen: the document close that sat in the
      // navigation slot before the tab strip took it over shared the glyph, so one cross meant both
      // "stop what the kernel is doing" and "close this document".
      Icon(Icons.Filled.Stop, contentDescription = "Interrupt kernel")
    }
    FocusRingIconButton(onClick = { confirming = RestartRequest.RESTART }) {
      Icon(Icons.Filled.Refresh, contentDescription = "Restart kernel")
    }
    FocusRingIconButton(onClick = { confirming = RestartRequest.RESTART_AND_RUN_ALL }) {
      RestartAndRunAllIcon()
    }
    // Boxed with its menu, which anchors to its parent layout node: left in the bar's own Row a
    // DropdownMenu opens at the far left, a whole bar away from the button that was tapped. The
    // same lesson `CellHeader` records.
    Box {
      FocusRingIconButton(onClick = { kernelMenuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Kernel actions")
      }
      KernelMenu(
        expanded = kernelMenuOpen,
        onDismiss = { kernelMenuOpen = false },
        commands = commands,
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
          // [KernelMenu]'s rows ask these, and they never reach this bar's own dialog.
          RestartRequest.RESTART_AND_CLEAR, RestartRequest.RESTART_AND_RUN_TO_SELECTED -> Unit
        }
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
 * The dependency arrived later; reading the reference settled which icon it should have been.
 */
@Composable
private fun RestartAndRunAllIcon() {
  Icon(Icons.Filled.FastForward, contentDescription = "Restart kernel and run all cells")
}
