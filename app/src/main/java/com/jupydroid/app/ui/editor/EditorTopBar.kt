package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.Breadcrumb
import com.jupydroid.app.ui.DocumentDismissAction
import com.jupydroid.app.ui.RenameFileDialog
import com.jupydroid.app.ui.ancestorCrumbsFor

/** Small enough to read as part of the title rather than as a button beside it. */
private val TITLE_EDIT_ICON_SIZE = 18.dp

/**
 * The notebook's app bar: the document's name on a row of its own, and its actions below.
 *
 * **Two rows, because the reference has two.** In single-document mode - the mode that shows one
 * document at a time, which is this app's shape - `LabShell.mode`'s setter shows a title panel
 * above the document (`packages/application/src/shell.ts:690-705`), and that panel holds the
 * document's name and nothing else: `#jp-title-panel` is a strip whose only child is an `<input>`
 * carrying the title (`packages/application/style/titlepanel.css:10-37`). The toolbar sits below
 * it. Sharing one row was measured and it does not fit: at 320dp the title drew **64dp of the
 * 175dp** `Heading Sections.ipynb` needs, and truncated to `Heading…` - the number is
 * `EditorTopBarTest.theWholeFileNameFitsAtTheNarrowestWidth`'s own failure message before this.
 *
 * **The height is Material's own for this arrangement, not a stack that happens to add up.** A
 * 48dp title row over a 64dp action row is 112dp, which is `MediumTopAppBar`'s container height -
 * Material's two-row bar, with the same split. What differs is the order and the size: Material
 * puts the title *below* the actions at `headlineSmall`, the reference puts it above and smaller,
 * and it is the reference this app follows.
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
 * **The active cell's type is here because the reference's toolbar carries it** - `cellType` at
 * rank 40 (`packages/notebook-extension/schema/panel.json`) - and it is on *this* bar rather than
 * [NotebookBar] for that bar's own stated reason: it is what you ask the kernel, and what a cell
 * is asks the kernel nothing. Where the row has no width for it, it moves to the overflow the way
 * a `ReactiveToolbar` moves an item into its popup opener; see [CellTypeControl] and
 * [labelsFitOnActionRow].
 *
 * **The breadcrumb is between the two rows** (P2w-1), which is where `BreadcrumbsControl` sits
 * relative to a tab title in VS Code, and it is **absent** on a document at the workspace root -
 * see [ancestorCrumbsFor] for both departures. A tap navigates the file browser, which is
 * JupyterLab's behaviour rather than VS Code's picker (`packages/filebrowser/src/crumbs.ts:415`):
 * the re-base moved where this app takes its *presentation* from, never its behaviour.
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
  /**
   * Whether a browser is drawn beside this notebook, which decides only how [onBack] is presented -
   * see [DocumentDismissAction]. Passed in for the reason [cellTypeOnBar] is.
   */
  twoPane: Boolean = false,
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
  /** Whether the cells are drawing their gutters, which is what the View row is named from. */
  lineNumbersShown: Boolean = true,
  /** `notebook:toggle-all-cell-line-numbers` - see [NotebookActionsMenu]. */
  onToggleLineNumbers: () -> Unit = {},
  /**
   * The persisted, app-wide preferences the View menu's toggles read and write - see
   * [NotebookActionsMenu], which owns which row means which field.
   *
   * **Adaptation:** the reference files every one of these under Settings; this app has none until
   * P6c, so they sit in View and move once P6c lands.
   */
  settings: EditorSettings = EditorSettings(),
  onSettingsChanged: (EditorSettings) -> Unit = {},
  /** `toc:show-panel`'s content - every heading in the notebook; see [NotebookActionsMenu]. */
  tableOfContentsHeadings: List<TocHeading> = emptyList(),
  /** `toc:show-panel`'s pick on the overlay - jumps to the chosen heading's cell. */
  onTableOfContentsHeadingSelected: (String) -> Unit = {},
  /**
   * `toc:show-panel` where the shell has a side area: activates its panel, and nothing else -
   * `labShell.activateById(toc.id)` (`packages/toc-extension/src/index.ts:161-172`). Null where
   * there is no side area, which is what puts the overlay up instead.
   */
  onShowTableOfContentsPanel: (() -> Unit)? = null,
  /**
   * `Show call stack and variables`'s content - [DebuggerPanel]/
   * [DebuggerSidePanel][com.jupydroid.app.ui.shell.DebuggerSidePanel]'s own state.
   */
  debugStack: DebugStack? = null,
  /** [onShowTableOfContentsPanel]'s own rule, for the debugger's side panel. */
  onShowDebuggerPanel: (() -> Unit)? = null,
  /** [DebuggerPanel]'s own drill-down (P6a-14) - [NotebookDebugger.expand]. */
  onExpandVariable: suspend (Int) -> List<DebugVariable> = { emptyList() },
  /** [DebuggerPanel]'s own frame reselection (P6a-15) - [NotebookDebugger.selectFrame]. */
  onSelectFrame: suspend (Int) -> Unit = {},
  /** [DebuggerPanel]'s own ad hoc expression (P6a-17) - [NotebookDebugger.evaluate]. */
  onEvaluate: suspend (String) -> DebugVariable? = { null },
  /** `documentsearch:start` - see [FindTopBar], which is what it puts up. */
  onFind: () -> Unit = {},
  /** `notebook:export-to-format` - what its picker is doing; see [NotebookActionsMenu]. */
  exportState: ExportState = ExportState(),
  /** Asks the Space what it can convert to, and only when the picker is opened. */
  onExportPickerOpened: () -> Unit = {},
  onExportPickerDismissed: () -> Unit = {},
  onExportAs: (String) -> Unit = {},
  /**
   * How far the title's row has slid away as the notebook is read - see [titleCollapseFraction].
   * `0f` at the top of the document, `1f` once the strip is gone and the notebook has its 48dp.
   */
  titleCollapse: Float = 0f,
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
  var renameOpen by remember { mutableStateOf(false) }

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
    NotebookTitleStrip(title = title, onClick = { renameOpen = true }, collapse = titleCollapse)
    // **One fraction collapses both rows**, because `titleCollapseFraction` takes the region's
    // height as an argument - so widening the region is a change to what [EditorScreen] passes it,
    // not a second fraction that could disagree with this one.
    if (crumbs.isNotEmpty()) {
      Breadcrumb(crumbs = crumbs, onGoTo = onGoToFolder, modifier = Modifier.collapsedBy(titleCollapse))
    }
    TopAppBar(
      // The document's *name* is not here - it has the row above, which is what the reference's
      // single-document mode does (`application/style/titlepanel.css:10-37`). What this slot holds
      // is the space between the navigation icon and the actions, measured at **72dp at 320dp and
      // 552dp at 800dp** (`evidence/p2o-3-two-pane-threshold/`), and the reference lays its toolbar
      // out left to right by rank - so `cellType` at 40 belongs here, well before the spacer at
      // 100. Empty again where there is no room for it; see [labelsFitOnActionRow].
      title = {
        if (cellTypeOnBar && activeCellKind != null) {
          CellTypeControl(kind = activeCellKind, onPick = onChangeCellKind)
        }
      },
      // Already taken by the Column that holds both rows, just above.
      windowInsets = WindowInsets(0, 0, 0, 0),
      navigationIcon = {
        // Named for where it goes, not for the gesture. "Back" is also the system navigation
        // bar's button, and a `uiautomator` dump has both - so five Maestro flows meaning this
        // arrow have been tapping the system one and passing anyway, because before the modal
        // back of U2 the two did the same thing. They stopped agreeing, which is how it was
        // found (`evidence/u-flow-sweep/`). A destination-naming label is also what TalkBack
        // wants: "Back" leaves a screen reader to guess back to what.
        //
        // Beside a browser it is a *close* instead, and one home decides that for all three bars.
        DocumentDismissAction(twoPane = twoPane, backDescription = "Back to notebooks", onDismiss = onBack)
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
          lineNumbersShown = lineNumbersShown,
          onToggleLineNumbers = onToggleLineNumbers,
          settings = settings,
          onSettingsChanged = onSettingsChanged,
          tableOfContentsHeadings = tableOfContentsHeadings,
          onTableOfContentsHeadingSelected = onTableOfContentsHeadingSelected,
          onShowTableOfContentsPanel = onShowTableOfContentsPanel,
          debugStack = debugStack,
          onShowDebuggerPanel = onShowDebuggerPanel,
          onExpandVariable = onExpandVariable,
          onSelectFrame = onSelectFrame,
          onEvaluate = onEvaluate,
          // Moved, not copied: null once the bar above carries the control itself.
          cellTypeInMenu = activeCellKind?.takeUnless { cellTypeOnBar },
          onChangeCellKind = onChangeCellKind,
        )
      },
    )
  }

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
 * The document's name, on a row of its own, and the whole row is the rename target.
 *
 * **The reference's own shape, with two departures named.** `#jp-title-panel-title input` fills
 * the strip - `width: 100%`, transparent, borderless - so in JupyterLab the title *is* the field
 * and clicking anywhere on it starts the rename
 * (`packages/application/style/titlepanel.css:17-37`, `shell.ts` `TitleHandler`). Here the tap
 * opens [RenameFileDialog] instead of editing in place: a soft keyboard over an inline field at
 * the top of the screen leaves nothing to see what is being renamed, and the dialog is what every
 * other rename in this app already uses (`ui/RenameFileDialog.kt`).
 *
 * **The size is a Material role, not the reference's pixels.** The reference asks for
 * `calc(--jp-ui-font-size1 * 18 / 13)` = 18px against a 13px base, 1.38x. `titleMedium` is 16sp
 * against Material's 14sp body, and it is the role Material gives a title in a compact container -
 * stating a role rather than computing a size is what P2l-1 settled. What it costs is the weight:
 * the reference says `font-weight: normal` and `titleMedium` is 500.
 *
 * The height is Material's interactive minimum rather than the reference's 28px strip, because
 * unlike an `<input>` reached with a mouse this is a touch target.
 */
@Composable
private fun NotebookTitleStrip(title: String, onClick: () -> Unit, collapse: Float) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .collapsedBy(collapse)
      .clickable(onClickLabel = "Rename notebook", onClick = onClick)
      // The reference's own inset is `margin-left: 8px`; Material's horizontal margin for a bar's
      // content is 16dp, and the row below this one is drawn by `TopAppBar` on that margin - so
      // taking the reference's would step the two rows in and out by 8dp against each other.
      .padding(horizontal = Spacing.lg),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    Spacer(modifier = Modifier.width(Spacing.sm))
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

/**
 * How a row of this bar gives its height back as the notebook is read - see [titleCollapseFraction].
 *
 * **Shrunk rather than hidden**, so the notebook slides under it instead of jumping when the last
 * pixel goes, and clipped because whatever is inside keeps its own height whatever the row's is.
 * One modifier because two rows collapse now and they must do it identically; before P2w-1 the
 * title strip spelled it inline.
 *
 * @param collapse `0f` whole, `1f` gone.
 */
@Composable
private fun Modifier.collapsedBy(collapse: Float): Modifier =
  height(LocalMinimumInteractiveComponentSize.current * (1f - collapse)).clipToBounds()
