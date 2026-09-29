package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.ui.Breadcrumb
import com.jupydroid.app.ui.ancestorCrumbsFor

/**
 * The notebook's app bar: its actions, with the breadcrumb trail above them.
 *
 * **The document's name is not here - the tab owns it.** In tabbed mode the reference shows no
 * title panel at all: `#jp-title-panel` exists only in `single-document` mode
 * (`packages/application/src/shell.ts:690-705`), which P8e does not run - the name, the dirty
 * dot and the rename affordance all live on the `lm-TabBar-tab` now, where
 * `[data-type="document-title"]` puts them (`docmanager-extension/src/index.tsx:122`). What this
 * bar keeps is what the reference's toolbar keeps: the actions and the kind of cell the caret is
 * on.
 *
 * **The breadcrumb is above the action row**, which is where `BreadcrumbsControl` sits
 * relative to a tab title in VS Code, and it is **absent** on a document at the workspace root -
 * see [ancestorCrumbsFor] for both departures. A tap navigates the file browser, which is
 * JupyterLab's behaviour rather than VS Code's picker (`packages/filebrowser/src/crumbs.ts:415`).
 * It collapses as the notebook scrolls, the way the title row it replaced did - see
 * [collapsibleRegionFraction].
 *
 * Run All lives on [NotebookBar] now, not here. It is a kernel action like interrupt and restart,
 * it belongs beside them rather than alone at the far corner of the screen, and the bottom of a
 * phone is where a thumb already is.
 *
 * **The active cell's type is here because the reference's toolbar carries it** - `cellType` at
 * rank 40 (`packages/notebook-extension/schema/panel.json`) - and it is on *this* bar rather than
 * [NotebookBar] for that bar's own stated reason: it is what you ask the kernel, and what a cell
 * is asks the kernel nothing. Where the row has no width for it, it moves to the overflow the way
 * a `ReactiveToolbar` moves an item into its popup opener; see [CellTypeControl] and
 * [labelsFitOnActionRow].
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
  /** Whether there is a cell operation to take back; the button is shown either way, see below. */
  canUndo: Boolean,
  canRedo: Boolean,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  /**
   * What the active cell is, for the control that names it - see [CellTypeControl]. Null on a
   * notebook with no active cell, which draws nothing rather than a control about nothing.
   */
  activeCellKind: CellKind? = null,
  /** `notebook:change-cell-to-{code,markdown,raw}`, from the control above. */
  onChangeCellKind: (CellKind) -> Unit = {},
  /**
   * Whether this bar has the room to carry [activeCellKind] itself - [labelsFitOnActionRow].
   *
   * Passed in rather than read here, because `currentWindowAdaptiveInfo()` measures the
   * *activity's* window and so does not follow a test's `ForcedSize` (`Navigation.kt`,
   * `PaneDirective.kt`) - the same seam [EditorScreen] already crosses for the side area.
   */
  cellTypeOnBar: Boolean = false,
  /** `notebook:collapse-all-headings` / `expand-all-headings` - see [NotebookActionsMenu]. */
  onSetAllHeadingsCollapsed: (Boolean) -> Unit = {},
  /** `toc:show-panel`'s content - every heading in the notebook; see [NotebookActionsMenu]. */
  tableOfContentsHeadings: List<TocHeading> = emptyList(),
  /** `toc:show-panel`'s pick on the overlay - jumps to the chosen heading's cell. */
  onTableOfContentsHeadingSelected: (String) -> Unit = {},
  /** How the overlay numbers what it lists - see [NotebookActionsMenu]. */
  tocNumbering: TocNumbering = TocNumbering(),
  /**
   * `toc:show-panel` where the shell has a side area: activates its panel, and nothing else -
   * `labShell.activateById(toc.id)` (`packages/toc-extension/src/index.ts:161-172`). Null where
   * there is no side area, which is what puts the overlay up instead.
   */
  onShowTableOfContentsPanel: (() -> Unit)? = null,
  /**
   * [DebuggerSidePanel][com.jupydroid.app.ui.shell.DebuggerSidePanel]'s own rule, for the
   * debugger's side panel. Null where there is none - [DebuggerControlBar]'s own toggle is where
   * a one-pane window reaches the call stack and variables instead.
   */
  onShowDebuggerPanel: (() -> Unit)? = null,
  /** `documentsearch:start` - see [FindTopBar], which is what it puts up. */
  onFind: () -> Unit = {},
  /** `notebook:export-to-format` - what its picker is doing; see [NotebookActionsMenu]. */
  exportState: ExportState = ExportState(),
  /** Asks the Space what it can convert to, and only when the picker is opened. */
  onExportPickerOpened: () -> Unit = {},
  onExportPickerDismissed: () -> Unit = {},
  onExportAs: (String) -> Unit = {},
  /**
   * How far the breadcrumb's row has slid away as the notebook is read - see
   * [collapsibleRegionFraction]. `0f` at the top of the document, `1f` once the row is gone.
   */
  collapsibleRegion: Float = 0f,
  /**
   * Where the document lives, root first - [ancestorCrumbsFor]. **Empty draws no row at all**,
   * which is what a notebook at the workspace root gets.
   */
  crumbs: List<Pair<String, String>> = emptyList(),
  /** A segment was tapped: browse that folder. `""` is the workspace root, not "no target". */
  onGoToFolder: (String) -> Unit = {},
  /**
   * The kernel indicator, at the right of this row - see [KernelIndicator].
   *
   * **A slot rather than a dozen parameters**, because what the control opens is [KernelMenu] and
   * that menu needs every kernel callback the bottom bar already takes. Passed as content, this bar
   * gains one parameter and still decides the one thing that is its business: *where* the indicator
   * sits. [EditorTopBars] fills it, which is where this screen's wiring already lives.
   */
  kernelIndicator: @Composable () -> Unit = {},
) {
  // **The insets are the Column's, and the bar below it is told to take none.** `TopAppBar`
  // consumes `TopAppBarDefaults.windowInsets` itself; left to do so under a strip that is now
  // above it, it padded the *second* row down by the status bar's height while the first row was
  // drawn underneath the clock. Caught by looking at the screenshot and by nothing else - no
  // assertion in this suite sees a system inset, because the test environment has none.
  Column(
    modifier = Modifier
      .background(TopAppBarDefaults.topAppBarColors().containerColor)
      .windowInsetsPadding(TopAppBarDefaults.windowInsets),
  ) {
    if (crumbs.isNotEmpty()) {
      Breadcrumb(crumbs = crumbs, onGoTo = onGoToFolder, modifier = Modifier.collapsedBy(collapsibleRegion))
    }
    TopAppBar(
      // The space between the navigation icon and the actions, measured at **72dp at 320dp and
      // 552dp at 800dp**, and the reference lays its toolbar
      // out left to right by rank - so `cellType` at 40 belongs here, well before the spacer at
      // 100. Empty again where there is no room for it; see [labelsFitOnActionRow].
      title = {
        if (cellTypeOnBar && activeCellKind != null) {
          CellTypeControl(kind = activeCellKind, onPick = onChangeCellKind)
        }
      },
      // Already taken by the Column that holds both rows, just above.
      windowInsets = WindowInsets(0, 0, 0, 0),
      // No navigationIcon: leaving the document is the tab strip's own act - the ✕ beside a
      // browser, the destination-named arrow below one pane (see `MainAreaTabStrip`). The bar
      // itself is actions only, which is what the reference's `jp-NotebookPanel-toolbar` is too.
      actions = {
        UndoRedo(canUndo, canRedo, onUndo, onRedo)
        // On the bar rather than in the View menu, and that is what it is: searching is neither a
        // view toggle nor something asked of the kernel, and both Material and the reference put it
        // where a thumb can reach it in one tap - `documentsearch:start` is `Ctrl F` there, the
        // shortest binding in the app.
        IconButton(onClick = onFind, modifier = Modifier.semantics { contentDescription = FIND_ACTION }) {
          Icon(Icons.Filled.Search, contentDescription = null)
        }
        // **Between Find and the overflow, which is the reference's own far right.** JupyterLab
        // ranks `kernelName` 1000 and `executionProgress` 1002, after the spacer at 100 - but its
        // toolbar's last widget is the popup opener, which `ReactiveToolbar` inserts first and
        // never collects (`toolbar.tsx:391`, `:470-480`, `:661-700`). Ours is the `⋮` below.
        kernelIndicator()
        NotebookActionsMenu(
          exportState = exportState,
          onExportPickerOpened = onExportPickerOpened,
          onExportPickerDismissed = onExportPickerDismissed,
          onExportAs = onExportAs,
          onSetAllHeadingsCollapsed = onSetAllHeadingsCollapsed,
          tableOfContentsHeadings = tableOfContentsHeadings,
          onTableOfContentsHeadingSelected = onTableOfContentsHeadingSelected,
          tocNumbering = tocNumbering,
          onShowTableOfContentsPanel = onShowTableOfContentsPanel,
          onShowDebuggerPanel = onShowDebuggerPanel,
          // Moved, not copied: null once the bar above carries the control itself.
          cellTypeInMenu = activeCellKind?.takeUnless { cellTypeOnBar },
          onChangeCellKind = onChangeCellKind,
        )
      },
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
 * **The name goes on the button, not on the icon inside it**, and here it is load-bearing
 * rather than tidy: `enabled` lands on the
 * `IconButton`'s node; a `contentDescription` on the `Icon` lands on a *child* of it. Named on the
 * icon, the accessibility tree has the label and the state on two different nodes - measured, not
 * reasoned: the first version put "Undo cell operation" on a node reporting `enabled=true` with a
 * disabled parent one level up, and a flow asking for the disabled control could not find it.
 * One node carries both now.
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
 * How a row of this bar gives its height back as the notebook is read - see
 * [collapsibleRegionFraction].
 *
 * **Shrunk rather than hidden**, so the notebook slides under it instead of jumping when the last
 * pixel goes, and clipped because whatever is inside keeps its own height whatever the row's is.
 *
 * @param collapse `0f` whole, `1f` gone.
 */
@Composable
private fun Modifier.collapsedBy(collapse: Float): Modifier =
  height(LocalMinimumInteractiveComponentSize.current * (1f - collapse)).clipToBounds()
