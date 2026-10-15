package com.jupydroid.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.Explorer
import com.jupydroid.app.Launcher
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.SpacesHome
import com.jupydroid.app.activateMainAreaTab
import com.jupydroid.app.checkpointSaved
import com.jupydroid.app.closeMainAreaTab
import com.jupydroid.app.documentIn
import com.jupydroid.app.documentOpening
import com.jupydroid.app.domain.port.DocumentCheckpoints
import com.jupydroid.app.domain.port.FileNameTakenException
import com.jupydroid.app.domain.port.WorkspaceFiles
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.launcherIsClosable
import com.jupydroid.app.liveDocumentPath
import com.jupydroid.app.mainAreaTabTitle
import com.jupydroid.app.runningTabTitle
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.OverwriteFileDialog
import com.jupydroid.app.ui.RenameFileDialog
import kotlinx.coroutines.launch

/**
 * A detail entry drawn the way the reference's main area is drawn: `lm-DockPanel-tabBar` across
 * the top, the document beneath it.
 *
 * **The strip lives inside the entry, not beside `NavDisplay`**, because `ListDetailSceneStrategy`
 * is what knows where the detail pane is - at two panes it is the right half and at one it is the
 * whole window, and only the entry's own content lands in both places without the shell measuring
 * the layout twice. Every detail entry wraps itself in this, which is also what makes
 * "Launcher/Settings/Terminal/Console are tabs too" free: they are already entries on the stack,
 * and the stack is the strip's model.
 *
 * The reference draws the strip at every width - `single-document` mode was read and rejected on
 * that ground - so no `twoPane` branch exists here.
 *
 * [tabs] is the strip's own order - **opening order**, not the stack's: `activateById` changes
 * which widget is current and never moves a tab, so a click must not reorder the row even though
 * the stack itself promoted the entry for the scene to draw it.
 */
@Composable
fun TabbedDocumentPane(
  backStack: MutableList<NavKey>,
  openDocuments: OpenDocuments,
  /**
   * Beside the stack because a key keeps the path it opened under - the screens write their
   * live path into it on every move, and the strip's own rename writes it too: `emit` suspends
   * until the document's `WorkspaceChange.Renamed` collector has adopted, so by the time the
   * write lands the document already answers to the new path and the map is what recomposes the
   * tab that shows it.
   */
  openDocumentPaths: MutableMap<NavKey, String>,
  tabs: List<NavKey>,
  /**
   * A label a screen reports while it is alive, ahead of the key's static one - today the
   * terminal's OSC title, which the reference sets straight on `title.label` and so onto the
   * tab (`packages/terminal/src/widget.ts:486-488`). Nothing a document tab reports: its live
   * label already comes from the document's own path.
   */
  liveTabTitles: Map<NavKey, String>,
  workspaceRepository: WorkspaceRepository,
  /** What the tab's Rename… and Delete… act through; [workspaceRepository] is what Save As writes. */
  workspaceFiles: WorkspaceFiles,
  /**
   * Whether a browser is drawn beside the document, which decides one thing: whether the strip
   * leads with the Up affordance. Below two panes the document *is* the window and leaving it
   * goes back to what was underneath - Material's navigation icon on the detail pane's top bar,
   * which this strip is, and the same place IntelliJ keeps its Back/Forward arrows
   * (`EditorTabbedContainer`'s toolbar). Beside a browser there is nothing to go back *to* and
   * closing is the tab's ✕, so the strip draws nothing there.
   */
  twoPane: Boolean = true,
  /**
   * The one act that empties a main-area slot - `application:close` - shared by the strip's ✕,
   * the one-pane arrow and the Running panel's own close, which is why it is *handed in* from
   * above: the Running panel lives outside this entry, so `rememberMainAreaCloseGate` owns the
   * dirty ask where both can reach it.
   */
  onCloseTab: (NavKey) -> Unit,
  /** `docmanager:open` on a path - where a Save As's copy opens, recorded in Recents as any open is. */
  onOpenDocument: (String) -> Unit,
  /** Where a document's checkpoint is made by the strip's Save and a Save As, and reverted to from its menu. */
  documentCheckpoints: DocumentCheckpoints,
  content: @Composable () -> Unit,
) {
  // The tab a rename is being asked over - held here rather than inside a tab so the dialog
  // outlives that tab's recomposition (its own long-press is what opened the ask).
  var renaming by remember { mutableStateOf<PendingTabRename?>(null) }
  // The rename the repository refused - `renameFile`'s 409 becomes `shouldOverwrite`, the dialog
  // below, and only then `overwrite` (`packages/docmanager/src/dialogs.ts`).
  var overwriting by remember { mutableStateOf<TakenTabName?>(null) }
  // Why the disk refused a rename or an overwrite, until the user has read it.
  var refusal by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  // The copy holds the edits now, so the old tab drops them unwritten and closes without the dirty
  // ask - its file keeps what was last saved, as `closeFile` leaves it in the reference.
  val saveAs = rememberTabSaveAs(workspaceRepository) { key, document, copy ->
    document.discardChanges()
    backStack.closeMainAreaTab(key, tabs)
    scope.launch { documentCheckpoints.checkpointSaved(copy) }
    onOpenDocument(copy)
  }
  val revertToCheckpoint = rememberTabRevertToCheckpoint(documentCheckpoints)
  // The strip closes the deleted tab itself, on top or beneath, as `deleteWidgets` disposes every
  // widget on the file: a screen beneath the top is not composed to hear its file go.
  val delete = rememberTabDelete(workspaceFiles) { key, document ->
    document.discardChanges()
    backStack.closeMainAreaTab(key, tabs)
  }

  Column(modifier = Modifier.fillMaxSize()) {
    MainAreaTabStrip(
      backStack = backStack,
      openDocuments = openDocuments,
      liveDocumentPaths = openDocumentPaths,
      liveTabTitles = liveTabTitles,
      tabs = tabs,
      twoPane = twoPane,
      onCloseTab = onCloseTab,
      onRenameTab = { key ->
        val path = key.documentIn(openDocuments)?.path ?: key.liveDocumentPath(openDocumentPaths)
        path?.let { renaming = PendingTabRename(key, it) }
      },
      onSaveTabAs = { key -> key.documentIn(openDocuments)?.let { saveAs(key, it) } },
      onRevertTab = { key -> key.documentIn(openDocuments)?.let(revertToCheckpoint) },
      onDeleteTab = { key -> key.documentIn(openDocuments)?.let { delete(key, it) } },
      onSaved = { document -> scope.launch { documentCheckpoints.checkpointSaved(document.path) } },
    )
    Box(modifier = Modifier.weight(1f)) { content() }
  }

  // `docmanager:rename` (`docmanager-extension/schema/plugin.json:75`, bound to the tab's own
  // node through `data-type="document-title"`): the reference's Rename File dialog, stem selected
  // and the same `isValidFileName` rule - the dialog is shared with the browser's row rename for
  // exactly the reason JupyterLab shares `DocumentManagerDialogs.rename`.
  renaming?.let { pending ->
    RenameFileDialog(
      currentTitle = pending.path.substringAfterLast('/'),
      heading = "Rename file",
      onConfirm = { name ->
        renaming = null
        // `context.rename` - the contents layer, so the document adopts through its own
        // `WorkspaceChange.Renamed` listener whichever kind it is, on screen or not.
        scope.launch {
          runCatching { workspaceFiles.rename(pending.path, name) }
            .onSuccess { moved -> openDocumentPaths[pending.key] = moved }
            .onFailure {
              if (it is FileNameTakenException) {
                overwriting = TakenTabName(pending.key, pending.path, name)
              } else {
                refusal = it.message ?: "could not rename '${pending.path}' to '$name'"
              }
            }
        }
      },
      onDismiss = { renaming = null },
    )
  }

  overwriting?.let { taken ->
    OverwriteFileDialog(
      name = taken.name,
      onOverwrite = {
        overwriting = null
        scope.launch {
          runCatching { workspaceFiles.overwrite(taken.path, taken.name) }
            .onSuccess { openDocumentPaths[taken.key] = it }
            .onFailure { refusal = it.message ?: "could not overwrite '${taken.name}' with '${taken.path}'" }
        }
      },
      onDismiss = { overwriting = null },
    )
  }

  // The browser's answer, not the tab's: `_doRename` shows every refusal but the user's own cancel
  // as `showErrorMessage('Rename Error', error)` (jupyterlab@7f0e690
  // packages/filebrowser/src/listing.ts), while `context.rename` lets the tab's refusal reach the
  // console alone - which, on a device, is silence.
  refusal?.let { message ->
    AlertDialog(
      onDismissRequest = { refusal = null },
      title = { Text("Rename Error") },
      text = { Text(message) },
      confirmButton = { TextButton(onClick = { refusal = null }) { Text("OK") } },
    )
  }
}

/** The tab being renamed and where it lives now - the live path, so the dialog names it. */
private class PendingTabRename(val key: NavKey, val path: String)

/** The rename the repository refused for a taken name - the key retitled on overwrite, the path tried, the name. */
private class TakenTabName(val key: NavKey, val path: String, val name: String)

/**
 * The strip itself: one tab per main-area entry, in [tabs] order - **opening order**, because a
 * Lumino tab's click changes `currentWidget` and nothing else. The row scrolls when they
 * overflow - the reference's `flex: 0 1 216px` shrunk to a fair share, floored so a tab stays a
 * touch target.
 */
@Composable
private fun MainAreaTabStrip(
  backStack: MutableList<NavKey>,
  openDocuments: OpenDocuments,
  liveDocumentPaths: Map<NavKey, String>,
  liveTabTitles: Map<NavKey, String>,
  tabs: List<NavKey>,
  twoPane: Boolean,
  onCloseTab: (NavKey) -> Unit,
  onRenameTab: (NavKey) -> Unit,
  onSaveTabAs: (NavKey) -> Unit,
  onRevertTab: (NavKey) -> Unit,
  onDeleteTab: (NavKey) -> Unit,
  onSaved: (OpenDocument) -> Unit,
) {
  val chrome = chromePalette
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .background(chrome.tabStripBackground)
      .bottomBorder(chrome.tabStripBorder)
      // The strip is the detail pane's topmost thing - the status bar is the window's, and
      // without its inset the strip draws *behind* it and the system window eats the taps.
      // `windowInsetsPadding` consumes as it pads, so the document's own bar below resolves the
      // same insets to zero rather than double-pad (`EditorTopBar` draws bare in tests).
      .windowInsetsPadding(TopAppBarDefaults.windowInsets)
      .semantics { contentDescription = TAB_STRIP },
  ) {
    // `flex: 0 1 var(--jp-private-horizontal-tab-width)` - a 216px basis that shrinks to a fair
    // share and never grows. Below the floor the row overflows and the scroll takes over.
    val share = if (tabs.isEmpty()) TAB_MAX_WIDTH else maxWidth / tabs.size
    val tabWidth = share.coerceIn(TAB_MIN_WIDTH, TAB_MAX_WIDTH)
    Row(modifier = Modifier.fillMaxWidth()) {
      // The one-pane Up affordance - fixed ahead of the scrollable tabs so it never scrolls
      // away, and present only while something sits beneath to return to. Its label names the
      // destination rather than saying "Back", which is the system bar's own button.
      if (!twoPane && backStack.size > 1) {
        IconButton(onClick = { onCloseTab(backStack.last()) }) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = upDestinationLabel(
              backStack.dropLast(1).lastOrNull(),
              openDocuments,
              liveDocumentPaths,
              liveTabTitles,
            ),
          )
        }
      }
      Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
        tabs.forEach { tab ->
          val document = tab.documentIn(openDocuments)
          MainAreaTab(
            key = tab,
            // Live wins over static, in both its forms: a document's current path (`title.label`
            // follows `context.path`) and a terminal's reported OSC title (`title.label` again -
            // `widget.ts:487`). A rename landed while the document sat in the background still
            // renames its tab the same way. Shared with the Running panel's Open Tabs row - it is
            // the same `title.label`.
            title = tab.runningTabTitle(openDocuments, liveDocumentPaths, liveTabTitles),
            document = document,
            active = backStack.lastOrNull() == tab,
            // `main.title.closable = widgets('main').length > 1` - the lone launcher earns no close.
            closable = tab !is Launcher || backStack.launcherIsClosable(),
            // `docmanager:rename` only exists for what has a path (`document-title` tabs).
            renameable = tab.documentOpening() != null,
            width = tabWidth,
            onActivate = { backStack.activateMainAreaTab(tab) },
            onClose = { onCloseTab(tab) },
            onRename = { onRenameTab(tab) },
            onSaveAs = { onSaveTabAs(tab) },
            onRevert = { onRevertTab(tab) },
            onDelete = { onDeleteTab(tab) },
          )
        }
      }
      // Fixed behind the scrolling tabs as the Up arrow is fixed ahead of them, so neither ever
      // scrolls away. The strip's own height rather than an `IconButton`'s 48dp, which would
      // make the strip taller at two panes than its tabs are.
      val top = backStack.lastOrNull()?.takeIf { it in tabs }
      ActiveTabReload(
        title = top?.runningTabTitle(openDocuments, liveDocumentPaths, liveTabTitles).orEmpty(),
        document = top?.documentIn(openDocuments),
        height = TAB_HEIGHT + ACTIVE_EDGE,
        modifier = Modifier.align(Alignment.CenterVertically),
      )
      ActiveTabSave(
        title = top?.runningTabTitle(openDocuments, liveDocumentPaths, liveTabTitles).orEmpty(),
        document = top?.documentIn(openDocuments),
        height = TAB_HEIGHT + ACTIVE_EDGE,
        modifier = Modifier.align(Alignment.CenterVertically),
        onSaved = onSaved,
      )
    }
  }
}

/**
 * What the one-pane arrow says it returns to - the entry **beneath** the top, named the way the
 * tabs name themselves: the browser is "files", the Spaces home "Spaces", a document its live
 * title, a terminal its OSC title or "Terminal N". Destination-named because bare "Back" is the
 * system bar's own button and TalkBack deserves "back to *what*" (`DocumentDismissAction`'s old
 * labels, moved here with the affordance).
 */
private fun upDestinationLabel(
  beneath: NavKey?,
  openDocuments: OpenDocuments,
  liveDocumentPaths: Map<NavKey, String>,
  liveTabTitles: Map<NavKey, String>,
): String = when (beneath) {
  null -> "Back"
  is Explorer -> "Back to files"
  SpacesHome -> "Back to Spaces"
  else -> "Back to " + (
    beneath.documentIn(openDocuments)?.path?.substringAfterLast('/')
      ?: liveTabTitles[beneath]
      ?: beneath.mainAreaTabTitle(liveDocumentPaths)
    )
}

private const val TAB_STRIP = "Tab strip"

/** `--jp-private-horizontal-tab-width` (`tabs.css:10`) - a tab's width before it must share. */
private val TAB_MAX_WIDTH = 216.dp

/**
 * The fair-share floor: `flex 0 1` shrinks a tab to this and the row scrolls beneath it, which is
 * what the Lumino strip does once its content outgrows the bar.
 */
private val TAB_MIN_WIDTH = 80.dp

/** The strip's own height - between JupyterLab's 26px and VS Code's 35px, at a touchable size. */
internal val TAB_HEIGHT = 32.dp

/** `tab.activeBorderTop`'s 2px, and `--jp-private-horizontal-tab-active-top-border` beside it. */
internal val ACTIVE_EDGE = 2.dp
