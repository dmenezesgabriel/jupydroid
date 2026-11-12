package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.extension.api.FocusRingDropdownMenuItem
import com.jupydroid.extension.api.FocusRingIconButton

/**
 * The notebook's overflow: everything the reference files under **View**, and under **File** as
 * well.
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
 * **And it is a third thing: the toolbar's own popup opener.** JupyterLab's notebook
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
  tableOfContentsHeadings: List<TocHeading>,
  onTableOfContentsHeadingSelected: (String) -> Unit,
  /**
   * How the overlay numbers what it lists - read-only here, because the toggles that set it live
   * in `SettingsScreen`; this menu only ever displayed them, through [TableOfContentsPanel].
   */
  tocNumbering: TocNumbering,
  /** `toc:show-panel` where the shell has a side area - see [EditorTopBar]. Null where it has none. */
  onShowTableOfContentsPanel: (() -> Unit)?,
  /**
   * [DebuggerSidePanel][com.jupydroid.app.ui.shell.DebuggerSidePanel]'s own rule, for the
   * debugger's side panel. Null where there is none - [DebuggerControlBar]'s own toggle is where
   * a one-pane window reaches the call stack and variables instead, since it is that bar and not
   * this menu that a paused kernel actually surfaces.
   */
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
  var open by remember { mutableStateOf(false) }
  // Its own flag rather than reusing [open]: picking "Table of contents" replaces this menu with
  // [TableOfContentsPanel] rather than closing it outright, the same two-step [CellActions]' own
  // heading-level picker already establishes.
  var tocOpen by remember { mutableStateOf(false) }
  // The export picker, on its own flag for [tocOpen]'s reason: picking a format is a second step,
  // and `CellActionSurface` already cost a commit to learn that a row which opens something must
  // not also dismiss the surface it opened.
  var exportOpen by remember { mutableStateOf(false) }
  // Its own flag for [tocOpen]'s reason: the row opens [CellTypePicker] rather than acting, and a
  // row that opens something must not also dismiss the surface it opened.
  var cellTypeOpen by remember { mutableStateOf(false) }
  Box {
    FocusRingIconButton(onClick = { open = true }) {
      Icon(Icons.Filled.MoreVert, contentDescription = "Notebook actions")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      // Above the reference's two menus and divided off, because it did not come from either: it is
      // the toolbar's own item, arriving here only because the row could not hold it.
      cellTypeInMenu?.let { kind ->
        FocusRingDropdownMenuItem(
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
      FocusRingDropdownMenuItem(
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
      FocusRingDropdownMenuItem(
        text = { Text(COLLAPSE_ALL_HEADINGS) },
        onClick = {
          open = false
          onSetAllHeadingsCollapsed(true)
        },
      )
      FocusRingDropdownMenuItem(
        text = { Text(EXPAND_ALL_HEADINGS) },
        onClick = {
          open = false
          onSetAllHeadingsCollapsed(false)
        },
      )
      // One row, two jobs, and which one is whether the shell has a side area. With one, this is
      // `toc:show-panel` exactly as the reference defines it - it *activates* the sidebar panel and
      // has no toggled state to name (`packages/toc-extension/src/index.ts:161-172`). Without one,
      // it opens the overlay, which is the same row reading the same thing, since a pick dismisses
      // that surface and leaves nothing to name either.
      FocusRingDropdownMenuItem(
        text = { Text(SHOW_TABLE_OF_CONTENTS) },
        onClick = {
          open = false
          onShowTableOfContentsPanel?.invoke() ?: run { tocOpen = true }
        },
      )
      // Only where the shell has a side area to activate - [SHOW_TABLE_OF_CONTENTS]'s own
      // reasoning does not apply here the way it does there: a one-pane window has
      // [DebuggerControlBar]'s own toggle for this instead, reachable only while a kernel is
      // actually paused, so this menu has nothing to offer in that case at all.
      if (onShowDebuggerPanel != null) {
        FocusRingDropdownMenuItem(
          text = { Text(SHOW_DEBUGGER_PANEL) },
          onClick = {
            open = false
            onShowDebuggerPanel()
          },
        )
      }
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
      state.loadingFormats -> FocusRingDropdownMenuItem(
        text = { Text(LOADING_EXPORT_FORMATS) },
        enabled = false,
        onClick = {},
      )
      state.offered.isEmpty() -> FocusRingDropdownMenuItem(
        text = { Text(NO_EXPORT_FORMATS) },
        enabled = false,
        onClick = {},
      )
      else -> state.offered.forEach { format ->
        FocusRingDropdownMenuItem(text = { Text(format.label) }, onClick = { onPick(format.id) })
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
 * `toc:show-panel` (`packages/toc-extension/src/index.ts:161-172`), and **one label rather than
 * two** - because the reference's command departs from a toggle: it activates the sidebar panel
 * and declares no `isToggled`. Where the shell has no side area the row opens
 * [TableOfContentsPanel], whose pick dismisses it, so there is no state to name on that side
 * either.
 */
internal const val SHOW_TABLE_OF_CONTENTS = "Show table of contents"

/**
 * One label, not two, for [SHOW_TABLE_OF_CONTENTS]'s own reason: [DebuggerPanel]'s row is read-only
 * and declares no toggled state either. Named for what it shows rather than quoted from a reference
 * command - JupyterLab's own Debugger sidebar has no "show" command to mirror; see [DebuggerPanel].
 */
internal const val SHOW_DEBUGGER_PANEL = "Show call stack and variables"
