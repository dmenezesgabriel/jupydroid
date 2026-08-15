package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jupydroid.app.domain.model.Cell

/**
 * The bar that stands in for [EditorTopBar] while a run of cells is marked.
 *
 * **A contextual bar rather than more controls on each cell**, which is Material's answer to
 * multi-select and also the reference's split under another name: JupyterLab's selection commands
 * live in the Edit menu and the notebook's context menu, never on the cell
 * (`packages/notebook-extension/src/index.ts:5267-5268`). One surface acts on the whole run, so
 * there is never a question of what a menu applies to - which is why the cell's own `⋮` is hidden
 * while this is up.
 *
 * **Two controls beside the title, and the number was measured rather than chosen.** The first
 * version put the four `extend-marked-cells-*` reaches on the bar as arrows: at 360dp that is a
 * navigation icon and four actions, 240dp of the 360, and the count wrapped to three lines reading
 * `3 / select / ed` (`evidence/p2c-multi-select/x86_64/`, before this). So all four reaches went
 * into the overflow, where they also read as one family, and what is left on the bar is the pair a
 * touch user actually reaches for - a tick on a cell is the extend gesture here, and Select all is
 * the one command with no per-cell equivalent at all.
 *
 * ```
 * SelectionTopBar(selectedCount = 3, headCell = cell, canPaste = false, …)
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
  selectedCount: Int,
  /**
   * The cell the selection's head is on, which decides the *shape* of the action list - only a
   * code cell offers the two collapses. The actions themselves apply to the whole run.
   */
  headCell: Cell,
  canPaste: Boolean,
  onDeselectAll: () -> Unit,
  onSelectAll: () -> Unit,
  onExtend: (SelectionReach) -> Unit,
  onCellAction: (CellAction) -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  TopAppBar(
    // Tinted, so it reads as a mode the notebook is in rather than as the notebook's own bar with
    // different buttons on it. Material's own contextual-bar treatment.
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
      titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ),
    // `titleMedium`, not the bar's default `titleLarge`: a count is a status line, not a document
    // title, and 22sp is what left it no room beside the actions. One line, so a three-figure
    // count clips rather than reflowing the bar.
    title = {
      Text(
        text = selectionCountLabel(selectedCount),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    navigationIcon = {
      // `notebook:deselect-all`, under Material's close affordance: leaving the mode and clearing
      // the run are one act, because a run of one is what "no selection" already means.
      IconButton(
        onClick = onDeselectAll,
        modifier = Modifier.semantics { contentDescription = "Deselect all cells" },
      ) {
        Icon(Icons.Filled.Close, contentDescription = null)
      }
    },
    actions = {
      IconButton(
        onClick = onSelectAll,
        modifier = Modifier.semantics { contentDescription = "Select all cells" },
      ) {
        Icon(Icons.Filled.SelectAll, contentDescription = null)
      }
      // Boxed for the reason CellHeader's is: a DropdownMenu anchors to its parent layout node,
      // and left in the actions Row it opens against the whole row rather than under the button.
      Box {
        IconButton(
          onClick = { menuOpen = true },
          modifier = Modifier.semantics { contentDescription = SELECTED_CELL_ACTIONS },
        ) {
          Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        CellActionSurface(
          expanded = menuOpen,
          items = reachItems(onExtend) +
            cellActionItems(headCell, canPaste, selecting = true, count = selectedCount)
              .map { (label, action) -> label to { onCellAction(action) } },
          onDismiss = { menuOpen = false },
        )
      }
    },
  )
}

/**
 * The four `notebook:extend-marked-cells-*` commands, as the overflow's first rows.
 *
 * In the reference's own order - above, below, top, bottom
 * (`packages/notebook-extension/src/index.ts:254-260`) - so the one-step pair and the jump-to-end
 * pair sit together rather than being sorted by how far they go.
 */
private fun reachItems(onExtend: (SelectionReach) -> Unit): List<Pair<String, () -> Unit>> = listOf(
  "Extend above" to { onExtend(SelectionReach.ABOVE) },
  "Extend below" to { onExtend(SelectionReach.BELOW) },
  "Extend to top" to { onExtend(SelectionReach.TOP) },
  "Extend to bottom" to { onExtend(SelectionReach.BOTTOM) },
)

/**
 * How many cells the bar will act on, in words.
 *
 * Pluralised because the reference pluralises every one of its selection-scoped labels off the
 * same count - `trans._n('Move Cell Up', 'Move Cells Up', selectedCells.length)`
 * (`packages/notebook-extension/src/index.ts:4287-4292`). The singular spells "cell" out and the
 * plural does not, so the count is what the eye lands on once there is more than one.
 */
internal fun selectionCountLabel(count: Int): String =
  if (count == 1) "1 cell selected" else "$count selected"

/** Distinct from the cell header's "Cell actions" on purpose: the two act on different things. */
internal const val SELECTED_CELL_ACTIONS = "Selected cell actions"
