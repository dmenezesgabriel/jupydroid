package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupydroid.app.ui.RenameFileDialog

/** Small enough to read as part of the title rather than as a button beside it. */
private val TITLE_EDIT_ICON_SIZE = 18.dp

/**
 * The notebook's app bar: back and the title.
 *
 * Run All lives on [NotebookBar] now, not here. It is a kernel action like interrupt and restart,
 * it belongs beside them rather than alone at the far corner of the screen, and the bottom of a
 * phone is where a thumb already is.
 *
 * **The title is the rename target**, which is where JupyterLab puts it too - `docmanager:rename`
 * is on the document-title context menu, `"selector": "[data-type=\"document-title\"]"`
 * (`packages/docmanager-extension/schema/plugin.json:73-77`). A tap is the touch analogue of
 * that right-click, and the pencil beside it is what says so: a gesture with nothing on screen
 * announcing it is undiscoverable, which is the lesson the cell menu cost three attempts to
 * learn.
 *
 * **Undo and redo are here and not on [NotebookBar]**, which is the split that bar states about
 * itself: it is what you ask the *kernel*. Taking back a cell operation is neither a kernel action
 * nor a cell one - it is the document's - and the reference files it under Edit and the notebook's
 * own context menu, never the notebook toolbar (`packages/notebook-extension/schema/tracker.json`,
 * `jp-mainmenu-edit` at :31-35 and `.jp-Notebook` at :341-349). It is also a command-mode action
 * there (`Z` / `Shift Z` are bound to `.jp-mod-commandMode` only, `:572-648`), and this bar is the
 * surface that is up in command mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
  title: String,
  /** Whether there is a cell operation to take back; the button is shown either way, see below. */
  canUndo: Boolean,
  canRedo: Boolean,
  onRename: (String) -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onBack: () -> Unit,
  /** `notebook:collapse-all-headings` / `expand-all-headings` - see [ViewMenu]. */
  onSetAllHeadingsCollapsed: (Boolean) -> Unit = {},
  /** Whether the cells are drawing their gutters, which is what the View row is named from. */
  lineNumbersShown: Boolean = true,
  /** `notebook:toggle-all-cell-line-numbers` - see [ViewMenu]. */
  onToggleLineNumbers: () -> Unit = {},
  /** Whether a typed `(` closes itself - what the row beside it is named from. */
  autoClosingBrackets: Boolean = true,
  /**
   * `fileeditor:toggle-autoclosing-brackets-universal` - the notebook, the console and the file
   * editor together (`packages/fileeditor-extension/src/commands.ts:552-575`). One app-wide
   * setting *is* the universal command here, since `PythonTypingRules` already serves both
   * surfaces. **Adaptation:** the reference files this under Settings; this app has none until
   * P6c, so it sits in View beside the other two toggles and moves once P6c lands.
   */
  onToggleAutoClosingBrackets: () -> Unit = {},
  /** `toc:show-panel`'s content - every heading in the notebook; see [ViewMenu]. */
  tableOfContentsHeadings: List<TocHeading> = emptyList(),
  /** `toc:show-panel`'s pick, in [TocPresentation.OVERLAY] - jumps to the chosen heading's cell. */
  onTableOfContentsHeadingSelected: (String) -> Unit = {},
  /** Whether the editor is drawing the list itself; see [tocPresentationFor]. */
  tableOfContentsPresentation: TocPresentation = TocPresentation.OVERLAY,
  /** Whether that pane is up, which is what the row is named from - see [ViewMenu]. */
  tableOfContentsPaneOpen: Boolean = false,
  /** `toc:show-panel` in [TocPresentation.PANE], where the row is a toggle rather than a picker. */
  onToggleTableOfContentsPane: () -> Unit = {},
  /** Whether headings carry their number - what the two numbering rows are named from. */
  tocNumbering: TocNumbering = TocNumbering(),
  /** `toc:display-numbering`. */
  onToggleTocNumbering: () -> Unit = {},
  /** `toc:display-h1-numbering`. */
  onToggleTocNumberingH1: () -> Unit = {},
  /** Whether a code cell's printed headings are listed - what the row is named from. */
  tocIncludeOutput: Boolean = true,
  /** `toc:display-outputs-numbering`. */
  onToggleTocIncludeOutput: () -> Unit = {},
  /** `documentsearch:start` - see [FindTopBar], which is what it puts up. */
  onFind: () -> Unit = {},
) {
  var renameOpen by remember { mutableStateOf(false) }

  TopAppBar(
    title = { RenameableTitle(title = title, onClick = { renameOpen = true }) },
    navigationIcon = {
      IconButton(onClick = onBack) {
        // Named for where it goes, not for the gesture. "Back" is also the system navigation
        // bar's button, and a `uiautomator` dump has both - so five Maestro flows meaning this
        // arrow have been tapping the system one and passing anyway, because before the modal
        // back of U2 the two did the same thing. They stopped agreeing, which is how it was
        // found (`evidence/u-flow-sweep/`). A destination-naming label is also what TalkBack
        // wants: "Back" leaves a screen reader to guess back to what.
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to notebooks")
      }
    },
    actions = {
      UndoRedo(canUndo, canRedo, onUndo, onRedo)
      // On the bar rather than in the View menu, and that is what it is: searching is neither a
      // view toggle nor something asked of the kernel, and both Material and the reference put it
      // where a thumb can reach it in one tap - `documentsearch:start` is `Ctrl F` there, the
      // shortest binding in the app.
      IconButton(onClick = onFind, modifier = Modifier.semantics { contentDescription = FIND_ACTION }) {
        Icon(Icons.Filled.Search, contentDescription = null)
      }
      ViewMenu(
        onSetAllHeadingsCollapsed = onSetAllHeadingsCollapsed,
        lineNumbersShown = lineNumbersShown,
        onToggleLineNumbers = onToggleLineNumbers,
        autoClosingBrackets = autoClosingBrackets,
        onToggleAutoClosingBrackets = onToggleAutoClosingBrackets,
        tableOfContentsHeadings = tableOfContentsHeadings,
        onTableOfContentsHeadingSelected = onTableOfContentsHeadingSelected,
        tableOfContentsPresentation = tableOfContentsPresentation,
        tableOfContentsPaneOpen = tableOfContentsPaneOpen,
        onToggleTableOfContentsPane = onToggleTableOfContentsPane,
        tocNumbering = tocNumbering,
        onToggleTocNumbering = onToggleTocNumbering,
        onToggleTocNumberingH1 = onToggleTocNumberingH1,
        tocIncludeOutput = tocIncludeOutput,
        onToggleTocIncludeOutput = onToggleTocIncludeOutput,
      )
    },
  )

  if (renameOpen) {
    RenameFileDialog(
      currentTitle = title,
      onConfirm = { renamed ->
        renameOpen = false
        onRename(renamed)
      },
      onDismiss = { renameOpen = false },
    )
  }
}

/**
 * `notebook:undo-cell-action` and `notebook:redo-cell-action`, as the reference names them.
 *
 * **Disabled rather than absent** when there is nothing to take back, which is what the reference
 * does with `isEnabled` on both commands (`packages/notebook-extension/src/index.ts:4485`, `:4502`)
 * and what stops the pair from being a control nobody ever sees.
 *
 * The icons are the reference's, checked rather than chosen: `undo.svg` is the path
 * `M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62…`, which is Material's `AutoMirrored.Undo`
 * verbatim, and `redo.svg` is its mirror. Same reading that settled `FastForward` on [NotebookBar].
 *
 * The descriptions are the reference's labels - *"Undo Cell Operation"*, in this app's sentence
 * case - so a screen reader and a Maestro flow address one string, as the cell menu already does.
 *
 * **The name goes on the button, not on the icon inside it**, which is the same call
 * [RenameableTitle] makes and here it is load-bearing rather than tidy. `enabled` lands on the
 * `IconButton`'s node; a `contentDescription` on the `Icon` lands on a *child* of it. Named on the
 * icon, the accessibility tree has the label and the state on two different nodes - measured, not
 * reasoned: the first version put "Undo cell operation" on a node reporting `enabled=true` with a
 * disabled parent one level up, and a flow asking for the disabled control could not find it
 * (`evidence/p2a-undo-cell-operation/`). One node carries both now.
 */
@Composable
private fun UndoRedo(canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit) {
  IconButton(
    onClick = onUndo,
    enabled = canUndo,
    modifier = Modifier.semantics { contentDescription = "Undo cell operation" },
  ) {
    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
  }
  IconButton(
    onClick = onRedo,
    enabled = canRedo,
    modifier = Modifier.semantics { contentDescription = "Redo cell operation" },
  ) {
    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null)
  }
}

/**
 * The document's **View** menu, which the two heading commands open and P2g's toggles will fill.
 *
 * **The reference surfaces neither of them in a menu at all** - `collapse-all-headings` and
 * `expand-all-headings` are command-palette entries plus `Ctrl Shift Arrow` shortcuts
 * (`packages/notebook-extension/src/index.ts:5279-5281`, `schema/tracker.json:562-570`) - so where
 * they go here is an adaptation, and this is what it departs from. Its own overflow rather than
 * [NotebookBar]'s, because that bar states what it is: what you ask the *kernel*. Folding a section
 * away asks the kernel nothing, and the reference files it under View for the same reason.
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
private fun ViewMenu(
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
      Icon(Icons.Filled.MoreVert, contentDescription = "View actions")
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
    // Same Box as "View actions", so [TableOfContentsPanel]'s own `DropdownMenu` anchors to the
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

@Composable
private fun RenameableTitle(title: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.clickable(onClickLabel = "Rename notebook", onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(modifier = Modifier.width(8.dp))
    Icon(
      imageVector = Icons.Filled.Edit,
      // The whole row is one target, so the icon does not announce itself separately - naming
      // it again would give a screen reader two things to say about one control.
      contentDescription = null,
      modifier = Modifier.size(TITLE_EDIT_ICON_SIZE),
      tint = LocalContentColor.current,
    )
  }
}
