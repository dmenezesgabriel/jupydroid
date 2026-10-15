package com.jupydroid.app.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.untitledNotebookIn
import com.jupydroid.app.domain.port.KernelGateway
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
  /** Asked for [space]'s default kernel, the one a new notebook records, as `ExplorerViewModel.create` does. */
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
      notebookRepository.createNotebook(untitledNotebookIn(cwd), kernelSpecs.specs(space).defaultSpec).id,
    )

    LauncherCommand.NEW_CONSOLE -> mintConsole(cwd)
    LauncherCommand.NEW_TERMINAL ->
      LaunchedDocument.OpenedTerminal(terminalGateway.open(cwd.ifEmpty { null }).name)
    LauncherCommand.NEW_TEXT_FILE -> createdFile(cwd, "txt")
    LauncherCommand.NEW_MARKDOWN_FILE -> createdFile(cwd, "md")
    LauncherCommand.NEW_PYTHON_FILE -> createdFile(cwd, "py")
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
  private suspend fun mintConsole(cwd: String): LaunchedDocument.CreatedConsole {
    val ordinal = consoleOrdinal()
    val name = "console-$ordinal-${UUID.randomUUID()}"
    val path = if (cwd.isEmpty()) name else "$cwd/$name"
    val session = NotebookKernelSession(kernelGateway, space, sessionType = SessionType.CONSOLE) { path }
    session.open()
    return LaunchedDocument.CreatedConsole(session, path, "Console $ordinal")
  }
}
