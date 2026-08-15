package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.domain.model.Cell

/**
 * The actions the `⋮` on a cell header opens, on whichever surface the window has room for.
 *
 * One list of items, two presentations: a `ModalBottomSheet` where a dropdown would be a stretch
 * for a thumb, a `DropdownMenu` where it is the right weight. See [CellActionPresentation] for
 * the rule and why it is a width rule rather than a device one.
 *
 * The item labels are identical either way, so every test and every Maestro flow addresses the
 * same thing on both - which is the point of branching only the presentation.
 *
 * ```
 * CellActions(expanded = open, cell = cell, canPaste = false, onDismiss = { open = false }) { … }
 * ```
 */
@Composable
fun CellActions(
  expanded: Boolean,
  cell: Cell,
  canPaste: Boolean,
  onDismiss: () -> Unit,
  onAction: (CellAction) -> Unit,
  presentation: CellActionPresentation = currentCellActionPresentation(),
) = CellActionSurface(
  expanded = expanded,
  items = cellActionItems(cell, canPaste, selecting = false).map { (label, action) ->
    label to { onAction(action) }
  },
  onDismiss = onDismiss,
  presentation = presentation,
)

/**
 * The surface itself, over any list of labelled things to do.
 *
 * Generic over the item rather than over [CellAction] because [SelectionTopBar]'s overflow mixes
 * two vocabularies - the four `extend-marked-cells-*` reaches and the cell actions under them -
 * and a second copy of the sheet-versus-dropdown rule is exactly the thing this file exists to
 * stop there being.
 */
@Composable
internal fun CellActionSurface(
  expanded: Boolean,
  items: List<Pair<String, () -> Unit>>,
  onDismiss: () -> Unit,
  presentation: CellActionPresentation = currentCellActionPresentation(),
) {
  if (!expanded) return
  val choose: (() -> Unit) -> Unit = { act ->
    act()
    onDismiss()
  }
  when (presentation) {
    CellActionPresentation.MENU -> CellActionMenu(items, onDismiss, choose)
    CellActionPresentation.SHEET -> CellActionSheet(items, onDismiss, choose)
  }
}

@Composable
private fun CellActionMenu(
  items: List<Pair<String, () -> Unit>>,
  onDismiss: () -> Unit,
  onChoose: (() -> Unit) -> Unit,
) {
  DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
    items.forEach { (label, act) ->
      DropdownMenuItem(
        modifier = Modifier.semantics { contentDescription = cellMenuDescription(label) },
        text = { Text(label) },
        onClick = { onChoose(act) },
      )
    }
  }
}

/**
 * The compact-width surface: full-width rows starting at the bottom of the screen, where a thumb
 * already is.
 *
 * `navigationBarsPadding` because the sheet draws to the bottom edge under edge-to-edge, and the
 * last row would otherwise sit under the gesture bar - the row most likely to be Delete.
 *
 * **Scrollable, and the test is what said so.** Thirteen rows do not fit a phone-height sheet,
 * and a plain `Column` inside one simply clips them - which is the same "the action is on screen
 * but out of reach" bug this surface exists to fix, reintroduced one layer down. A `Column` with
 * `verticalScroll` rather than a `LazyColumn`: the rows are few and short, and keeping every one
 * in the semantics tree is what lets a screen reader and a test walk the whole list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellActionSheet(
  items: List<Pair<String, () -> Unit>>,
  onDismiss: () -> Unit,
  onChoose: (() -> Unit) -> Unit,
) {
  // `skipPartiallyExpanded`: a sheet that opens half-height shows about five of thirteen rows
  // and asks the user to drag it before they can read the rest. A list of actions is not content
  // to peek at - it is the whole reason the sheet opened.
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding(),
    ) {
      items.forEach { (label, act) ->
        // A DropdownMenuItem outside a menu: it is the Material row for exactly this - a label
        // that runs an action - and using it keeps the two surfaces' rows the same height and
        // the same shape.
        DropdownMenuItem(
          modifier = Modifier.fillMaxWidth().semantics { contentDescription = cellMenuDescription(label) },
          text = { Text(label) },
          onClick = { onChoose(act) },
        )
      }
    }
  }
}

/**
 * Named for the accessibility tree as well as the eye: "Delete" alone would also match any other
 * Delete on screen, and these are what a Maestro flow taps. Shared so the sheet and the menu are
 * addressed identically.
 */
private fun cellMenuDescription(label: String) = "Cell menu $label"
