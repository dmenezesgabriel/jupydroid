package com.jupydroid.app.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.parentDirectory
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.RecentDocumentsRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.DeleteFileDialog
import com.jupydroid.app.ui.OverwriteFileDialog
import com.jupydroid.app.ui.RenameFileDialog
import com.jupydroid.app.ui.relativeTime
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * The workspace, one directory at a time.
 *
 * JupyterLab's file browser is a single pane with a breadcrumb above it, not an expanding tree
 * (`packages/filebrowser`), and that is the shape a phone wants for the same reason it suits a
 * narrow sidebar: indentation would spend the width the names need.
 *
 * ```
 * ExplorerScreen(workspaceRepository, onOpenFile = { backStack.openNotebook(it.path) })
 * ```

 * @param onBack how to leave, or null when there is nowhere to leave to. Recents pushes the
 *   explorer today and the explorer becomes the root once Recents is a section of it; the arrow
 *   follows that rather than being drawn unconditionally.
 */
@Composable
fun ExplorerScreen(
  workspaceRepository: WorkspaceRepository,
  notebookRepository: NotebookRepository,
  recentDocuments: RecentDocumentsRepository,
  onOpenFile: (WorkspaceEntry) -> Unit,
  onOpenRecent: (String) -> Unit = {},
  /**
   * The document open in the pane beside this one, so its row can say so - or null when the
   * browser has the window to itself.
   *
   * The reference does the same from `_onActivateRequested`: the browser selects the row for the
   * document that was just activated, and **only when it is in the directory being browsed**
   * (`listing.ts:2761-2770`, which returns early when `dirname !== this._model.path`). Comparing
   * whole paths is that same rule said once, since every entry carries its full path.
   */
  openDocumentPath: String? = null,
  onBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  viewModel: ExplorerViewModel =
    viewModel { ExplorerViewModel(workspaceRepository, notebookRepository, recentDocuments) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // The entry whose name is being typed, held here rather than in the row: a `DropdownMenu` item
  // that opened its own dialog would take it down with itself the moment it dismissed.
  var renaming by remember { mutableStateOf<WorkspaceEntry?>(null) }
  var deleting by remember { mutableStateOf<WorkspaceEntry?>(null) }
  // Read once per listing rather than per row, so every row on screen is aged against the same
  // instant - otherwise two files written together can read as different ages.
  val now = remember(state.path, state.entries) { Instant.now() }
  val listing = rememberLazyListState()

  KeepTheListingFresh(viewModel::refresh)

  // A created row can land anywhere, because the listing is ordered by name - so it is brought
  // into view rather than left below the fold, which is audit A3 on this surface.
  BringCreatedItemIntoView(
    justCreated = state.justCreated,
    entries = state.entries,
    itemsAboveTheListing = itemsAboveTheListing(state.recents.size),
    listing = listing,
  )

  // Back climbs the workspace before it leaves the screen, which is `filebrowser:go-up` - the
  // reference binds Backspace to the same thing. Enabled only when there is a parent, so at the
  // root back still means "leave", and nothing swallows it.
  BackHandler(enabled = state.canGoUp) { viewModel.goTo(state.path.parentDirectory()) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      FilesBar(
        onBack = onBack,
        onCreate = viewModel::create,
      )
    },
  ) { padding ->
    Column(modifier = Modifier.padding(padding)) {
      Breadcrumb(crumbs = state.crumbs, onGoTo = viewModel::goTo)
      HorizontalDivider()
      state.error?.let { ExplorerError(it) }
      // One LazyColumn for both sections, through the item/items DSL: a second scrollable nested
      // in the same direction is what Compose forbids outright, and the guidance is to consolidate
      // rather than to give the inner one a fixed height.
      LazyColumn(state = listing, modifier = Modifier.fillMaxSize()) {
        recentsSection(
          recents = state.recents,
          onOpen = { document -> viewModel.openRecent(document, onOpenRecent) },
          onClear = viewModel::clearRecents,
        )
        // Keyed by path, which is the identity of a workspace entry and a String, so it survives
        // the Bundle round trip Compose needs for state restoration. contentType by kind, so a
        // folder row and a file row reuse separate composition pools rather than each other's.
        items(state.entries, key = { it.path }, contentType = { it.kind }) { entry ->
          EntryRow(
            entry = entry,
            now = now,
            isOpen = entry.path == openDocumentPath,
            onOpen = { viewModel.open(entry, onOpenFile) },
            onRename = { renaming = entry },
            onDelete = { deleting = entry },
          )
        }
      }
    }
  }

  renaming?.let { entry ->
    RenameFileDialog(
      currentTitle = entry.name,
      heading = if (entry.kind == WorkspaceEntryKind.DIRECTORY) "Rename folder" else "Rename file",
      onConfirm = { name ->
        renaming = null
        viewModel.rename(entry, name)
      },
      onDismiss = { renaming = null },
    )
  }

  // Asked before anything goes, and the question names what would go: `DirListing.delete` puts
  // this in front of `_delete`, never after it.
  deleting?.let { entry ->
    DeleteFileDialog(
      name = entry.name,
      onDelete = {
        deleting = null
        viewModel.delete(entry)
      },
      onDismiss = { deleting = null },
    )
  }

  // Only a name already taken gets here: the repository refuses everything else outright, exactly
  // as `renameFile` rethrows anything that is not a 409.
  state.nameTaken?.let { name ->
    OverwriteFileDialog(
      name = name,
      onOverwrite = { viewModel.answerOverwrite(accepted = true) },
      onDismiss = { viewModel.answerOverwrite(accepted = false) },
    )
  }
}

/**
 * What the screen is, above the breadcrumb that says where in it you are.
 *
 * Two rows rather than one: the title is fixed and the crumbs scroll, and putting them together
 * would make the title scroll away with them - on a phone that is most of the bar's width gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesBar(onBack: (() -> Unit)?, onCreate: (NewItem) -> Unit) {
  TopAppBar(
    title = { Text("Files") },
    navigationIcon = {
      onBack?.let {
        IconButton(onClick = it) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to recents")
        }
      }
    },
    actions = { NewMenu(onCreate = onCreate) },
  )
}

/**
 * The reference's New drop-down, on a phone: a "+" that opens Notebook / Python file / Folder.
 *
 * The reference offers the same three from the filebrowser toolbar's New widget and then renames
 * the result in place (`browser.ts:_createNew` -> `rename()`). Ours lands the creation in the
 * browsed directory under the server's own `untitled{ext}` name and leaves the rename to the row's
 * own menu, which X2f gave it - one way to name a thing rather than a second one that only new
 * files can reach.
 */
@Composable
private fun NewMenu(onCreate: (NewItem) -> Unit) {
  var open by remember { mutableStateOf(false) }
  IconButton(onClick = { open = true }) {
    Icon(Icons.Filled.Add, contentDescription = "New")
  }
  DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
    NEW_MENU_ITEMS.forEach { (item, label) ->
      DropdownMenuItem(text = { Text(label) }, onClick = {
        open = false
        onCreate(item)
      })
    }
  }
}

/**
 * The ancestry, scrolling sideways rather than eliding.
 *
 * Horizontal inside a vertical list is the one nesting Compose allows without a fixed size - the
 * guidance forbids only *same-direction* nesting - and it is what keeps the folder you are **in**
 * on screen. Truncating the tail would hide precisely the crumb that matters.
 */
@Composable
private fun Breadcrumb(crumbs: List<Pair<String, String>>, onGoTo: (String) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    crumbs.forEachIndexed { at, (path, label) ->
      if (at > 0) {
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          modifier = Modifier.size(CRUMB_SEPARATOR_SIZE),
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clickable { onGoTo(path) }.padding(horizontal = Spacing.md, vertical = Spacing.lg),
      )
    }
  }
}

/**
 * Re-lists while the screen is in front of someone, and stops when it is not.
 *
 * **The workspace is shared, so the listing goes stale without this app doing anything.** It is
 * the kernel's `HOME` since X1a, so a cell calling `open(..., 'w')` writes into the directory
 * being browsed, and an editor writes the notebook a row names. The reference answers exactly this
 * with a poll: `FileBrowserModel` runs `cd('.')` on a `Poll` at `DEFAULT_REFRESH_INTERVAL = 10000`
 * with `standby: 'when-hidden'` (`packages/filebrowser/src/model.ts:26,97-106`). Its own
 * `fileChanged` signal is not enough and never was - it only reports changes the *client* made,
 * which is why the poll exists beside it. Our equivalent of that signal is `relistAfter`, which
 * already re-lists after this app's own create, rename and delete.
 *
 * **`standby: 'when-hidden'` is `RESUMED` here**, and that is the mobile half of the adaptation: a
 * phone that kept polling in the background would spend a wakeup every ten seconds on a listing
 * nobody is looking at. `repeatOnLifecycle` cancels the loop at `ON_PAUSE` and starts a fresh one
 * at `ON_RESUME`, so coming back also refreshes immediately - which is the case `a6` and
 * `step2-import` needed and the reason this replaced a resume-only effect rather than joining it.
 *
 * **Not adapted: the reference's `backoff: true, max: 300000`.** That backs off a failing *HTTP*
 * poll against a server that may be down. Ours is a `listFiles` on this app's own storage; the one
 * way it fails is the directory being removed underneath, which `goTo` already reports while
 * leaving the view where it was, and which a later tick recovers from on its own.
 */
@Composable
private fun KeepTheListingFresh(refresh: () -> Unit) {
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner, refresh) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      while (true) {
        refresh()
        delay(EXPLORER_REFRESH_INTERVAL)
      }
    }
  }
}

/**
 * Scrolls a newly created row into view, once.
 *
 * The index has to count what sits *above* the listing, because the Recents section's items are in
 * the same `LazyColumn` - see [itemsAboveTheListing]. Off-by-one here would scroll to a neighbour,
 * which looks like the bug it is fixing.
 */
@Composable
private fun BringCreatedItemIntoView(
  justCreated: String?,
  entries: List<WorkspaceEntry>,
  itemsAboveTheListing: Int,
  listing: LazyListState,
) {
  LaunchedEffect(justCreated, entries) {
    val at = entries.indexOfFirst { it.path == justCreated }
    if (justCreated == null || at < 0) return@LaunchedEffect
    listing.animateScrollToItem(itemsAboveTheListing + at)
  }
}

/**
 * One entry: an icon that says what it is, and its name.
 *
 * The icon carries the kind for a scanning eye and its `contentDescription` carries it for a
 * screen reader, which is the same split the reference makes between its file-type icons and the
 * `data-isdir` its own menu selectors read.
 */
@Composable
private fun EntryRow(
  entry: WorkspaceEntry,
  now: Instant,
  isOpen: Boolean,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  val directory = entry.kind == WorkspaceEntryKind.DIRECTORY
  var menuOpen by remember { mutableStateOf(false) }
  // The same items the long press opens, in the local context menu TalkBack reads. A long press is
  // not reachable by a screen reader at all, which U5e already paid to learn on the cell actions.
  val actions = remember(entry.path, onRename, onDelete) {
    listOf(
      CustomAccessibilityAction("Rename") { onRename(); true },
      CustomAccessibilityAction("Delete") { onDelete(); true },
    )
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      // One accessible item rather than an icon and two Texts, so a row reads as
      // "Nested.ipynb, just now, selected" instead of making TalkBack walk three nodes - and so
      // `selected` is found on the node the name is on.
      .semantics(mergeDescendants = true) { selected = isOpen; customActions = actions }
      .background(if (isOpen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
      // Long press for the row's own actions, which is the touch equivalent of the reference's
      // right-click context menu (`filebrowser-extension`) and of Winlator's per-row menu - and is
      // already what a notebook row on Home opens.
      .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
      .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      // The reference gives every row a file-type icon from its icon registry; these are the
      // Material equivalents. AutoMirrored because the page-corner fold flips in a right-to-left
      // locale, the same way the breadcrumb's chevron does.
      imageVector = if (directory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
      contentDescription = if (directory) "Folder" else "File",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // The name and its time stack rather than sitting in two columns: `DirListing` gives
    // `last_modified` a column of its own, and a 320dp phone has no second column to give it -
    // the reference itself narrows the format as the space shrinks (`listing.ts:1061-1073`), and
    // this is the end of that scale.
    Column(modifier = Modifier.padding(start = Spacing.lg)) {
      Text(
        text = entry.name,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = relativeTime(entry.updatedAt, now),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
        text = { Text("Rename") },
        onClick = {
          menuOpen = false
          onRename()
        },
      )
      DropdownMenuItem(
        text = { Text("Delete") },
        onClick = {
          menuOpen = false
          onDelete()
        },
      )
    }
  }
}

/** What went wrong reaching a directory - a shared workspace can lose one under the user. */
@Composable
private fun ExplorerError(message: String) {
  Text(
    text = message,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
  )
}

/**
 * The chevron between two breadcrumbs. A size and not a gap, and named as one: `SEPARATOR` said
 * what it was beside rather than what it is, and `Modifier.size(SEPARATOR)` read as a spacer.
 */
private val CRUMB_SEPARATOR_SIZE = 16.dp


/**
 * How often the listing is re-read: `DEFAULT_REFRESH_INTERVAL`, which the reference sets to 10 s
 * (`packages/filebrowser/src/model.ts:26`).
 */
internal val EXPLORER_REFRESH_INTERVAL: Duration = 10.seconds

/** The three the reference's New widget offers, in its order. */
private val NEW_MENU_ITEMS = listOf(
  NewItem.NOTEBOOK to "New notebook",
  NewItem.PYTHON_FILE to "New Python file",
  NewItem.FOLDER to "New folder",
)
