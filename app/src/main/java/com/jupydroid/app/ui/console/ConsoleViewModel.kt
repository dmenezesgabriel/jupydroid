package com.jupydroid.app.ui.console

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.ui.editor.ExecutionStatus
import com.jupydroid.app.ui.editor.NotebookKernelSession
import com.jupydroid.app.ui.editor.kernelFailure
import com.jupydroid.app.ui.editor.newCellId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs what is typed into the Console against the notebook's own kernel session - P6b-4a.
 *
 * [submit] is `console:run-forced`'s mobile stand-in: there is no Shift+Enter here to force a run
 * with, so a dedicated action replaces the reference's Enter/Shift+Enter split rather than porting
 * it (`docs/PLAN.md`'s own P6b-4 design decision). [session] is handed in already attached to the
 * open notebook's kernel (P6b-1) - this class neither builds nor owns one.
 *
 * ```kotlin
 * val viewModel = ConsoleViewModel(kernelGateway, session)
 * viewModel.input.edit { insert(0, "1 + 1") }
 * viewModel.submit()
 * ```
 */
class ConsoleViewModel(private val kernelGateway: KernelGateway, private val session: NotebookKernelSession) :
  ViewModel() {
  /** The draft the input row edits - P6b-6's own history walk will replace its text the same way. */
  val input = TextFieldState()

  private val _entries = MutableStateFlow<List<ConsoleEntry>>(emptyList())
  val entries = _entries.asStateFlow()

  /**
   * Submits [input]'s current text as a new entry and runs it.
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

  private suspend fun run(id: String, source: String) {
    session.reopeningIfStale { open -> kernelGateway.execute(open, id, source) }
      .catch { failure -> emit(kernelFailure(id, failure)) }
      .collect { event -> _entries.update { entries -> entries.after(id, event) } }
    _entries.update { entries -> entries.resolved(id) }
  }
}

/** Folds one [KernelEvent] into the entry it belongs to - `EditorUiState.after`'s own rule, by id. */
private fun List<ConsoleEntry>.after(id: String, event: KernelEvent): List<ConsoleEntry> = when (event) {
  is KernelEvent.Output -> withEntry(id) { it.copy(outputs = it.outputs + event.output) }
  is KernelEvent.ExecutionCount -> withEntry(id) { it.copy(executionCount = event.count) }
  KernelEvent.Idle -> this
}

/** What `Idle` means once the flow has finished - `NotebookCellRuns.run`'s own raised/ok rule. */
private fun List<ConsoleEntry>.resolved(id: String): List<ConsoleEntry> = withEntry(id) { entry ->
  val raised = entry.outputs.any { it is CellOutput.Error }
  entry.copy(status = if (raised) ExecutionStatus.STOPPED_ERROR else ExecutionStatus.STOPPED_OK)
}

private inline fun List<ConsoleEntry>.withEntry(
  id: String,
  transform: (ConsoleEntry) -> ConsoleEntry,
): List<ConsoleEntry> = map { if (it.id == id) transform(it) else it }
