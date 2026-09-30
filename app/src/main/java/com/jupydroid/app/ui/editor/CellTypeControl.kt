package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.domain.model.CellKind

/**
 * What the active cell **is**, and the one gesture that changes it.
 *
 * **The reference's own control, in the place the reference puts it.** `cellType` is an item on
 * the notebook toolbar at rank 40 (`packages/notebook-extension/schema/panel.json`), rendered as
 * an `HTMLSelect` whose value is the active cell's type
 * (`packages/notebook/src/default-toolbar.tsx:332-397`). It is deliberately *not* a cell-toolbar
 * item there - that toolbar's six entries are duplicate, move, insert and delete
 * (`packages/cell-toolbar-extension/schema/plugin.json`) - so the type is a fact about the
 * notebook's focus rather than a control on each cell.
 *
 * **It exists because the thing that used to say this was taken away.** The words `markdown` and
 * `raw` in a cell's prompt were this app's own; the reference draws no prompt on a non-code cell
 * at all (`packages/cells/src/widget.ts:1693-1700`), and removing them was right. This is where
 * the reference answers the question they were answering - see [executionLabel].
 *
 * **The reference's `-` is not modelled**, and that is a reading of this app rather than a
 * simplification of the reference. `-` is what its select shows when a cell in the *selection*
 * has a different type; here [EditorTopBars] swaps in [SelectionTopBar] the moment a run is
 * marked, so whenever this control is on screen [selectedCellIds] is exactly one cell. A `-`
 * option would be a branch no test could reach, and the marked-run case already has the
 * reference's command on the other bar's overflow ("Change cells to markdown", [cellActionItems]).
 *
 * A `TextButton` anchoring a `DropdownMenu` rather than an `ExposedDropdownMenuBox`: the latter is
 * Material's *form input* select and brings a text field's chrome and height with it, where this
 * is a toolbar control - value plus caret, which is also what an `HTMLSelect` reads as. The label
 * is `labelLarge`, Material's own role for a button's text, because [TopAppBar] provides
 * `titleLarge` to this slot and 22sp is a document title rather than a control.
 *
 * ```
 * CellTypeControl(kind = CellKind.MARKDOWN, onPick = viewModel.edits::changeKind)
 * ```
 */
@Composable
internal fun CellTypeControl(kind: CellKind, onPick: (CellKind) -> Unit) {
  var open by remember { mutableStateOf(false) }
  // Boxed with its menu, which anchors to its parent layout node - the lesson [CellHeader],
  // [NotebookBar] and [NotebookActionsMenu] each record.
  Box {
    TextButton(
      onClick = { open = true },
      // **Neutral, not the brand colour a `TextButton` defaults to.** Two reasons, and the
      // screenshot is what raised it: the reference's own
      // control is an `HTMLSelect` drawn in `--jp-ui-font-color0` like the rest of its toolbar,
      // and this app spends brand blue on exactly two things - the active cell's collapser and the
      // editor being typed into. A fifth control in the bar wearing that colour
      // would be the only coloured thing in the row and would spend the one signal those two rely
      // on. The caret is what says it is pressable, which is Material's own menu-button
      // affordance.
      colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
      // The name goes on the button and not on the icon inside it, which is [UndoRedo]'s rule and
      // load-bearing for the same reason: one node carries the label a screen reader reads and the
      // state a flow asserts. See [cellTypeDescription] for why the state is *in* the name.
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .semantics { contentDescription = cellTypeDescription(kind) },
    ) {
      Text(text = cellTypeLabel(kind), style = MaterialTheme.typography.labelLarge)
      Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
    CellTypePicker(expanded = open, onDismiss = { open = false }, onPick = {
      open = false
      onPick(it)
    })
  }
}

/**
 * The three types to choose between - shared by [CellTypeControl] and the row [NotebookActionsMenu]
 * shows where the bar has no room for one.
 *
 * **Every type is offered, including the one the cell already is**, which is the reference's
 * select: its options are fixed and `changeCellType` skips a cell whose type already matches
 * (`packages/notebook/src/actions.tsx`, `child.model.type !== value`). So a picker that hid the
 * current type would move its own rows about as the focus changed, for no command that behaves
 * differently.
 *
 * **No check mark on the current one**, which is this app's convention and it is load-bearing:
 * Android's accessibility tree keeps a `DropdownMenuItem`'s toggled state on a node carrying no
 * label, so a check is a state no Maestro flow can read (and see [SHOW_LINE_NUMBERS]). The current
 * type is on the trigger that opened this, which is where the reference's select carries it too.
 */
@Composable
internal fun CellTypePicker(expanded: Boolean, onDismiss: () -> Unit, onPick: (CellKind) -> Unit) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    CellKind.entries.forEach { kind ->
      DropdownMenuItem(
        modifier = Modifier.semantics { contentDescription = cellTypePickDescription(kind) },
        text = { Text(cellTypeLabel(kind)) },
        onClick = { onPick(kind) },
      )
    }
  }
}

/**
 * What each type is **called**, as the reference's select names it
 * (`packages/notebook/src/default-toolbar.tsx:390-392`).
 *
 * The noun half of a vocabulary this app already has both halves of: [cellActionItems]' rows name
 * an action ("Change to markdown") because a menu there is a verb list, and this names a state
 * because a control showing what something *is* has to.
 *
 * Exhaustive rather than derived from the enum's own name, for [changeKindItem]'s reason: a fourth
 * cell type has to decide how it reads here, not inherit a spelling.
 */
internal fun cellTypeLabel(kind: CellKind): String = when (kind) {
  CellKind.CODE -> "Code"
  CellKind.MARKDOWN -> "Markdown"
  CellKind.RAW -> "Raw"
}

/**
 * What the control is called **and what it currently holds**, on one node.
 *
 * The reference's screen reader gets those two facts from two places - the select's `aria-label`
 * *"Cell type"* (`packages/notebook/src/default-toolbar.tsx:394`) and its selected option. Android's
 * does not: a `contentDescription` replaces the text under it, so a fixed name would leave the
 * value unreadable to TalkBack and unassertable by a flow. Folding the state into the name is the
 * rule [SHOW_LINE_NUMBERS] already states for this app's menus, applied to a control.
 *
 * Spelled apart from [cellTypePickDescription] on purpose: neither string contains the other, so a
 * flow asking for the trigger cannot match a row of the picker it opens - which is the collision
 * [cellMenuDescription] was written to avoid between "Cell actions" and "Cell menu Delete".
 */
internal fun cellTypeDescription(kind: CellKind) = "$CELL_TYPE_ACTION ${cellTypeLabel(kind)}"

/** One row of [CellTypePicker], named for the tree as well as the eye - [cellMenuDescription]'s rule. */
internal fun cellTypePickDescription(kind: CellKind) = "$CELL_TYPE_ACTION picker ${cellTypeLabel(kind)}"

/**
 * The same control as a **menu row**, for the width whose bar cannot carry [CellTypeControl].
 *
 * A `ReactiveToolbar` moves an item that does not fit into its popup opener rather than dropping it
 * (`packages/ui-components/src/components/toolbar.tsx:570-700`); this is that row, and
 * [CellTypePicker] is what it opens, so the two homes share one list of types.
 *
 * **The value is in the label**, which is the same rule the trigger follows and what makes this a
 * row that *shows* the type rather than only offering to change it - see [cellTypeDescription]. The
 * colon is what separates a control's name from its value, as the reference's select separates them
 * by being a box with a value in it.
 *
 * Spelled apart from [cellTypeDescription] and [cellTypePickDescription]: a Maestro selector is a
 * regex matched against the whole node, so `Cell type: Markdown` cannot be reached by a flow asking
 * for either of the others.
 */
internal fun cellTypeRowLabel(kind: CellKind) = "$CELL_TYPE_ACTION: ${cellTypeLabel(kind)}"

/** The reference's own `aria-label` for this control (`default-toolbar.tsx:394`). */
internal const val CELL_TYPE_ACTION = "Cell type"
