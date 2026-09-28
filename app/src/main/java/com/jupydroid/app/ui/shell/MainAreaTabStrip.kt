package com.jupydroid.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.Console
import com.jupydroid.app.Editor
import com.jupydroid.app.Explorer
import com.jupydroid.app.FileEditor
import com.jupydroid.app.Launcher
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.Settings
import com.jupydroid.app.Terminal
import com.jupydroid.app.activateMainAreaTab
import com.jupydroid.app.closeMainAreaTab
import com.jupydroid.app.documentIn
import com.jupydroid.app.domain.port.FileNameTakenException
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.launcherIsClosable
import com.jupydroid.app.liveDocumentPath
import com.jupydroid.app.mainAreaTabTitle
import com.jupydroid.app.openedUnderPath
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.DirtyMarker
import com.jupydroid.app.ui.OverwriteFileDialog
import com.jupydroid.app.ui.RenameFileDialog
import com.jupydroid.app.ui.SaveBeforeCloseDialog
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
 * and the stack is the strip's model (P8d).
 *
 * The reference draws the strip at every width (`docs/PLAN.md` P8e rejected `single-document`
 * mode on that reading), so no `twoPane` branch exists here.
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
  /**
   * Whether a browser is drawn beside the document, which decides one thing: whether the strip
   * leads with the Up affordance. Below two panes the document *is* the window and leaving it
   * goes back to what was underneath - Material's navigation icon on the detail pane's top bar,
   * which this strip is, and the same place IntelliJ keeps its Back/Forward arrows
   * (`EditorTabbedContainer`'s toolbar). Beside a browser there is nothing to go back *to* and
   * closing is the tab's ✕, so the strip draws nothing there.
   */
  twoPane: Boolean = true,
  content: @Composable () -> Unit,
) {
  // The dirty document a tab's close asked about - held here rather than inside a tab so the
  // dialog outlives that tab's recomposition (its own ✕ is what opened the ask).
  var closing by remember { mutableStateOf<PendingTabClose?>(null) }
  // The tab a rename is being asked over - same "outlives the tab" reason as [closing].
  var renaming by remember { mutableStateOf<PendingTabRename?>(null) }
  // The rename the repository refused - `renameFile`'s 409 becomes `shouldOverwrite`, the dialog
  // below, and only then `overwrite` (`packages/docmanager/src/dialogs.ts`).
  var overwriting by remember { mutableStateOf<TakenTabName?>(null) }
  val scope = rememberCoroutineScope()

  // One act empties a main-area slot, whichever control asked: `application:close` is the same
  // command on the tab's ✕ as on the one-pane arrow, so both run this dirty-ask-then-close.
  val closeTab: (NavKey) -> Unit = { key ->
    val document = key.documentIn(openDocuments)
    if (document != null && document.dirty.value) {
      closing = PendingTabClose(key, document, key.tabTitle(openDocuments, openDocumentPaths))
    } else {
      backStack.closeMainAreaTab(key, tabs)
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    MainAreaTabStrip(
      backStack = backStack,
      openDocuments = openDocuments,
      liveDocumentPaths = openDocumentPaths,
      liveTabTitles = liveTabTitles,
      tabs = tabs,
      twoPane = twoPane,
      onCloseTab = closeTab,
      onRenameTab = { key ->
        val path = key.documentIn(openDocuments)?.path ?: key.liveDocumentPath(openDocumentPaths)
        path?.let { renaming = PendingTabRename(key, it) }
      },
    )
    Box(modifier = Modifier.weight(1f)) { content() }
  }

  // `saveBeforeClose` word for word (`docmanager/src/dialogs.ts:155`) - the same dialog the
  // screens' own gate draws, asked on behalf of a tab whose document may not be on screen.
  closing?.let { pending ->
    SaveBeforeCloseDialog(
      name = pending.title,
      // The close is the flush's continuation: a save that cannot be made never runs the remove,
      // so the document stays - `context.save()` rejecting in `widgetmanager.ts:353-365`.
      onSave = {
        closing = null
        pending.document.saveNow { backStack.closeMainAreaTab(pending.key, tabs) }
      },
      onDiscard = {
        closing = null
        pending.document.discardChanges()
        backStack.closeMainAreaTab(pending.key, tabs)
      },
      onCancel = { closing = null },
    )
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
          runCatching { workspaceRepository.rename(pending.path, name) }
            .onSuccess { moved -> openDocumentPaths[pending.key] = moved }
            .onFailure { if (it is FileNameTakenException) overwriting = TakenTabName(pending.key, pending.path, name) }
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
          runCatching { workspaceRepository.overwrite(taken.path, taken.name) }
            .onSuccess { openDocumentPaths[taken.key] = it }
        }
      },
      onDismiss = { overwriting = null },
    )
  }
}

/** What a tab's close is still deciding - the entry, its document, and the label the dialog names. */
private class PendingTabClose(val key: NavKey, val document: OpenDocument, val title: String)

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
      // destination rather than saying "Back", which is the system bar's own button
      // (`evidence/u-flow-sweep/`).
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
            // renames its tab the same way.
            title = document?.path?.substringAfterLast('/')
              ?: liveTabTitles[tab]
              ?: tab.mainAreaTabTitle(liveDocumentPaths),
            document = document,
            active = backStack.lastOrNull() == tab,
            // `main.title.closable = widgets('main').length > 1` - the lone launcher earns no close.
            closable = tab !is Launcher || backStack.launcherIsClosable(),
            // `docmanager:rename` only exists for what has a path (`document-title` tabs).
            renameable = tab.openedUnderPath() != null,
            width = tabWidth,
            onActivate = { backStack.activateMainAreaTab(tab) },
            onClose = { onCloseTab(tab) },
            onRename = { onRenameTab(tab) },
          )
        }
      }
    }
  }
}

/**
 * What the one-pane arrow says it returns to - the entry **beneath** the top, named the way the
 * tabs name themselves: the browser is "files", a document its live title, a terminal its OSC
 * title or "Terminal N". Destination-named because bare "Back" is the system bar's own button
 * and TalkBack deserves "back to *what*" (`DocumentDismissAction`'s old labels, moved here with
 * the affordance).
 */
private fun upDestinationLabel(
  beneath: NavKey?,
  openDocuments: OpenDocuments,
  liveDocumentPaths: Map<NavKey, String>,
  liveTabTitles: Map<NavKey, String>,
): String = when (beneath) {
  null -> "Back"
  Explorer -> "Back to files"
  else -> "Back to " + (
    beneath.documentIn(openDocuments)?.path?.substringAfterLast('/')
      ?: liveTabTitles[beneath]
      ?: beneath.mainAreaTabTitle(liveDocumentPaths)
    )
}

/** The path a tab names - a document's, live where the document registered, else the key's own. */
private fun NavKey.tabTitle(openDocuments: OpenDocuments, liveDocumentPaths: Map<NavKey, String>): String =
  documentIn(openDocuments)?.path?.substringAfterLast('/') ?: mainAreaTabTitle(liveDocumentPaths)

/**
 * One `lm-TabBar-tab`: the kind's icon, the `title.label`, and the close icon that swaps for the
 * `jp-mod-dirty` busy dot while the document owes a write (`ui-components/style/icons.css:268-278`).
 * The dot stays the close *button* - the reference hides the swap behind `:hover`, and a touch
 * screen has none, so here the dot itself is what a dirty tab's close is tapped on.
 *
 * A long press is the tab's context menu - on a document tab that is `docmanager:rename`, the one
 * `document-title`-bound command. The press is the only gesture left once click activates, and
 * `RenameFileDialog` is the dialog it opens there too.
 */
@Composable
private fun MainAreaTab(
  key: NavKey,
  title: String,
  document: OpenDocument?,
  active: Boolean,
  closable: Boolean,
  renameable: Boolean,
  width: Dp,
  onActivate: () -> Unit,
  onClose: () -> Unit,
  onRename: () -> Unit,
) {
  val chrome = chromePalette
  val dirty by document?.dirty?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
  Column(modifier = Modifier.width(width)) {
    // `jp-mod-current::before` / `tab.activeBorderTop` - the brand line the current tab wears.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(ACTIVE_EDGE)
        .background(if (active) chrome.tabActiveBorderTop else Color.Transparent),
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .height(TAB_HEIGHT)
        .background(if (active) chrome.tabActiveBackground else chrome.tabInactiveBackground)
        .trailingBorder(chrome.tabBorder)
        .semantics {
          contentDescription = "Tab: $title"
          selected = active
        }
        .combinedClickable(
          onClick = onActivate,
          // No menu body to put the command on: a touch context menu would be one item deep, so
          // the press opens the dialog itself - as the title row's tap did before the strip took
          // the name over.
          onLongClick = if (renameable) onRename else null,
          onLongClickLabel = if (renameable) "Rename" else null,
        )
        .padding(start = Spacing.sm, end = Spacing.xs),
    ) {
      Icon(
        imageVector = tabIcon(key),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(TAB_ICON),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // Cleared, not merged: the row already announces itself as "Tab: $title", and a bare
        // text node here is the document's title a second time - which turns every
        // `onNodeWithText(title)` in the navigation tests into a two-node match.
        modifier = Modifier.padding(start = Spacing.xs).weight(1f).clearAndSetSemantics {},
      )
      if (closable) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(TAB_CLOSE_TOUCH)
            .semantics { contentDescription = "Close tab: $title" }
            .clickable(onClick = onClose),
        ) {
          if (dirty) {
            DirtyMarker()
          } else {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(TAB_ICON),
            )
          }
        }
      }
    }
  }
}

/** `title.icon` - the kind's own glyph (its `filetype` SVG), in the Material equivalents the app draws. */
private fun tabIcon(key: NavKey): ImageVector = when (key) {
  is Editor -> Icons.AutoMirrored.Filled.MenuBook
  is FileEditor -> Icons.AutoMirrored.Filled.InsertDriveFile
  is Terminal -> Icons.Filled.Terminal
  is Console -> Icons.Filled.Code
  Launcher -> Icons.AutoMirrored.Filled.OpenInNew
  Settings -> Icons.Filled.Settings
  else -> Icons.AutoMirrored.Filled.InsertDriveFile
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
private val TAB_HEIGHT = 32.dp

/** `tab.activeBorderTop`'s 2px, and `--jp-private-horizontal-tab-active-top-border` beside it. */
private val ACTIVE_EDGE = 2.dp

/**
 * The `filetype` glyph's slot - a step under the reference's `jp-icon3` 16px so the ellipsized
 * label keeps the extra room where a tab is squeezed to the 80dp floor.
 */
private val TAB_ICON = 14.dp

/** A ✕-sized slot - JupyterLab's close icon is ~16px; the tap target is the slot around it. */
private val TAB_CLOSE_TOUCH = 24.dp
