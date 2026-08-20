package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
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
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.theme.Spacing
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
  /** `toc:show-panel`'s pick, in [TocPresentation.OVERLAY] - jumps to the chosen heading's cell. */
  onTableOfContentsHeadingSelected: (String) -> Unit = {},
  /** Whether the editor is drawing the list itself; see [tocPresentationFor]. */
  tableOfContentsPresentation: TocPresentation = TocPresentation.OVERLAY,
  /** Whether that pane is up, which is what the row is named from - see [NotebookActionsMenu]. */
  tableOfContentsPaneOpen: Boolean = false,
  /** `toc:show-panel` in [TocPresentation.PANE], where the row is a toggle rather than a picker. */
  onToggleTableOfContentsPane: () -> Unit = {},
  /** `documentsearch:start` - see [FindTopBar], which is what it puts up. */
  onFind: () -> Unit = {},
  /** `notebook:export-to-format` - what its picker is doing; see [NotebookActionsMenu]. */
  exportState: ExportState = ExportState(),
  /** Asks the Space what it can convert to, and only when the picker is opened. */
  onExportPickerOpened: () -> Unit = {},
  onExportPickerDismissed: () -> Unit = {},
  onExportAs: (String) -> Unit = {},
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
        tableOfContentsPresentation = tableOfContentsPresentation,
        tableOfContentsPaneOpen = tableOfContentsPaneOpen,
        onToggleTableOfContentsPane = onToggleTableOfContentsPane,
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

@Composable
private fun RenameableTitle(title: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.clickable(onClickLabel = "Rename notebook", onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
