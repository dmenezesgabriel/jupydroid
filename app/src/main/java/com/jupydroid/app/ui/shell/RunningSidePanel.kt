package com.jupydroid.app.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.RunningTab
import com.jupydroid.app.SidePanel
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.extension.api.DocumentContribution
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * `jp-running-sessions` - **"Sessions and Tabs"**, `app.shell.add(running, 'left', {rank: 200})`
 * (`packages/running-extension/src/index.ts:98-122`): the open documents beside the kernel
 * sessions and terminals the server still holds, which is the panel that makes "my kernel died"
 * something a user can *see* rather than infer.
 *
 * Three collapsible sections in the reference's registration order - Open Tabs, Kernel Sessions,
 * Terminals - each a `Section` (`running/src/index.tsx`): its name is the accordion header, its
 * toolbar the bulk action drawn only while the section is open, and its rows are tap-to-open with
 * a trailing ✕ that ends the thing it names. The panel's own toolbar carries `refreshIcon`
 * ("Refresh List"), and the list polls while it is composed - the reference's
 * `Poll{interval: 10s, standby: 'when-hidden'}`, where "hidden" is simply not being on the rail.
 *
 * What is *not* here, on purpose: the reference's list/tree switch and "Shut Down Unused" (they
 * group kernels by spec over `GET api/kernels`, which is not in the contract),
 * per-row confirmations (the reference's `Item.shutdown` asks nothing), and synthesising a console
 * from a session row (the single `consoleSession` holder cannot - a console row reveals its tab
 * where one is open, the way `console:open` does).
 *
 * @param openTabs the main area's entries in opening order - `labShell.widgets('main')`.
 * @param onActivateTab `OpenTab.open()` - `labShell.activateById`.
 * @param onCloseTab `OpenTab.shutdown()` - `widget.close()`, through the caller's dirty gate.
 * @param saveAllEnabled whether any open tab's document owes a write - VS Code's
 *   `DirtyWorkingCopiesContext`.
 * @param onSaveAllTabs `workbench.action.files.saveAll`, which VS Code's Open Editors view draws
 *   beside Close All (vscode@3f3380c `contrib/files/browser/views/openEditorsView.ts`); jupyterlab's
 *   Open Tabs has no save. Unconfirmed, as there: a save loses nothing.
 * @param onOpenSession `running:kernel-open-session` - `docmanager:open`, or `console:open` for a
 *   console row (the caller decides, for the reason the class doc gives).
 * @param onOpenTerminal `terminal:open` - reattaches to the shell the server still holds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningSidePanel(
  kernelSessions: KernelSessions,
  terminalGateway: TerminalGateway,
  openTabs: List<RunningTab>,
  onActivateTab: (NavKey) -> Unit,
  onCloseTab: (NavKey) -> Unit,
  saveAllEnabled: Boolean,
  onSaveAllTabs: () -> Unit,
  onOpenSession: (KernelSessionModel) -> Unit,
  onOpenTerminal: (TerminalSession) -> Unit,
  modifier: Modifier = Modifier,
  /** What an extension's tab is drawn with, for its row's glyph - see [tabIcon]. */
  documentContributions: List<DocumentContribution> = emptyList(),
  viewModel: RunningPanelViewModel = viewModel { RunningPanelViewModel(kernelSessions, terminalGateway) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // `Poll{interval: 10s, standby: 'when-hidden'}` - composed *is* visible here, so cancelling the
  // effect when the rail switches away is the whole of the standby half.
  LaunchedEffect(viewModel) {
    while (true) {
      viewModel.refresh()
      delay(POLL_INTERVAL)
    }
  }
  // Section folds are the reference's accordion state, which it persists in a StateDB - saved
  // here so a rotation keeps the fold rather than re-opening everything.
  var collapsed by rememberSaveable { mutableStateOf(listOf<String>()) }
  var pendingBulk by remember { mutableStateOf<PendingBulk?>(null) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = chromePalette.sideBarBackground,
    topBar = {
      TopAppBar(
        colors = sideBarTopAppBarColors(),
        title = { Text(SidePanel.RUNNING.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
          IconButton(
            onClick = viewModel::refresh,
            modifier = Modifier.semantics { contentDescription = "Refresh list" },
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())) {
      RunningSection(
        title = OPEN_TABS_SECTION,
        collapsed = collapsed,
        onToggle = { collapsed = collapsed.toggled(OPEN_TABS_SECTION) },
        bulkLabel = "Close All",
        bulkEnabled = openTabs.isNotEmpty(),
        onBulk = {
          pendingBulk = PendingBulk("Close All?", "Are you sure you want to close all open tabs?", "Close All") {
            openTabs.forEach { onCloseTab(it.key) }
          }
        },
        // VS Code's order: Save All (20) ahead of Close All (30) in Open Editors' title.
        leadingAction = {
          SectionActionButton("Save All", OPEN_TABS_SECTION, enabled = saveAllEnabled, onClick = onSaveAllTabs)
        },
      ) {
        openTabs.forEach { tab ->
          RunningRow(
            icon = tabIcon(tab.key, documentContributions),
            label = tab.title,
            detail = null,
            openDescription = "Open tab: ${tab.title}",
            shutdownDescription = "Close: ${tab.title}",
            onOpen = { onActivateTab(tab.key) },
            onShutdown = { onCloseTab(tab.key) },
          )
        }
      }
      RunningSection(
        title = KERNEL_SESSIONS_SECTION,
        collapsed = collapsed,
        onToggle = { collapsed = collapsed.toggled(KERNEL_SESSIONS_SECTION) },
        bulkLabel = "Shut Down All",
        bulkEnabled = state.sessions.isNotEmpty(),
        onBulk = {
          pendingBulk = PendingBulk("Shut Down All?", kernelShutdownAllText(state.sessions.size), "Shut Down All") {
            viewModel.shutdownAllSessions()
          }
        },
      ) {
        state.sessions.forEach { session ->
          RunningRow(
            icon = sessionIcon(session),
            label = session.name,
            // List-mode's `(kernelIdPrefix)` detail - `kernels.tsx` renders the kernel id's
            // first segment beside the session's name.
            detail = session.kernelId?.let { "(${it.substringBefore('-')})" },
            openDescription = "Open kernel session: ${session.name}",
            shutdownDescription = "Shut Down Kernel: ${session.name}",
            onOpen = { onOpenSession(session) },
            onShutdown = { viewModel.shutdownSession(session) },
          )
        }
      }
      RunningSection(
        title = TERMINALS_SECTION,
        collapsed = collapsed,
        onToggle = { collapsed = collapsed.toggled(TERMINALS_SECTION) },
        bulkLabel = "Shut Down All",
        bulkEnabled = state.terminals.isNotEmpty(),
        onBulk = {
          pendingBulk = PendingBulk(
            "Shut Down All?",
            "Are you sure you want to permanently shut down all running terminals?",
            "Shut Down All",
          ) { viewModel.shutdownAllTerminals() }
        },
      ) {
        state.terminals.forEach { terminal ->
          RunningRow(
            icon = Icons.Filled.Terminal,
            label = "terminals/${terminal.name}",
            detail = null,
            openDescription = "Open terminal: terminals/${terminal.name}",
            shutdownDescription = "Shut Down: terminals/${terminal.name}",
            onOpen = { onOpenTerminal(terminal) },
            onShutdown = { viewModel.closeTerminal(terminal) },
          )
        }
      }
    }
  }

  pendingBulk?.let { bulk ->
    BulkConfirmationDialog(
      pending = bulk,
      onConfirm = {
        pendingBulk = null
        bulk.run()
      },
      onDismiss = { pendingBulk = null },
    )
  }
}

/**
 * One `Section` - the accordion header, then while open its toolbar (the bulk action, disabled on
 * an empty section the way `jp-mod-disabled` dims it, after any [leadingAction]) and its rows.
 */
@Composable
private fun RunningSection(
  title: String,
  collapsed: List<String>,
  onToggle: () -> Unit,
  bulkLabel: String,
  bulkEnabled: Boolean,
  onBulk: () -> Unit,
  leadingAction: @Composable () -> Unit = {},
  rows: @Composable () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(Spacing.sm),
    ) {
      Icon(
        imageVector = if (title in
          collapsed
        ) {
          Icons.AutoMirrored.Filled.KeyboardArrowRight
        } else {
          Icons.Filled.KeyboardArrowDown
        },
        contentDescription = null,
        modifier = Modifier.size(SECTION_CARET),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = Spacing.xs),
      )
    }
    if (title !in collapsed) {
      Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.weight(1f))
        leadingAction()
        SectionActionButton(bulkLabel, title, enabled = bulkEnabled, onClick = onBulk)
      }
      rows()
    }
  }
}

/** A section toolbar's button, named with its section so "Close All" in two sections stays two acts. */
@Composable
private fun SectionActionButton(label: String, section: String, enabled: Boolean, onClick: () -> Unit) {
  TextButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.semantics { contentDescription = "$label: $section" },
  ) {
    Text(label)
  }
}

/**
 * One `Item` - the kind's icon, its label (and the `(kernelId)` detail a session row carries), a
 * tap that opens it, and the trailing `closeIcon` that ends it without asking
 * (`running/src/index.tsx`'s `Item.shutdown` - direct, never confirmed).
 */
@Composable
private fun RunningRow(
  icon: ImageVector,
  label: String,
  detail: String?,
  openDescription: String,
  shutdownDescription: String,
  onOpen: () -> Unit,
  onShutdown: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    // A clickable row names its act, not just its label - "running-panel-kernel.ipynb" alone is also the
    // tab's text and the browser's, so the section-qualified name is what a screen reader (and a
    // flow) can address without ambiguity.
    modifier = Modifier
      .fillMaxWidth()
      .semantics { contentDescription = openDescription }
      .clickable(onClick = onOpen)
      .padding(Spacing.sm),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(ROW_ICON),
    )
    // The weighted wrapper is what keeps the close button at one x: an unweighted label is measured first
    // with all the width left, so a long name would push the button past the edge. The detail is measured
    // before the label, so it keeps its width and the label is the part that is cut
    // (RunningSidePanelFontScaleTest).
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false).padding(start = Spacing.sm),
      )
      detail?.let {
        Text(
          text = it,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          modifier = Modifier.padding(start = Spacing.xs),
        )
      }
    }
    IconButton(
      onClick = onShutdown,
      modifier = Modifier.semantics { contentDescription = shutdownDescription },
    ) {
      Icon(
        imageVector = Icons.Filled.Close,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * The section toolbar's confirm - `showDialog({title: '<label>?', buttons: [Cancel, warn]})`
 * (`running/src/index.tsx:591-612`). The warn button keeps the section's own label, so the answer
 * reads exactly like the control that asked.
 */
@Composable
private fun BulkConfirmationDialog(pending: PendingBulk, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(pending.title) },
    text = { Text(pending.body) },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        modifier = Modifier.semantics { contentDescription = "Confirm ${pending.confirmLabel}" },
      ) {
        Text(pending.confirmLabel, color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/** `trans._n` over the row count - the reference counts its kernels; this section counts sessions. */
private fun kernelShutdownAllText(count: Int): String = if (count == 1) {
  "Are you sure you want to permanently shut down the running kernel session?"
} else {
  "Are you sure you want to permanently shut down the $count running kernel sessions?"
}

/** `consoleIcon`/`notebookIcon`/`jupyterIcon` by session type, in the app's own glyph set. */
private fun sessionIcon(session: KernelSessionModel): ImageVector = when (session.type) {
  "notebook" -> Icons.AutoMirrored.Filled.MenuBook
  "console" -> Icons.Filled.Code
  else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/** The fold a header tap flips - [title] is the section's own name, so the set is the fold. */
private fun List<String>.toggled(title: String): List<String> = if (title in this) this - title else this + title

/** A bulk ask waiting on its confirm - the dialog's three strings and the act it gates. */
private class PendingBulk(val title: String, val body: String, val confirmLabel: String, val run: () -> Unit)

private const val OPEN_TABS_SECTION = "Open Tabs"

/**
 * The reference's section name is `Kernels` (`kernels.tsx:272`) because it lists *kernels* grouped
 * by spec - which needs `GET api/kernels`, not in the contract. These rows are sessions, and
 * so the section is named after sessions.
 */
private const val KERNEL_SESSIONS_SECTION = "Kernel Sessions"

private const val TERMINALS_SECTION = "Terminals"

/** `Poll`'s own cadence - `interval: 10_000` on the session manager's `refreshRunning`. */
private val POLL_INTERVAL = 10.seconds

/** `lm-AccordionPanel-titleCollapser`'s own slot - `height: 16px` (`ui-components/style/sidepanel.css:93`). */
private val SECTION_CARET = 16.dp

/** `jp-RunningSessions-icon`'s own slot - `width: 16px; height: 16px` (`running/style/base.css:86-89`). */
private val ROW_ICON = 16.dp
