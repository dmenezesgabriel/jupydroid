package com.jupydroid.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.NOTEBOOK_SUFFIX
import com.jupydroid.app.domain.model.incrementedFileName
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.SharedDocumentReader
import com.jupydroid.app.domain.port.SharedDocumentTooLarge
import com.jupydroid.app.domain.port.SpaceServers
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.import.SharedNotebook
import com.jupydroid.extension.api.DocumentContribution
import kotlinx.coroutines.launch

/**
 * `docmanager:open` on a path: the editor kind the suffix names, revealed rather than pushed a
 * second time when the document is already open.
 *
 * A `.ipynb` goes to the notebook editor and anything else to the plain one, which is the
 * reference's own split: the notebook factory claims its extension and the file editor is the
 * default for the rest (`packages/fileeditor`). There is no `defaultViewers` knob on top
 * (`docmanager-extension/src/index.tsx:297-315` lets a setting point a file type at another
 * factory) because every type here has exactly one factory - an override would have nothing to
 * point at.
 *
 * The reveal matches a tab by where its document lives **now**: `openOrReveal` reaches a widget
 * through `_contextsForPath`, which filters on a context's current path (`manager.ts:481-503`),
 * and a key keeps only where it opened. So a renamed tab answers to its new name and never to the
 * old one, and a file recreated at the old name opens a tab of its own, under the lowest
 * [DocumentOpening.ordinal] no open tab on that path holds (`RecreatedPathOpeningTest`). The tab
 * revealed keeps its own kind, so a notebook renamed to `.py` still reveals the notebook editor.
 *
 * A suffix an extension claims goes to that extension's tab first ([claiming]), as a factory
 * `defaultFor` a file type outranks the file editor's default. Its key is the path itself, so
 * [openInMainArea] reveals it where the tab is already open.
 */
internal fun MutableList<NavKey>.openDocument(
  path: String,
  openDocuments: OpenDocuments,
  documentContributions: List<DocumentContribution> = emptyList(),
) {
  val contributed = documentContributions.claiming(path)
  if (contributed != null) return openInMainArea(ContributedDocument(contributed.id, path))
  val open = firstOrNull { it.livePathIn(openDocuments) == path }
  if (open != null) return openInMainArea(open)
  val opening = unusedOpeningOf(path)
  if (path.endsWith(NOTEBOOK_SUFFIX, ignoreCase = true)) {
    openNotebook(path, opening)
  } else {
    openFile(path, opening)
  }
}

/** The lowest ordinal no tab on this stack opened [path] under - a new tab's [DocumentOpening]. */
private fun List<NavKey>.unusedOpeningOf(path: String): Int =
  generateSequence(0) { it + 1 }.first { n -> none { it.documentOpening() == DocumentOpening(path, n) } }

/**
 * `running:kernel-open-session` - `docmanager:open` for a document row, `console:open` for a
 * console one (`running-extension/src/kernels.tsx:260-264`). The console arm can only *reveal*:
 * `entry<Console>` draws from the single `consoleSession` holder, so a console row with no tab
 * already open cannot be synthesised into one - which is `openConsole`'s own constraint.
 *
 * ```
 * backStack.openRunningSession(row, orderedTabs, openDocument)
 * ```
 */
internal fun MutableList<NavKey>.openRunningSession(
  session: KernelSessionModel,
  openTabs: List<NavKey>,
  openDocument: (String) -> Unit,
) {
  if (session.type != "console") return openDocument(session.path)
  openTabs.filterIsInstance<Console>().firstOrNull { it.sessionPath == session.path }?.let(::activateMainAreaTab)
}

/**
 * Opening a document, wherever it was opened from: the right editor, a line in Recents, and a
 * checkpoint when it has none.
 *
 * **One opener rather than one per screen.** Home knew which of its rows was a notebook and the
 * explorer worked it out from the suffix, so the same rule lived in two places and only one of
 * them could record a recent. The reference has a single `docmanager:open` for the same reason -
 * the path decides the factory, and recents are recorded once, from the widget manager's
 * `activate-request` hook (`packages/docmanager/src/widgetmanager.ts:268-285`).
 *
 * The kind split and the reveal live in [openDocument]; what stays here is the record - the
 * *asked* path goes to Recents even when the open resolved to a document that has since been
 * renamed, because the asked path is the one the file lives at now.
 *
 * Recorded on [AppContainer.applicationScope], not on a screen's: the write must finish even if
 * the tap that caused it is the last thing that screen does.
 */
@Composable
internal fun rememberDocumentOpener(
  backStack: MutableList<NavKey>,
  appContainer: AppContainer,
  openDocuments: OpenDocuments,
): (String) -> Unit = remember(backStack, appContainer, openDocuments) {
  { path ->
    backStack.openDocument(path, openDocuments, appContainer.documentContributions)
    val ports = appContainer.portsOf(backStack.enteredSpaceId())
    appContainer.applicationScope.launch { ports.recentDocuments.recordOpening(path) }
    appContainer.applicationScope.launch { ports.documentCheckpoints.checkpointIfNone(path) }
  }
}

/**
 * `terminal:create-new`, adapted: the reference reaches it from the Launcher tab or the File
 * menu, and this app has neither. So this is the
 * explorer's own provisional reach point - a second icon beside the New notebook/file/folder
 * menu rather than a fourth item inside it, since that menu's own KDoc already states parity
 * with the reference's New widget (exactly three, none of them a terminal).
 *
 * A separate `rememberCoroutineScope` rather than [AppContainer.applicationScope]: opening a
 * terminal only matters while this screen exists to navigate away from, which is
 * `architecture.md`'s own line for what belongs on which scope - unlike recording a recent or
 * flushing an edit, nothing here has to outlive the composition that asked for it.
 */
@Composable
internal fun rememberNewTerminal(backStack: MutableList<NavKey>, terminalGateway: TerminalGateway): (String) -> Unit {
  val scope = rememberCoroutineScope()
  return remember(backStack, terminalGateway, scope) {
    { cwd -> scope.launch { backStack.openNewTerminal(terminalGateway, cwd) } }
  }
}

/**
 * `terminal:create-new`'s mint and reveal, as a suspend on the stack so the refused half is
 * testable without Compose. **A refused `open` is no terminal opened, not a crash** - the same
 * `runCatching` the VIEW intent gives its copy ([ImportedNotebookEffect]), since this reach has
 * no error surface the way the launcher card's `LauncherViewModel.error` is.
 */
internal suspend fun MutableList<NavKey>.openNewTerminal(terminalGateway: TerminalGateway, cwd: String) {
  runCatching { terminalGateway.open(cwd.ifEmpty { null }) }
    .onSuccess { openTerminal(it.name) }
}

/**
 * The VIEW intent, which used to be Home's because Home was the first screen. It is an app-level
 * act rather than a screen's - nothing about it belongs to the browser. Keyed on the request so a
 * second import in the same process is not swallowed.
 *
 * **Opened, not merely listed.** Home added a row and left it there, which was defensible when
 * Home was a list of notebooks; an ACTION_VIEW intent asks to *view* the document, and opening
 * it is also what makes the result deterministic - a row only appears once the browser has
 * listed again, and the copy races that listing on a cold start. Opening also records it as a
 * recent, so it is on the browser's first section when the editor is left.
 *
 * **Inside a workspace.** A document belongs to one, so the act itself is [importNotebook]: it lands
 * in the Space already entered, or from the Spaces home runs and enters base. The browser is then the
 * list pane the document pairs with, rather than a bare home underneath it.
 *
 * ```
 * ImportedNotebookEffect(sharedNotebook, appContainer, backStack, openDocument, onImportConsumed, importReports)
 * ```
 */
@Composable
internal fun ImportedNotebookEffect(
  sharedNotebook: SharedNotebook?,
  appContainer: AppContainer,
  backStack: MutableList<NavKey>,
  openDocument: (String) -> Unit,
  onImportConsumed: () -> Unit,
  importReports: SnackbarHostState,
) {
  // Launched rather than awaited: a snackbar suspends until it is dismissed, and the import is
  // consumed the moment it is settled, not seconds later.
  val reportScope = rememberCoroutineScope()
  LaunchedEffect(sharedNotebook) {
    sharedNotebook?.let { shared ->
      backStack.importNotebook(
        shared,
        appContainer::portsOf,
        appContainer.spaceServers,
        appContainer.sharedDocumentReader,
        { message -> reportScope.launch { importReports.showSnackbar(message) } },
      )
        ?.let(openDocument)
      onImportConsumed()
    }
  }
}

/**
 * The VIEW intent's act on the stack, testable without Compose: copy the shared notebook into the
 * Space it lands in, and enter that Space. **Inside a workspace it stays in the Space entered**,
 * since the workspace never switches Space in place. **From the home it runs base first**, because
 * a Space is only entered running (the home's Open is gated on it). A refused read, run or write
 * opens nothing and returns null, the `runCatching` [openNewTerminal] gives a refused mint.
 *
 * The document is read before anything runs, so a file that cannot be read starts no Space.
 *
 * ```
 * backStack.importNotebook(shared, appContainer::portsOf, spaceServers, sharedDocumentReader)
 * ```
 */
internal suspend fun MutableList<NavKey>.importNotebook(
  shared: SharedNotebook,
  portsOf: (spaceId: String) -> EnteredSpacePorts,
  spaceServers: SpaceServers,
  reader: SharedDocumentReader,
  reportRefusal: suspend (String) -> Unit = {},
): String? {
  val spaceId = enteredSpaceId()
  return runCatching {
    val bytes = reader.readBytes(shared.uri)
    if (!isInWorkspace()) spaceServers.run(spaceId)
    portsOf(spaceId).workspaceRepository.copyIn(shared.fileName, bytes)
  }.onFailure { refusal ->
    if (refusal is SharedDocumentTooLarge) reportRefusal(refusal.refusalMessage(shared.fileName))
  }.getOrNull()?.also { enterWorkspace(spaceId) }
}

/** What the user is told, with the limit in the unit a person counts a file in. */
private fun SharedDocumentTooLarge.refusalMessage(fileName: String): String {
  val mebibyte = 1024 * 1024
  val limit = if (limitBytes % mebibyte == 0) "${limitBytes / mebibyte} MiB" else "$limitBytes B"
  return "$fileName is over the $limit limit for an import"
}

/**
 * Writes [bytes] at the workspace root under [fileName], or the first name after it nothing holds.
 *
 * **A taken name is counted past**, as `createNotebook` counts. JupyterLab asks before an upload
 * overwrites (`jupyterlab@7f0e690 packages/docmanager/src/dialogs.ts` `shouldOverwrite`) and Termux
 * overwrites without asking (`termux-app@3df69d1 app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java`);
 * a VIEW intent asks to *view* a document, never to replace the one the user already has.
 */
private suspend fun WorkspaceRepository.copyIn(fileName: String, bytes: ByteArray): String {
  val taken = list("").mapTo(HashSet()) { it.name }
  val path = incrementedFileName(fileName) { it in taken }
  writeBytes(path, bytes)
  return path
}
