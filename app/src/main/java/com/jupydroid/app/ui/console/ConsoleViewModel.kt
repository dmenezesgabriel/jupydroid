package com.jupydroid.app.ui.console

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.ui.editor.ExecutionStatus
import com.jupydroid.app.ui.editor.NotebookHistory
import com.jupydroid.app.ui.editor.NotebookKernelSession
import com.jupydroid.app.ui.editor.kernelFailure
import com.jupydroid.app.ui.editor.newCellId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs what is typed into the Console against the notebook's own kernel session.
 *
 * [submit] is `console:run-forced`'s mobile stand-in: there is no Shift+Enter here to force a run
 * with, so a dedicated action replaces the reference's Enter/Shift+Enter split rather than porting
 * it (a design decision). [session] is handed in already attached to the
 * open notebook's kernel - this class neither builds nor owns one.
 *
 * ```kotlin
 * val viewModel = ConsoleViewModel(kernelGateway, session)
 * viewModel.input.edit { insert(0, "1 + 1") }
 * viewModel.submit()
 * ```
 */
class ConsoleViewModel(
  private val kernelGateway: KernelGateway,
  private val session: NotebookKernelSession,
  /**
   * Where "the session ended elsewhere" arrives: the session this Console shares is one
   * the Running panel lists and can shut down, and keeping it would have the next [submit] run
   * against a session the server deleted. `session.disposed`'s own subscription, by another name.
   */
  kernelSessions: KernelSessions = FakeKernelSessions(),
) : ViewModel() {
  init {
    viewModelScope.launch { kernelSessions.ended.collect { session.endIfMatches(it) } }
    // `kernelChanged` beside `disposed`: the notebook may swap the kernel under the session this
    // console shares - the copy here follows the rebound or it would address a dead kernel.
    viewModelScope.launch { kernelSessions.rebound.collect { session.rebindIfMatches(it) } }
  }

  /** The draft the input row edits, and what a history walk replaces the text of. */
  val input = TextFieldState()

  /**
   * `console:history-back`/`-forward`'s mobile stand-in.
   *
   * [NotebookHistory] is itself already modelled on `console/src/history.ts` line for line (its
   * own doc comment), so this is the reference's own class returned to the surface it was written
   * for, not a notebook feature being ported sideways. [session] and [kernelGateway] are exactly
   * what it already takes; there was nothing Console-specific left to write.
   */
  private val history = NotebookHistory(session, kernelGateway, viewModelScope)

  private val _entries = MutableStateFlow<List<ConsoleEntry>>(emptyList())
  val entries = _entries.asStateFlow()

  /** The entry [interrupt] asked to stop, so its resolution reads as cancelled rather than raised. */
  private var interrupting: String? = null

  /** One step older per tap, oldest last - see [NotebookHistory.recallPrevious]. */
  fun recallPreviousEntry() = history.recallPrevious(input.text.toString(), ::writeInput)

  /** One step newer per tap, back to what was being typed at the walk's end. */
  fun recallNextEntry() = history.recallNext(input.text.toString(), ::writeInput)

  /**
   * The field reported its text - [ConsoleInputRow]'s own debounced `onSourceEdited`.
   *
   * Ends a walk in progress unless this is the edit the walk itself made - see
   * [NotebookHistory.sourceReported]. The same report also fires after [submit] clears the field,
   * which is what ends a walk at a submission boundary without a second, explicit call: a fresh
   * entry is exactly what a notebook cell losing focus already resets against.
   */
  fun inputEdited(source: String) = history.sourceReported(source)

  private fun writeInput(text: String) = input.edit { replace(0, length, text) }

  /**
   * `console:clear` - drops every transcript entry, keeping the session exactly as it is.
   *
   * Unconditional, matching `CodeConsole.clear()` (`console/src/widget.ts:333-339`): a future
   * `Output`/`Idle` for whatever was `RUNNING` folds into an id [withEntry] no longer finds and is
   * silently dropped - the same fate the reference's own disposed cell gets once its id drops out
   * of `_msgIdCells`.
   */
  fun clear() = _entries.update { emptyList() }

  /**
   * Submits [input]'s current text as a new entry and runs it.
   *
   * Always the *forced* arm: the reference's unforced `execute()` asks `is_complete_request`
   * whether the block is whole and inserts a newline when it is not
   * (`packages/console/src/widget.ts:979`), and `docs/architecture.md` §11's `GAP-012` records
   * that nothing here sends it - so a submission runs rather than waiting for the body.
   *
   * A no-op on blank input, and a no-op while the last entry is still [ExecutionStatus.RUNNING] -
   * `RunControl`'s own disabled-while-busy treatment, not the reference's queue (this row's own
   * concurrency decision).
   */
  fun submit() {
    val source = input.text.toString()
    if (source.isBlank() || isRunning()) return
    val id = newCellId()
    _entries.update { it + ConsoleEntry(id, source, emptyList(), ExecutionStatus.RUNNING) }
    input.edit { replace(0, length, "") }
    viewModelScope.launch { run(id, source) }
  }

  private fun isRunning(): Boolean = _entries.value.lastOrNull()?.status == ExecutionStatus.RUNNING

  /**
   * `console:interrupt-kernel`'s mobile stand-in - `NotebookCellRuns.stopRunningCell`'s own
   * pattern: the entry itself has no handle on the kernel's work, so this is how it is stopped.
   * Does nothing when nothing is running, since the kernel may be busy with somebody else's cell.
   */
  fun interrupt() {
    val running = _entries.value.lastOrNull()?.takeIf { it.status == ExecutionStatus.RUNNING } ?: return
    interrupting = running.id
    viewModelScope.launch { session.whileOpen { open -> kernelGateway.interrupt(open) } }
  }

  /**
   * Answers the prompt entry [id] is blocked on - `NotebookCellRuns.answerInput`'s own order: the
   * echo is folded first, so it precedes what the entry prints with the answer (ADR-0082). An
   * entry with no prompt is a no-op.
   *
   * ```kotlin
   * viewModel.answerInput(entry.id, "Ada")   // after entry.inputRequest appeared
   * ```
   */
  fun answerInput(id: String, value: String) {
    val request = _entries.value.firstOrNull { it.id == id }?.inputRequest ?: return
    _entries.update { entries ->
      entries.withEntry(id) { it.copy(outputs = it.outputs + request.echoOf(value), inputRequest = null) }
    }
    viewModelScope.launch { session.whileOpen { open -> kernelGateway.replyInput(open, request, value) } }
  }

  private suspend fun run(id: String, source: String) {
    // `reopeningIfStale` resolves inside the flow rather than before it, the shape
    // `NotebookCellRuns.eventsFor` already carries: a refused `connect` has to reach the same
    // error output a refused `execute` does, or the entry spins RUNNING for ever.
    flow { emitAll(session.reopeningIfStale { open -> kernelGateway.execute(open, id, source) }) }
      .catch { failure -> emit(kernelFailure(id, failure)) }
      .collect { event -> _entries.update { entries -> entries.after(id, event) } }
    val cancelled = interrupting == id
    interrupting = null
    _entries.update { entries -> entries.resolved(id, cancelled) }
  }
}

/** Folds one [KernelEvent] into the entry it belongs to - `EditorUiState.after`'s own rule, by id. */
private fun List<ConsoleEntry>.after(id: String, event: KernelEvent): List<ConsoleEntry> = when (event) {
  is KernelEvent.Output -> withEntry(id) { it.copy(outputs = it.outputs + event.output) }
  is KernelEvent.ExecutionCount -> withEntry(id) { it.copy(executionCount = event.count) }
  is KernelEvent.InputRequested -> withEntry(id) { it.copy(inputRequest = event.request) }
  KernelEvent.Idle -> this
}

/**
 * What `Idle` means once the flow has finished - `NotebookCellRuns.run`'s own cancelled/raised/ok
 * rule, [cancelled] taking priority since an interrupted run's own output is a `KeyboardInterrupt`
 * that would otherwise read as [ExecutionStatus.STOPPED_ERROR].
 */
private fun List<ConsoleEntry>.resolved(id: String, cancelled: Boolean): List<ConsoleEntry> = withEntry(id) { entry ->
  val raised = entry.outputs.any { it is CellOutput.Error }
  entry.copy(
    // The prompt belongs to the run that asked; once it ends there is nobody to answer.
    inputRequest = null,
    status = when {
      cancelled -> ExecutionStatus.CANCELLED
      raised -> ExecutionStatus.STOPPED_ERROR
      else -> ExecutionStatus.STOPPED_OK
    },
  )
}

private inline fun List<ConsoleEntry>.withEntry(
  id: String,
  transform: (ConsoleEntry) -> ConsoleEntry,
): List<ConsoleEntry> = map { if (it.id == id) transform(it) else it }
