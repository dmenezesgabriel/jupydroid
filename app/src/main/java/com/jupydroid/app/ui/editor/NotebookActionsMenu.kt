package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The notebook's overflow: everything the reference files under **View**, and - from P2j - under
 * **File** as well.
 *
 * **One overflow, two of the reference's menus**, which is the same adaptation [NotebookBar]
 * already makes and justifies for Run + Kernel: a phone's bar has room for one overflow, not two,
 * and a second `MoreVert` beside the first is where a thumb stops telling them apart. So the
 * button is named for the document rather than for one of the menus folded into it - a menu
 * offering an export while calling itself *View* is the control-that-lies failure this app names
 * elsewhere, and "Notebook actions" is the one string the code, the instrumented tests and nine
 * Maestro flows all address.
 *
 * **Where the heading commands go is an adaptation too**, and this is what it departs from: the
 * reference surfaces `collapse-all-headings` and `expand-all-headings` in no menu at all - they
 * are command-palette entries plus `Ctrl Shift Arrow` shortcuts
 * (`packages/notebook-extension/src/index.ts:5279-5281`, `schema/tracker.json:562-570`). Here
 * rather than on [NotebookBar], because that bar states what it is: what you ask the *kernel*.
 * Folding a section away asks the kernel nothing, and the reference files it under View for the
 * same reason.
 *
 * Boxed with its menu, which anchors to its parent layout node - the lesson [CellHeader] and
 * [NotebookBar] each record.
 *
 * Neither row is ever disabled, which is the reference's own choice: unlike the undo pair beside
 * it, neither command declares an `isEnabled` (`index.ts:5094-5121`). Collapsing a notebook with no
 * headings changes nothing and says so by nothing changing, which is cheaper than a greyed row
 * asking to be understood.
 */
@Composable
internal fun NotebookActionsMenu(
  onSetAllHeadingsCollapsed: (Boolean) -> Unit,
  lineNumbersShown: Boolean,
  onToggleLineNumbers: () -> Unit,
  autoClosingBrackets: Boolean,
  onToggleAutoClosingBrackets: () -> Unit,
  tableOfContentsHeadings: List<TocHeading>,
  onTableOfContentsHeadingSelected: (String) -> Unit,
  tableOfContentsPresentation: TocPresentation,
  tableOfContentsPaneOpen: Boolean,
  onToggleTableOfContentsPane: () -> Unit,
  tocNumbering: TocNumbering,
  onToggleTocNumbering: () -> Unit,
  onToggleTocNumberingH1: () -> Unit,
  tocIncludeOutput: Boolean,
  onToggleTocIncludeOutput: () -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  // Its own flag rather than reusing [open]: picking "Table of contents" replaces this menu with
  // [TableOfContentsPanel] rather than closing it outright, the same two-step [CellActions]' own
  // heading-level picker already establishes.
  var tocOpen by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }) {
      Icon(Icons.Filled.MoreVert, contentDescription = "Notebook actions")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(
        text = { Text(COLLAPSE_ALL_HEADINGS) },
        onClick = {
          open = false
          onSetAllHeadingsCollapsed(true)
        },
      )
      DropdownMenuItem(
        text = { Text(EXPAND_ALL_HEADINGS) },
        onClick = {
          open = false
          onSetAllHeadingsCollapsed(false)
        },
      )
      DropdownMenuItem(
        text = { Text(if (lineNumbersShown) HIDE_LINE_NUMBERS else SHOW_LINE_NUMBERS) },
        onClick = {
          open = false
          onToggleLineNumbers()
        },
      )
      DropdownMenuItem(
        text = { Text(if (autoClosingBrackets) DISABLE_AUTO_CLOSING_BRACKETS else ENABLE_AUTO_CLOSING_BRACKETS) },
        onClick = {
          open = false
          onToggleAutoClosingBrackets()
        },
      )
      // One row, two jobs, and which one is the editor's own width: with a pane on screen the row
      // toggles it and flips its label, and without one it opens the overlay and reads the same
      // thing every time - there is no state to name, since the pick dismisses it.
      val paneMode = tableOfContentsPresentation == TocPresentation.PANE
      DropdownMenuItem(
        text = { Text(if (paneMode && tableOfContentsPaneOpen) HIDE_TABLE_OF_CONTENTS else SHOW_TABLE_OF_CONTENTS) },
        onClick = {
          open = false
          if (paneMode) onToggleTableOfContentsPane() else tocOpen = true
        },
      )
      DropdownMenuItem(
        text = { Text(if (tocNumbering.numberHeaders) HIDE_HEADING_NUMBERS else SHOW_HEADING_NUMBERS) },
        onClick = {
          open = false
          onToggleTocNumbering()
        },
      )
      // Only while there are numbers for it to change, which is the reference's own `isEnabled`
      // read through this app's rule for a control that would do nothing: it gates the row on the
      // option being supported, and here "unsupported" is simply nothing being numbered.
      if (tocNumbering.numberHeaders) {
        DropdownMenuItem(
          text = { Text(if (tocNumbering.numberingH1) HIDE_TOP_LEVEL_NUMBER else SHOW_TOP_LEVEL_NUMBER) },
          onClick = {
            open = false
            onToggleTocNumberingH1()
          },
        )
      }
      DropdownMenuItem(
        text = { Text(if (tocIncludeOutput) HIDE_OUTPUT_HEADINGS else SHOW_OUTPUT_HEADINGS) },
        onClick = {
          open = false
          onToggleTocIncludeOutput()
        },
      )
    }
    // Same Box as "Notebook actions", so [TableOfContentsPanel]'s own `DropdownMenu` anchors to the
    // button that opened it rather than to wherever this composable happens to sit in the screen.
    // Never both surfaces at once: with the editor drawing the list, a copy over the top of it
    // would be two answers to one question.
    TableOfContentsPanel(
      expanded = tocOpen && tableOfContentsPresentation == TocPresentation.OVERLAY,
      headings = tableOfContentsHeadings,
      onHeadingSelected = onTableOfContentsHeadingSelected,
      onDismiss = { tocOpen = false },
      numbering = tocNumbering,
    )
  }
}

/** `notebook:collapse-all-headings` (`packages/notebook-extension/src/index.ts:5096`). */
internal const val COLLAPSE_ALL_HEADINGS = "Collapse All Headings"

/** `notebook:expand-all-headings` (`:5111`). */
internal const val EXPAND_ALL_HEADINGS = "Expand All Headings"

/**
 * `notebook:toggle-all-cell-line-numbers`, as **two** labels rather than one and a check mark.
 *
 * The reference has one label - *"Show Line Numbers"* (`index.ts:4406`) - and shows the state as a
 * toggled menu item, because its View menu is a checkbox list. This app's menus are verb lists
 * (`COLLAPSE_CODE`/`EXPAND_CODE` in [cellActionItems]), and here that convention is load-bearing:
 * Android's accessibility tree keeps a `DropdownMenuItem`'s toggled state on a node that carries
 * no label, so a check mark is a state no Maestro flow can read (`docs/DECISIONS.md`). A label
 * that flips is one both trees can see.
 */
internal const val SHOW_LINE_NUMBERS = "Show line numbers"

/** The same command from the other side - see [SHOW_LINE_NUMBERS]. */
internal const val HIDE_LINE_NUMBERS = "Hide line numbers"

/**
 * `fileeditor:toggle-autoclosing-brackets-universal`, as two labels for the reason
 * [SHOW_LINE_NUMBERS] is: a flip-with-state label is one both the Compose and the accessibility
 * tree can read, where a check mark is not. This app's own convention, not the reference's - see
 * [EditorTopBar]'s `onToggleAutoClosingBrackets` for why the reference's own choice on this exact
 * row (a fixed label) is not repeated here.
 */
internal const val ENABLE_AUTO_CLOSING_BRACKETS = "Enable auto-closing brackets"

/** The same command from the other side - see [ENABLE_AUTO_CLOSING_BRACKETS]. */
internal const val DISABLE_AUTO_CLOSING_BRACKETS = "Disable auto-closing brackets"

/**
 * `toc:show-panel` (`packages/toc-extension/src/index.ts:161-172`), as two labels for the reason
 * [SHOW_LINE_NUMBERS] is - and here only half the time. Where the editor draws
 * [TableOfContentsPane] the row is a toggle and the label flips; where it opens
 * [TableOfContentsPanel] the pick dismisses the surface, so there is no state to name and it reads
 * "Show" every time.
 */
internal const val SHOW_TABLE_OF_CONTENTS = "Show table of contents"

/**
 * The same command from the other side - see [SHOW_TABLE_OF_CONTENTS]. Also the name on
 * [TableOfContentsPane]'s own close button, so the menu, the pane and a Maestro flow all address
 * one string.
 */
internal const val HIDE_TABLE_OF_CONTENTS = "Hide table of contents"

/**
 * `toc:display-numbering`, whose own label is *"Show heading number in the document"*
 * (`packages/toc-extension/src/index.ts:113`) - and "in the document" is the load-bearing half:
 * the number goes on the notebook's rendered heading as well as on the panel's row
 * (`packages/notebook/src/toc.ts:733-748`). Two labels rather than one and a check mark, for
 * [SHOW_LINE_NUMBERS]'s reason.
 *
 * **Adaptation:** the reference puts this on the ToC panel's own toolbar (`:260-306`). Here it is
 * on the View menu, because that is the only surface both presentations of the ToC share - a phone
 * gets the overlay, which has no toolbar to hang it from, and a 280dp pane header has no room for
 * a fourth control.
 */
internal const val SHOW_HEADING_NUMBERS = "Show heading numbers"

/** The same command from the other side - see [SHOW_HEADING_NUMBERS]. */
internal const val HIDE_HEADING_NUMBERS = "Hide heading numbers"

/**
 * `toc:display-h1-numbering`, *"Show first-level heading number"* (`index.ts:95`). Offered only
 * while something is numbered, which is that command's own `isEnabled` read through this app's
 * rule: with numbering off, both states of this row look identical on screen.
 */
internal const val SHOW_TOP_LEVEL_NUMBER = "Number top-level headings"

/** The same command from the other side - see [SHOW_TOP_LEVEL_NUMBER]. */
internal const val HIDE_TOP_LEVEL_NUMBER = "Skip top-level heading numbers"

/**
 * `toc:display-outputs-numbering`, *"Show output headings"* (`index.ts:139`) - whether a heading a
 * code cell **printed** is listed beside the ones its neighbours wrote. Two labels rather than the
 * reference's one, for [SHOW_LINE_NUMBERS]'s reason.
 */
internal const val SHOW_OUTPUT_HEADINGS = "Show output headings"

/** The same command from the other side - see [SHOW_OUTPUT_HEADINGS]. */
internal const val HIDE_OUTPUT_HEADINGS = "Hide output headings"
