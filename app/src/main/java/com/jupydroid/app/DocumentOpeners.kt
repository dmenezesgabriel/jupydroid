package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.NOTEBOOK_SUFFIX
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.SpaceServers
import com.jupydroid.app.domain.port.TerminalGateway
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
 * The reveal asks [OpenDocuments] first. `openOrReveal` matches a widget by its context's
 * **current** path - `manager.ts:481-503` reaches it through `_contextsForPath`, which filters
 * on where a context lives *now* - and a `NavKey` keeps only the path it was opened under, so a
 * document renamed since it opened answers to a path the stack has never seen. `at` resolves the
 * live path to the `openedAs` the key still carries; a path nothing is open on falls through to
 * the suffix split, and a renamed file's `openedAs` still points the right kind at it.
 */
internal fun MutableList<NavKey>.openDocument(path: String, openDocuments: OpenDocuments) {
  val openedAs = openDocuments.at(path)?.openedAs ?: path
  if (openedAs.endsWith(NOTEBOOK_SUFFIX, ignoreCase = true)) {
    openNotebook(openedAs)
  } else {
    openFile(openedAs)
  }
}

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
 * Opening a document, wherever it was opened from: the right editor, and a line in Recents.
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
    backStack.openDocument(path, openDocuments)
    val recentDocuments = appContainer.portsOf(backStack.enteredSpaceId()).recentDocuments
    appContainer.applicationScope.launch { recentDocuments.recordOpening(path) }
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
 * `runCatching` the VIEW intent gives `createNotebook` ([ImportedNotebookEffect]), since this reach has
 * no error surface the way the launcher card's `LauncherViewModel.error` is.
 */
internal suspend fun MutableList<NavKey>.openNewTerminal(terminalGateway: TerminalGateway, cwd: String) {
  runCatching { terminalGateway.open(cwd.ifEmpty { null }) }
    .onSuccess { openTerminal(it.name) }
}

/**
 * The VIEW intent, which used to be Home's because Home was the first screen. It is an app-level
 * act rather than a screen's - nothing about it belongs to the browser. Keyed on the title so a
 * second import in the same process is not swallowed.
 *
 * **Opened, not merely listed.** Home added a row and left it there, which was defensible when
 * Home was a list of notebooks; an ACTION_VIEW intent asks to *view* the document, and opening
 * it is also what makes the result deterministic - a row only appears once the browser has
 * listed again, and the create races that listing on a cold start. Opening also records it as a
 * recent, so it is on the browser's first section when the editor is left.
 *
 * **Inside a workspace.** A document belongs to one, so the act itself is [importNotebook]: it lands
 * in the Space already entered, or from the Spaces home runs and enters base. The browser is then the
 * list pane the document pairs with, rather than a bare home underneath it.
 *
 * ```
 * ImportedNotebookEffect(importTitle, appContainer, backStack, openDocument, onImportConsumed)
 * ```
 */
@Composable
internal fun ImportedNotebookEffect(
  importTitle: String?,
  appContainer: AppContainer,
  backStack: MutableList<NavKey>,
  openDocument: (String) -> Unit,
  onImportConsumed: () -> Unit,
) {
  LaunchedEffect(importTitle) {
    importTitle?.let { title ->
      backStack.importNotebook(title, appContainer::portsOf, appContainer.spaceServers)
        ?.let(openDocument)
      onImportConsumed()
    }
  }
}

/**
 * The VIEW intent's act on the stack, testable without Compose: create the notebook in the Space it
 * lands in, and enter that Space. **Inside a workspace it stays in the Space entered**, since the
 * workspace never switches Space in place. **From the home it runs base first**, because a Space is
 * only entered running (the home's Open is gated on it). A refused run or create opens nothing and
 * returns null, the `runCatching` [openNewTerminal] gives a refused mint. The notebook records that
 * Space's default kernel, asked after the run so a running server answers it, as
 * `ExplorerViewModel.create` does.
 *
 * ```
 * backStack.importNotebook("Imported", appContainer::portsOf, spaceServers)
 * ```
 */
internal suspend fun MutableList<NavKey>.importNotebook(
  title: String,
  portsOf: (spaceId: String) -> EnteredSpacePorts,
  spaceServers: SpaceServers,
): String? {
  val spaceId = enteredSpaceId()
  return runCatching {
    if (!isInWorkspace()) spaceServers.run(spaceId)
    val ports = portsOf(spaceId)
    ports.notebookRepository.createNotebook(title, ports.kernelSpecs.specs(ports.space).defaultSpec)
  }.getOrNull()?.let { notebook ->
    enterWorkspace(spaceId)
    notebook.id
  }
}
