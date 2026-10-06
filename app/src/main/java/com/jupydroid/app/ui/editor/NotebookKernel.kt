package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.IndentUnit
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelPickRequiredException
import com.jupydroid.app.domain.port.KernelPreference
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.LanguageFormatOptions
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.SessionType
import com.jupydroid.app.domain.port.StaleKernelSessionException
import com.jupydroid.app.domain.port.SteppingCommand
import com.jupydroid.app.domain.port.formattingOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * The notebook's handle on its kernel: run a cell, restart it, end it, ask it what it knows.
 *
 * Split out of `EditorViewModel` because those are one responsibility and the document - load,
 * edit, rename, reshape, save - is a different one. detekt said so first (`TooManyFunctions` at
 * 11 of 11), and the answer to that is to find the object that wants to exist rather than raise
 * the threshold; it is the same call that produced [DocumentAutosave].
 *
 * It said so again when [shutdown] arrived, and the seam the second time was *running cells* -
 * a queue, a stop in flight and a clock, none of which the rest of this needs. That is
 * [NotebookCellRuns], next door; what stays here is the session and what is done **to** the
 * kernel. The three run methods are kept on this class rather than exposing the collaborator,
 * because "the notebook's kernel" is the one handle every screen and test already holds.
 *
 * It owns the [NotebookKernelSession], which is why completion lives here despite not being
 * execution: the session is the thing all of it needs, and splitting on "is it a run" alone would
 * have put the session in one object and a user of it in another.
 *
 * ```
 * val kernel = NotebookKernel(uiState, kernelGateway, space, viewModelScope, runs, checkpoint, debugger = debugger)
 * kernel.runCell(cell)
 * kernel.close(applicationScope)   // on the way out
 * ```
 */
class NotebookKernel(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val kernelGateway: KernelGateway,
  /** The entered Space - whose server runs this notebook's kernel and whose catalogue it resolves. */
  private val space: Space,
  private val scope: CoroutineScope,
  /**
   * A run is counted here for as long as it lasts, opening the kernel included - see
   * [ActiveCellRuns] for why the kernel starting has to be part of it.
   */
  private val activeCellRuns: ActiveCellRuns,
  /**
   * What a finished run does to the document - write it, or mark it owed - decided by the
   * `autosave` setting on the document half; this only says a run has something to commit.
   */
  private val checkpoint: suspend () -> Unit,
  /** Injected so a test can assert on a run's duration without waiting for one. */
  private val clock: Clock = Clock.systemUTC(),
  /** Arms a cell's breakpoints before it runs, and reports where the kernel stops; see [NotebookCellRuns]. */
  private val debugger: NotebookDebugger,
  /**
   * `kernelShutdown`'s live read, consulted by [close] at the moment the document goes - the way
   * `SessionContext` reads `kernelPreference.shutdownOnDispose` inside `dispose()` rather than
   * once at construction. A provider and not a field value because a flipped setting reaches
   * notebooks already open.
   */
  private val shutdownOnClose: () -> Boolean = { false },
  /**
   * The `options` a `textDocument/rangeFormatting` must carry, read live at ask time the way
   * [shutdownOnClose] reads its own setting - a flipped `indentUnit` reaches a format asked
   * after it. Defaults to the schema's own `'4'` unit for a test driving the kernel alone.
   */
  private val formattingOptions: () -> LanguageFormatOptions = { IndentUnit.SPACES_4.formattingOptions() },
  /**
   * The Space's language server - the completion source, asked before the kernel and lazily
   * spawned by the port on the first ask. Faked where no Space can run, as [kernelGateway] is.
   */
  private val languageServer: LanguageServerPort = FakeLanguageServerPort(),
  /**
   * Where "the session ended elsewhere" arrives - `SessionContext`'s `session.disposed`
   * subscription (`packages/apputils/src/sessioncontext.tsx`), which is what lands this
   * notebook on No Kernel when the Running panel shuts its session down.
   */
  private val kernelSessions: KernelSessions = FakeKernelSessions(),
  /**
   * The Space's kernelspec catalogue - what a persisted preference is resolved against before it
   * reaches the wire, and what a bound kernel's `display_name`/`language` are read from for the
   * `_updateSpec` write.
   */
  private val kernelSpecs: KernelSpecs = FakeKernelSpecs(),
  /**
   * `kernelChanged` -> `NotebookPanel._updateSpec` (`panel.ts:248-258`): told the session and the
   * catalogue entry (or null) each time a kernel binds, so the document can persist
   * `metadata.kernelspec`. Suspending because resolving the spec is a suspend ask.
   */
  private val onKernelBound: suspend (KernelSession, KernelSpec?) -> Unit = { _, _ -> },
) {
  /** Which kernel this notebook is on, and how it gets another when that one's server stops. */
  private val session =
    NotebookKernelSession(
      kernelGateway,
      space,
      kernelSpecs,
      onConnectedChanged = { connected ->
        // `kernelDisplayName`'s no-kernel half: the spec the bound kernel resolved to goes
        // with it, so the indicator lands on "No Kernel" rather than naming one that is gone.
        uiState.update {
          it.copy(kernelConnected = connected, kernelDisplayName = if (connected) it.kernelDisplayName else "No Kernel")
        }
      },
      onBound = { bound ->
        scope.launch {
          val spec = kernelSpecs.specs(space).named(bound.kernelName)
          // `specs[kernel.name]?.display_name ?? kernel.name` - the reference's own derivation.
          uiState.update { it.copy(kernelDisplayName = spec?.displayName ?: bound.kernelName) }
          onKernelBound(bound, spec)
        }
      },
      onPendingKernelName = { name ->
        uiState.update { it.copy(kernelDisplayName = name) }
      },
    ) { uiState.value.notebookId }

  /**
   * `SessionContext.kernelPreference`, seeded from the file once it loads
   * (`Context._populate`, `docregistry/src/context.ts:587-595`) - written by the document half,
   * which is the one that has read the notebook.
   */
  var kernelPreference: KernelPreference?
    get() = session.kernelPreference
    set(value) {
      session.kernelPreference = value
    }

  init {
    scope.launch { kernelSessions.ended.collect { session.endIfMatches(it) } }
    scope.launch { kernelSessions.rebound.collect { session.rebindIfMatches(it) } }
  }

  private val runs =
    NotebookCellRuns(uiState, kernelGateway, session, scope, activeCellRuns, checkpoint, clock, debugger)

  /**
   * `notebook:access-previous-history-entry` / `access-next-history-entry` - what the kernel has
   * run, walked from the cell being edited. See [NotebookHistory].
   *
   * **Exposed where [runs] is hidden**, and the difference is what each is: the three run methods
   * are the same idea as the session this class owns, so they read as its own. A walk through the
   * kernel's history has a cursor, a batch size and a rule about what ends it, none of which the
   * rest of the kernel wants - and three more methods here is a class about two things.
   */
  val history = NotebookHistory(session, kernelGateway, scope)

  /**
   * The language-server-first ask [completionsAt] delegates to - see [NotebookCompletion] for
   * the ordering and why it lives beside this class rather than in it.
   */
  /**
   * The notebook's one document under the language server: completion syncs through
   * it on every ask, and the diagnostics watch both keeps it synced with the cells and maps
   * `publishDiagnostics` ranges back through its mirror - one truth for both feeds.
   */
  private val languageDocument = NotebookLanguageDocument(languageServer, space) { uiState.value.notebookId }

  private val completion = NotebookCompletion(session, kernelGateway, languageServer, languageDocument)

  /** The "Symbol info" ask - `textDocument/hover` at the caret's virtual position. */
  private val hover = NotebookHover(session, languageServer, languageDocument)

  /** The signature-help ask - `textDocument/signatureHelp` at the caret's virtual position. */
  private val signatureHelp = NotebookSignatureHelp(session, languageServer, languageDocument)

  /** The definition ask - `textDocument/definition` at the caret's virtual position. */
  private val definition = NotebookDefinition(session, languageServer, languageDocument)

  /** The format ask - `textDocument/rangeFormatting` over the cell's virtual block. */
  private val format = NotebookFormat(session, languageServer, languageDocument, formattingOptions)

  /** The rename ask - `textDocument/rename` at the caret's virtual position. */
  private val rename = NotebookRename(session, languageServer, languageDocument)

  /**
   * The cell-level asks - completion, hover, signature help, definition, format, rename -
   * [NotebookAsks] owns like [history].
   */
  val asks = NotebookAsks(uiState, completion, hover, signatureHelp, definition, format, rename)

  /** `publishDiagnostics` as per-cell marks in state - see [NotebookDiagnostics]. */
  private val diagnostics =
    NotebookDiagnostics(uiState, languageServer, languageDocument, scope).also { it.start() }

  /**
   * A Console's own session, pre-attached to whatever kernel this notebook already has.
   *
   * `console:inject`'s own contract (`console-extension/src/index.ts:1294-1338`): find the session
   * already tracked for this path and hand it over, never call `connect` a second time. A fresh
   * [NotebookKernelSession] rather than [session] itself, because a Console lives in its own
   * `ViewModelStore` entry - [attachedTo] is what lets its own copy skip the connect it exists to
   * skip, without the two screens sharing one mutable object across two independent lifecycles.
   * `null` when this notebook has never opened a kernel of its own yet - the returned session then
   * behaves exactly like a fresh one, and opens on whichever of the two runs something first.
   */
  fun attachConsole(): NotebookKernelSession = NotebookKernelSession(
    kernelGateway,
    space,
    kernelSpecs,
    attachedTo = session.opened,
    // `SessionContext({type: 'console'})` (`console/panel.ts:71`) - declared even though the
    // POST only goes out when there is no session to share, since the field is the opener's
    // to state, not the path's to imply.
    sessionType = SessionType.CONSOLE,
    // The kernel the notebook prefers - seeded from its metadata, then kept in step with what
    // it is bound to - is the console's preference too: a stale reconnect re-asks for it
    // rather than the server's default.
    kernelPreference = session.kernelPreference,
  ) { uiState.value.notebookId }

  /** @see NotebookCellRuns.runCell */
  fun runCell(cell: Cell) = runs.runCell(cell)

  /**
   * Runs a range of the notebook - `run-all-cells`, `run-all-above`, `run-all-below` and the
   * marked run, which differ only in which cells they name.
   *
   * @see NotebookCellRuns.run
   */
  fun run(range: RunRange) = runs.run(range)

  /** @see NotebookCellRuns.stopRunningCell */
  fun stopRunningCell() = runs.stopRunningCell()

  /**
   * The `selectKernel` dialog's kernel half - `notebook:change-kernel`, the kernel-name button's
   * tap and the load-time pick all reach it. A collaborator the way [history] and [asks] are;
   * see [NotebookKernelPicker]. The `shutdown` it delegates is this class's, because ending the
   * session is the kernel's act even when the picker's "No Kernel" is what asked for it.
   */
  val picker = NotebookKernelPicker(uiState, session, space, kernelSpecs, kernelSessions, scope) { shutdown() }

  /** @see NotebookCellRuns.step */
  fun step(command: SteppingCommand) = runs.step(command)

  /**
   * Restarts the kernel: a fresh interpreter, and every variable gone.
   *
   * **The caller is expected to have asked first.** This is irreversible and silent - nothing on
   * screen changes except that the notebook's state is no longer real - which is why JupyterLab
   * puts it behind a warn dialog reading *"All variables will be lost"*
   * (`packages/apputils/src/sessioncontext.tsx:1505-1520`).
   *
   * Does nothing **to the kernel** when none has been opened: there is no process to replace, and
   * connecting one only to restart it would boot a runtime the user did not ask for.
   *
   * [then] is what the three compound commands do afterwards - `restart-run-all`,
   * `restart-clear-output` and `restart-and-run-to-selected` are each an awaited restart followed
   * by one more thing (`packages/notebook-extension/src/index.ts:3245-3345`), which is why this
   * takes a continuation rather than growing a flag per command.
   *
   * It runs **after** the kernel has been replaced, and for a run that is the difference between a
   * result and a lie: cells executed first would answer from the interpreter the restart is about
   * to destroy. And it runs even when there was no kernel, exactly as [shutdown]'s does - with
   * nothing to replace there is nothing to wait for, and the second half of a command is not
   * conditional on the first having had work to do.
   */
  fun restart(then: suspend () -> Unit = {}) {
    scope.launch {
      // Nothing to replace - the continuation is not conditional on a kernel ever having run.
      if (session.opened == null) {
        then()
        return@launch
      }
      var replaced = false
      // Announced for the length of the call rather than after it: replacing an interpreter is
      // the one kernel operation with nothing else on screen to say it is happening, and on a
      // device it is not instant.
      uiState.update { state -> state.copy(kernelRestarting = true) }
      try {
        // `reopeningIfStale`, the run's own policy: the kernel a stale session named died with
        // its server, so the ask reconnects first - a fresh kernel is what a restart means.
        session.reopeningIfStale { open -> kernelGateway.restart(open) }
        replaced = true
      } catch (_: StaleKernelSessionException) {
        // The reconnect's second refusal is a real failure: `opened` is already dropped and the
        // indicator's No Kernel is the truth of it - the continuation's premise went with it.
      } catch (_: KernelPickRequiredException) {
        // The reconnect owes a kernel pick - the same cue a run lands on, not a crash.
        uiState.update { it.copy(kernelPickRequired = it.kernelPickRequired + 1) }
      } finally {
        // Whatever was in flight died with the process; anything finished is history and stays.
        uiState.update { state -> state.abandoningRun().copy(kernelRestarting = false) }
      }
      if (replaced) then()
    }
  }

  /**
   * `notebook:restart-run-all` - a restart, then every cell.
   *
   * Through [NotebookCellRuns.runCells] rather than [run] for the reason [restartAndRunTo] gives:
   * the reference composes this one out of the public `NotebookActions.runCells` (`index.ts:3315`),
   * which leaves the selection alone, and not out of `runAll`, which deselects.
   */
  fun restartAndRunAll() {
    val cells = uiState.value.cells
    restart(then = { runs.runCells(cells) })
  }

  /**
   * `notebook:restart-and-run-to-selected` - a restart, then the head and everything above it.
   *
   * Its range is [RunRange.UP_TO_SELECTED] and its runner is [NotebookCellRuns.runCells] rather
   * than [run], which is not an implementation detail: the reference hands its slice to the
   * **public** `NotebookActions.runCells` (`index.ts:3283`), and that one touches no selection - so
   * unlike the four `run*` commands this leaves the head where it is.
   *
   * The cells are claimed before the restart rather than inside it, so the command runs what was on
   * screen when it was asked for.
   */
  fun restartAndRunTo() {
    val cells = uiState.value.cellsToRun(RunRange.UP_TO_SELECTED)
    restart(then = { runs.runCells(cells) })
  }

  /**
   * Ends the session: the kernel process stops and every variable goes with it.
   *
   * **`notebook:shutdown-kernel`, and the deliberate opposite of [close].** Leaving a notebook
   * closes the socket and leaves the kernel running, so reopening reattaches to it; this is the
   * user saying that is not what they want. The reference guards it with nothing at all - it is
   * the *close* variant that asks first (`notebook:close-and-shutdown`,
   * `packages/notebook-extension/src/index.ts:3189-3213`) - so the dialog lives with that command
   * and not here.
   *
   * [then] is what `close-and-shutdown` does afterwards, and it runs **after** the session has
   * ended rather than beside it: the reference awaits its shutdown before disposing the widget.
   * It runs even when there was no kernel, because the command is Close *and* shut down and the
   * closing half is not conditional.
   *
   * Does nothing to the kernel when none was opened - connecting one in order to end it would
   * boot a runtime the user never asked for, exactly as [restart] refuses to.
   */
  fun shutdown(then: () -> Unit = {}) {
    val open = session.forget()
    scope.launch {
      open?.let { kernelGateway.shutdown(it) }
      // Whatever was in flight died with the process, and nothing that has finished is a lie.
      uiState.update { it.abandoningRun() }
      then()
    }
  }

  /**
   * Closes the socket, on a scope that outlives the screen.
   *
   * **Not a shutdown, by default, and that is deliberate**: `SessionContext.dispose()` disposes
   * the client connection and shuts the kernel down only when `shutdownOnDispose` is set, which
   * defaults to false (`packages/apputils/src/sessioncontext.tsx:679-709`). The reference's
   * `kernelShutdown` setting flips exactly this branch - the ordinary close then asks for the
   * shutdown the ⋮ menu's own command used to be the only way to get, which [shutdownOnClose]
   * is the live read of: the dispose asks again, not the open, so a setting flipped while the
   * document sits open is honoured by the close that follows it.
   */
  fun close(on: CoroutineScope) {
    val open = session.forget() ?: return
    on.launch {
      // shutdown() closes the socket first itself (`RealKernelGateway`'s own ordering), so the
      // branch is one call either way.
      if (shutdownOnClose()) kernelGateway.shutdown(open) else kernelGateway.disconnect(open)
    }
  }
}
