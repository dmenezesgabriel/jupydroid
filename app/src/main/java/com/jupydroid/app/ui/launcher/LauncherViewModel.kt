package com.jupydroid.app.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.untitledNotebookIn
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelPreference
import com.jupydroid.app.domain.port.KernelSpecModels
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.SessionType
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.WorkspaceFiles
import com.jupydroid.app.ui.editor.NotebookKernelSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * What a launched card leaves behind - the object the back stack needs to open the new document.
 * A console carries its minted [NotebookKernelSession] so `entry<Console>` hands the screen a
 * session that is already open rather than connecting a second kernel to the same path.
 */
sealed interface LaunchedDocument {
  /** `notebook:create-new` answered: the created notebook's workspace path. */
  data class CreatedNotebook(val path: String) : LaunchedDocument

  /** `fileeditor:create-new[-file]` answered: the created file's workspace path. */
  data class CreatedFile(val path: String) : LaunchedDocument

  /** `terminal:create-new` answered: the terminal's server-side name (`1`, `2`, …). */
  data class OpenedTerminal(val name: String) : LaunchedDocument

  /** `console:create` answered: the standalone console's session, minted path and tab title. */
  data class CreatedConsole(val session: NotebookKernelSession, val path: String, val title: String) :
    LaunchedDocument
}

/**
 * Runs launcher cards against the real ports - the reference's `commands.execute(item.command,
 * {cwd: launcher.cwd})` (`packages/launcher/src/widget.tsx:173,255`). Creation and terminal naming
 * stay inside the repositories and gateway, which is why this class knows nothing about how a new
 * file is numbered or where `untitled.txt` comes from.
 */
class LauncherViewModel(
  private val notebookRepository: NotebookRepository,
  private val workspaceFiles: WorkspaceFiles,
  private val terminalGateway: TerminalGateway,
  private val kernelGateway: KernelGateway,
  /**
   * The Space's kernelspec catalogue - what the cards are built from, and what a console card's
   * `kernelPreference` resolves through, as `ExplorerViewModel.create`'s default does.
   */
  private val kernelSpecs: KernelSpecs,
  /** The entered Space, whose server a standalone console's kernel runs on. */
  private val space: Space,
  /**
   * `Private.count` (`packages/console/src/panel.ts:332`) - the console ordinal outlives a single
   * launcher entry: each push owns a fresh ViewModel, and a per-VM counter would renumber a
   * second minted console "Console 1" again. The caller supplies the holder its lifetime needs
   * (`MainNavigation`'s composition, which is also what a test's `setContent` gets fresh).
   */
  private val consoleOrdinal: () -> Int,
) : ViewModel() {

  /**
   * The reference's `_pending` flag (`widget.tsx:254-260`): while one card's command runs, further
   * taps are swallowed so a double-tap can never mint two kernels or two files.
   */
  private val _pending = MutableStateFlow(false)
  val pending: StateFlow<Boolean> = _pending

  /** The failure a launched card reported, until the user dismisses it. */
  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error

  /**
   * The cards the screen draws - one per kernelspec the Space lists, built once at entry the way
   * the reference rebuilds its launcher items on `specsChanged`
   * (`notebook-extension/src/index.ts:2578-2611`). Empty until the catalogue answers.
   */
  private val _items = MutableStateFlow<List<LauncherItem>>(emptyList())
  val items: StateFlow<List<LauncherItem>> = _items

  init {
    viewModelScope.launch { _items.value = launcherItems(catalogue()) }
  }

  /**
   * The Space's kernelspecs, or its declared [Space.kernels] when the read fails - the same
   * honest fallback `FakeKernelSpecs` gives a device with no Space, so a refused catalogue leaves
   * the utility cards and `python3` rather than a blank launcher.
   */
  private suspend fun catalogue(): KernelSpecModels = runCatching { kernelSpecs.specs(space) }
    .getOrElse { failure ->
      // `runCatching` cannot tell a refusal from a cancellation - the same trap `launch` names.
      if (failure is CancellationException) throw failure
      KernelSpecModels(
        default = space.kernels.firstOrNull()?.name.orEmpty(),
        specs = space.kernels,
      )
    }

  /** Runs [item]'s command at [cwd] and hands the result to [onLaunched]. No-ops while pending. */
  fun launch(item: LauncherItem, cwd: String, onLaunched: (LaunchedDocument) -> Unit) {
    if (_pending.value) return
    _pending.value = true
    viewModelScope.launch {
      try {
        runCatching { run(item, cwd) }
          .onSuccess(onLaunched)
          .onFailure { failure ->
            // The contract `ExplorerViewModel.list` keeps: a cancellation is this scope ending,
            // not a launch failure to report - and `runCatching` cannot tell them apart.
            if (failure is CancellationException) throw failure
            _error.value = failure.message ?: failure.toString()
          }
      } finally {
        _pending.value = false
      }
    }
  }

  fun dismissError() {
    _error.value = null
  }

  private suspend fun run(item: LauncherItem, cwd: String): LaunchedDocument = when (item.command) {
    LauncherCommand.NEW_NOTEBOOK -> LaunchedDocument.CreatedNotebook(
      notebookRepository.createNotebook(untitledNotebookIn(cwd), kernelOf(item)).id,
    )

    LauncherCommand.NEW_CONSOLE -> mintConsole(cwd, kernelOf(item))
    LauncherCommand.NEW_TERMINAL ->
      LaunchedDocument.OpenedTerminal(terminalGateway.open(cwd.ifEmpty { null }).name)
    LauncherCommand.NEW_TEXT_FILE -> createdFile(cwd, "txt")
    LauncherCommand.NEW_MARKDOWN_FILE -> createdFile(cwd, "md")
    LauncherCommand.NEW_PYTHON_FILE -> createdFile(cwd, "py")
  }

  /**
   * The spec a kernel card carries - `args.kernelName` resolved to the catalogue's own row, so
   * the notebook records its `display_name` too. A kernel command with no spec is a card
   * [launcherItems] never builds.
   */
  private fun kernelOf(item: LauncherItem): KernelSpec = checkNotNull(item.kernel) {
    "'${item.command}' card carries no kernelspec; expected launcherItems to give every kernel card its spec"
  }

  private suspend fun createdFile(cwd: String, extension: String): LaunchedDocument =
    LaunchedDocument.CreatedFile(workspaceFiles.createFile(cwd, extension))

  /**
   * The standalone console - the reference's `console:create` mints the widget at
   * `PathExt.join(basePath, 'console-<count>-<uuid>')` named `Console <count>`
   * (`packages/console/src/panel.ts:58-70,332`) with its own `SessionContext`, so the kernel's
   * working directory is [cwd] and the session belongs to the console alone, not to an open
   * notebook.
   */
  private suspend fun mintConsole(cwd: String, kernel: KernelSpec): LaunchedDocument.CreatedConsole {
    val ordinal = consoleOrdinal()
    val name = "console-$ordinal-${UUID.randomUUID()}"
    val path = if (cwd.isEmpty()) name else "$cwd/$name"
    val session = NotebookKernelSession(
      kernelGateway,
      space,
      // The real catalogue, not the session's `FakeKernelSpecs` default: a preference naming an
      // env's spec resolves through it, and its pending-name report reads the same rows.
      kernelSpecs = kernelSpecs,
      sessionType = SessionType.CONSOLE,
      kernelPreference = KernelPreference(name = kernel.name),
    ) { path }
    session.open()
    return LaunchedDocument.CreatedConsole(session, path, "Console $ordinal")
  }
}
