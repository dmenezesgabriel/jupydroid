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
import com.jupydroid.app.domain.model.CellKind

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
) {
  if (!expanded) return
  val items = cellActionItems(cell, canPaste)
  val choose: (CellAction) -> Unit = { action ->
    onAction(action)
    onDismiss()
  }
  when (presentation) {
    CellActionPresentation.MENU -> CellActionMenu(items, onDismiss, choose)
    CellActionPresentation.SHEET -> CellActionSheet(items, onDismiss, choose)
  }
}

@Composable
private fun CellActionMenu(
  items: List<Pair<String, CellAction>>,
  onDismiss: () -> Unit,
  onChoose: (CellAction) -> Unit,
) {
  DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
    items.forEach { (label, action) ->
      DropdownMenuItem(
        modifier = Modifier.semantics { contentDescription = cellMenuDescription(label) },
        text = { Text(label) },
        onClick = { onChoose(action) },
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
  items: List<Pair<String, CellAction>>,
  onDismiss: () -> Unit,
  onChoose: (CellAction) -> Unit,
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
      items.forEach { (label, action) ->
        // A DropdownMenuItem outside a menu: it is the Material row for exactly this - a label
        // that runs an action - and using it keeps the two surfaces' rows the same height and
        // the same shape.
        DropdownMenuItem(
          modifier = Modifier.fillMaxWidth().semantics { contentDescription = cellMenuDescription(label) },
          text = { Text(label) },
          onClick = { onChoose(action) },
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

/**
 * Ordered the way the actions are reached for: adding, then re-ordering, then destroying - so
 * Delete is never next to the thing a user opened the menu to do.
 *
 * Collapse is offered on **code cells only**, which is JupyterLab's rule: every one of its
 * collapse actions checks `cell.model.type === 'code'`
 * (`packages/notebook/src/actions.tsx:1877`, `:1957`). A markdown cell already has its own
 * two states - rendered and source - and a third way to hide it would fight them.
 *
 * The label says what tapping will do rather than what the cell is, so the menu reads as a
 * verb list throughout.
 *
 * Internal rather than private because it is the one list three surfaces are built from: the
 * menu, the sheet, and the cell's accessibility context menu ([Modifier.cellCustomActions]).
 */
internal fun cellActionItems(cell: Cell, canPaste: Boolean): List<Pair<String, CellAction>> =
  addingItems() + shapingItems(cell) + clipboardItems(canPaste) + foldingItems(cell) + removingItems()

private fun addingItems(): List<Pair<String, CellAction>> = listOf(
  "Insert code above" to CellAction.InsertAbove(CellKind.CODE),
  "Insert code below" to CellAction.Insert(CellKind.CODE),
  "Insert markdown below" to CellAction.Insert(CellKind.MARKDOWN),
)

private fun shapingItems(cell: Cell): List<Pair<String, CellAction>> = listOf(
  "Move up" to CellAction.Move(CellMove.UP),
  "Move down" to CellAction.Move(CellMove.DOWN),
  "Duplicate" to CellAction.Duplicate,
  // The type the cell is *not*, so the item reads as the change rather than as the state.
  if (cell.kind == CellKind.CODE) {
    CHANGE_TO_MARKDOWN to CellAction.ChangeKind(CellKind.MARKDOWN)
  } else {
    CHANGE_TO_CODE to CellAction.ChangeKind(CellKind.CODE)
  },
  "Merge with above" to CellAction.Merge(MergeInto.ABOVE),
  "Merge with below" to CellAction.Merge(MergeInto.BELOW),
  // Beside the merges, because split is their inverse and the three read as one group.
  //
  // **On every cell, and whether or not a caret was ever put in one.** Both are the reference's
  // `isEnabled: !!current.content.activeCell?.editor`: a markdown cell keeps its editor while it
  // is rendered - `renderInput`/`showEditor` swap only which widget is displayed
  // (`packages/cells/src/widget.ts:2489-2503`) - and `editor.getSelections()` answers with the
  // last cursor, or 0 for an editor nobody has touched. Splitting there puts an empty cell above,
  // and an undo takes it back.
  "Split cell" to CellAction.Split,
)

private fun clipboardItems(canPaste: Boolean): List<Pair<String, CellAction>> = buildList {
  add("Cut" to CellAction.Cut)
  add("Copy" to CellAction.Copy)
  // Offered only when there is something to paste. An item that silently does nothing teaches
  // the user that the menu lies, which is worse than the item being absent.
  if (canPaste) add("Paste below" to CellAction.Paste)
}

private fun foldingItems(cell: Cell): List<Pair<String, CellAction>> = buildList {
  if (cell.kind != CellKind.CODE) return@buildList
  add((if (cell.sourceHidden) EXPAND_CODE else COLLAPSE_CODE) to CellAction.ToggleSourceHidden)
  add((if (cell.outputsHidden) EXPAND_OUTPUTS else COLLAPSE_OUTPUTS) to CellAction.ToggleOutputsHidden)
}

private fun removingItems(): List<Pair<String, CellAction>> = listOf(
  // Beside folding them, because the two read as a pair and are not: this one throws the
  // outputs away, the one above only folds them.
  "Clear outputs" to CellAction.ClearOutputs,
  "Delete" to CellAction.Delete,
)

internal const val CHANGE_TO_MARKDOWN = "Change to markdown"
internal const val CHANGE_TO_CODE = "Change to code"

internal const val COLLAPSE_CODE = "Collapse code"
internal const val EXPAND_CODE = "Expand code"
internal const val COLLAPSE_OUTPUTS = "Collapse outputs"
internal const val EXPAND_OUTPUTS = "Expand outputs"
