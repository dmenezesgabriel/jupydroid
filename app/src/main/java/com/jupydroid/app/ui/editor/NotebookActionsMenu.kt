package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.model.EditorSettings

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
 * **And since P2r-2 it is a third thing: the toolbar's own popup opener.** JupyterLab's notebook
 * toolbar is a `ReactiveToolbar`, which *moves* an item too wide for the row into a popup rather
 * than dropping it (`packages/apputils/src/mainareawidget.ts:51`,
 * `packages/ui-components/src/components/toolbar.tsx:570-700`). Measured, the cell-type control
 * does not fit at 320dp, so at that width it arrives here - [cellTypeInMenu]. That does not widen
 * this menu into a junk drawer: what it holds is still "the reference's File and View menus, plus
 * whatever the reference's own toolbar could not fit", and the row is first and divided off so it
 * reads as the second thing rather than as more View.
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
  /** `notebook:export-to-format` - what the picker is doing; see [NotebookExport]. */
  exportState: ExportState,
  /** Ask the Space what it can convert to. Called when the picker opens, never before. */
  onExportPickerOpened: () -> Unit,
  onExportPickerDismissed: () -> Unit,
  onExportAs: (String) -> Unit,
  onSetAllHeadingsCollapsed: (Boolean) -> Unit,
  /**
   * Per **notebook** and per sitting, which is why it is not in [settings]: the reference writes
   * `notebook.editorConfig`, which belongs to the widget - see [EditorUiState.lineNumbersShown].
   */
  lineNumbersShown: Boolean,
  onToggleLineNumbers: () -> Unit,
  /**
   * The persisted, app-wide preferences every other toggle here reads and writes.
   *
   * Whole rather than one boolean and one callback per row, which is what this was: four settings
   * cost eight parameters at each of three layers, and each row's own
   * `onSettingsChanged(settings.copy(x = !settings.x))` had to be written a layer away from the row
   * that knew which field it meant. A fifth setting now costs a row and nothing else.
   */
  settings: EditorSettings,
  onSettingsChanged: (EditorSettings) -> Unit,
  tableOfContentsHeadings: List<TocHeading>,
  onTableOfContentsHeadingSelected: (String) -> Unit,
  /** `toc:show-panel` where the shell has a side area - see [EditorTopBar]. Null where it has none. */
  onShowTableOfContentsPanel: (() -> Unit)?,
  /** [DebuggerPanel]'s content - see [EditorTopBar]. */
  debugStack: DebugStack?,
  /** [onShowTableOfContentsPanel]'s own rule, for the debugger's side panel. */
  onShowDebuggerPanel: (() -> Unit)?,
  /**
   * The active cell's type, where the bar has no room to name it itself - see [labelsFitOnActionRow].
   *
   * Null where the bar carries it, and null where there is no active cell: a `ReactiveToolbar`
   * moves an item into its popup, it does not copy it there.
   */
  cellTypeInMenu: CellKind? = null,
  /** `notebook:change-cell-to-{code,markdown,raw}`, from the row above. */
  onChangeCellKind: (CellKind) -> Unit = {},
) {
  val tocNumbering = settings.tocNumbering()
  var open by remember { mutableStateOf(false) }
  // Its own flag rather than reusing [open]: picking "Table of contents" replaces this menu with
  // [TableOfContentsPanel] rather than closing it outright, the same two-step [CellActions]' own
  // heading-level picker already establishes.
  var tocOpen by remember { mutableStateOf(false) }
  // [tocOpen]'s own reason: picking "Show call stack and variables" replaces this menu with
  // [DebuggerPanel] rather than closing it outright.
  var debuggerOpen by remember { mutableStateOf(false) }
  // The export picker, on its own flag for [tocOpen]'s reason: picking a format is a second step,
  // and `CellActionSurface` already cost a commit to learn that a row which opens something must
  // not also dismiss the surface it opened.
  var exportOpen by remember { mutableStateOf(false) }
  // Its own flag for [tocOpen]'s reason: the row opens [CellTypePicker] rather than acting, and a
  // row that opens something must not also dismiss the surface it opened.
  var cellTypeOpen by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }) {
      Icon(Icons.Filled.MoreVert, contentDescription = "Notebook actions")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      // Above the reference's two menus and divided off, because it did not come from either: it is
      // the toolbar's own item, arriving here only because the row could not hold it.
      cellTypeInMenu?.let { kind ->
        DropdownMenuItem(
          text = { Text(cellTypeRowLabel(kind)) },
          onClick = {
            open = false
            cellTypeOpen = true
          },
        )
        HorizontalDivider()
      }
      // The reference's File menu, at the top of the one overflow this bar has - and first,
      // because it is the only row here that changes anything outside the document.
      DropdownMenuItem(
        text = { Text(EXPORT_NOTEBOOK) },
        onClick = {
          open = false
          exportOpen = true
          onExportPickerOpened()
        },
      )
      // The seam between the reference's two menus, drawn the way its own schema draws one:
      // `"type": "separator"`.
      HorizontalDivider()
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
        text = {
          Text(if (settings.autoClosingBrackets) DISABLE_AUTO_CLOSING_BRACKETS else ENABLE_AUTO_CLOSING_BRACKETS)
        },
        onClick = {
          open = false
          onSettingsChanged(settings.copy(autoClosingBrackets = !settings.autoClosingBrackets))
        },
      )
      DropdownMenuItem(
        text = { Text(if (settings.accessKernelHistory) DISABLE_KERNEL_HISTORY else ENABLE_KERNEL_HISTORY) },
        onClick = {
          open = false
          onSettingsChanged(settings.copy(accessKernelHistory = !settings.accessKernelHistory))
        },
      )
      // One row, two jobs, and which one is whether the shell has a side area. With one, this is
      // `toc:show-panel` exactly as the reference defines it - it *activates* the sidebar panel and
      // has no toggled state to name (`packages/toc-extension/src/index.ts:161-172`). Without one,
      // it opens the overlay, which is the same row reading the same thing, since a pick dismisses
      // that surface and leaves nothing to name either.
      DropdownMenuItem(
        text = { Text(SHOW_TABLE_OF_CONTENTS) },
        onClick = {
          open = false
          onShowTableOfContentsPanel?.invoke() ?: run { tocOpen = true }
        },
      )
      DropdownMenuItem(
        text = { Text(if (tocNumbering.numberHeaders) HIDE_HEADING_NUMBERS else SHOW_HEADING_NUMBERS) },
        onClick = {
          open = false
          onSettingsChanged(settings.copy(tocNumberHeaders = !settings.tocNumberHeaders))
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
            onSettingsChanged(settings.copy(tocNumberingH1 = !settings.tocNumberingH1))
          },
        )
      }
      DropdownMenuItem(
        text = { Text(if (settings.tocIncludeOutput) HIDE_OUTPUT_HEADINGS else SHOW_OUTPUT_HEADINGS) },
        onClick = {
          open = false
          onSettingsChanged(settings.copy(tocIncludeOutput = !settings.tocIncludeOutput))
        },
      )
      // One row, two jobs, and which one is whether the shell has a side area - [SHOW_TABLE_OF_CONTENTS]'s
      // own reasoning. Always offered, like that row: an empty stack draws [DebuggerPanel]'s own
      // placeholder rather than the row being disabled.
      DropdownMenuItem(
        text = { Text(SHOW_DEBUGGER_PANEL) },
        onClick = {
          open = false
          onShowDebuggerPanel?.invoke() ?: run { debuggerOpen = true }
        },
      )
    }
    // Same Box as "Notebook actions", so [TableOfContentsPanel]'s own `DropdownMenu` anchors to the
    // button that opened it rather than to wherever this composable happens to sit in the screen.
    // Never both surfaces at once: with the editor drawing the list, a copy over the top of it
    // would be two answers to one question.
    TableOfContentsPanel(
      expanded = tocOpen,
      headings = tableOfContentsHeadings,
      onHeadingSelected = onTableOfContentsHeadingSelected,
      onDismiss = { tocOpen = false },
      numbering = tocNumbering,
    )
    // In the same Box for the same reason - see [TableOfContentsPanel]'s own comment just above.
    DebuggerPanel(expanded = debuggerOpen, stack = debugStack, onDismiss = { debuggerOpen = false })
    // In the same Box for the same reason, so it anchors to the button that was tapped.
    ExportPicker(
      expanded = exportOpen,
      state = exportState,
      onDismiss = {
        exportOpen = false
        onExportPickerDismissed()
      },
      onPick = { format ->
        exportOpen = false
        onExportAs(format)
      },
    )
    // The same picker the bar's own control opens, so there is one list of types and not two.
    CellTypePicker(
      expanded = cellTypeOpen,
      onDismiss = { cellTypeOpen = false },
      onPick = { kind ->
        cellTypeOpen = false
        onChangeCellKind(kind)
      },
    )
  }
}

/**
 * The formats to export to, as the second step of one row.
 *
 * **The reference has this as a submenu of File** and builds it from a list it fetched when the
 * first notebook opened (`packages/notebook-extension/src/index.ts:855-890`). Ours fetches when
 * this opens, because the Space is started on demand - so this is the one menu in the app that can
 * be empty while it is still finding out, and [LOADING_EXPORT_FORMATS] is that state rather than a
 * spinner: Android's accessibility tree carries a `DropdownMenuItem`'s *label*, and a
 * `CircularProgressIndicator` in a menu is a control no flow can read.
 *
 * A Space that offers nothing says so in a row rather than opening an empty menu, which is what
 * the reference's own guidance dialog does - *"Notebook exports are unavailable"* (`:938`).
 */
@Composable
private fun ExportPicker(expanded: Boolean, state: ExportState, onDismiss: () -> Unit, onPick: (String) -> Unit) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    when {
      state.loadingFormats -> DropdownMenuItem(
        text = { Text(LOADING_EXPORT_FORMATS) },
        enabled = false,
        onClick = {},
      )
      state.offered.isEmpty() -> DropdownMenuItem(
        text = { Text(NO_EXPORT_FORMATS) },
        enabled = false,
        onClick = {},
      )
      else -> state.offered.forEach { format ->
        DropdownMenuItem(text = { Text(format.label) }, onClick = { onPick(format.id) })
      }
    }
  }
}

/**
 * `notebook:export-to-format`, under the label the reference's own menu gives the submenu it
 * opens - *"Save and Export Notebook As"* (`packages/notebook-extension/schema/export.json:16`),
 * with this app's ellipsis, which is what says a row will ask rather than act.
 */
internal const val EXPORT_NOTEBOOK = "Save and Export Notebook As…"

/** The Space is being asked, which the reference never has to say - see [ExportPicker]. */
internal const val LOADING_EXPORT_FORMATS = "Finding export formats…"

/** The reference's own wording for a deployment that can export nothing (`index.ts:938`). */
internal const val NO_EXPORT_FORMATS = "No export formats are available"

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
 * `accessKernelHistory` - the setting the two `notebook:access-*-history-entry` commands are gated
 * on, and with them the pair of keys [SymbolRow] draws.
 *
 * **Adaptation, and it is where rather than whether.** The reference puts this in Settings and
 * says so in its own user guide (`docs/source/user/notebook.md:191`); this app has no settings
 * screen until P6c, so it sits in View beside the other persisted toggles and moves with them.
 * Two labels rather than one and a check mark, for [SHOW_LINE_NUMBERS]'s reason.
 */
internal const val ENABLE_KERNEL_HISTORY = "Enable kernel history access"

/** The same setting from the other side - see [ENABLE_KERNEL_HISTORY]. */
internal const val DISABLE_KERNEL_HISTORY = "Disable kernel history access"

/**
 * `toc:show-panel` (`packages/toc-extension/src/index.ts:161-172`), and **one label rather than
 * two** - which departs from [SHOW_LINE_NUMBERS]' convention on purpose, because the reference's
 * command departs from a toggle: it activates the sidebar panel and declares no `isToggled`. Where
 * the shell has no side area the row opens [TableOfContentsPanel], whose pick dismisses it, so
 * there is no state to name on that side either.
 */
internal const val SHOW_TABLE_OF_CONTENTS = "Show table of contents"

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

/**
 * One label, not two, for [SHOW_TABLE_OF_CONTENTS]'s own reason: [DebuggerPanel]'s row is read-only
 * and declares no toggled state either. Named for what it shows rather than quoted from a reference
 * command - JupyterLab's own Debugger sidebar has no "show" command to mirror; see [DebuggerPanel].
 */
internal const val SHOW_DEBUGGER_PANEL = "Show call stack and variables"
