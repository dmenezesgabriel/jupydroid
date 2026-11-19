package com.jupydroid.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.ErrorLogSummary
import com.jupydroid.app.domain.port.ErrorLog
import com.jupydroid.app.domain.port.ErrorLogSharer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** What the Diagnostics section shows: nothing yet, the log's summary, or why it could not be read or cleared. */
sealed interface DiagnosticsUiState {
  data object Loading : DiagnosticsUiState

  /** [confirmingClear] is true while the "Clear logs" confirmation, which states the size, is open. */
  data class Loaded(val summary: ErrorLogSummary, val confirmingClear: Boolean = false) : DiagnosticsUiState

  /** [message] names the cause, so the section states it rather than showing an empty log. */
  data class Failed(val message: String) : DiagnosticsUiState
}

/**
 * The Diagnostics section's state: what the error log holds, a share, and a clear that waits for confirmation.
 *
 * ```
 * val viewModel = DiagnosticsViewModel(appContainer.errorLog, appContainer.errorLogSharer)
 * viewModel.share()
 * viewModel.askToClear()
 * viewModel.confirmClear()
 * ```
 */
class DiagnosticsViewModel(private val errorLog: ErrorLog, private val errorLogSharer: ErrorLogSharer) : ViewModel() {
  private val _uiState = MutableStateFlow<DiagnosticsUiState>(DiagnosticsUiState.Loading)
  val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch { _uiState.value = read() }
  }

  /** Opens the confirmation; nothing is deleted until [confirmClear]. */
  fun askToClear() = setConfirming(true)

  /** Closes the confirmation and keeps every entry. */
  fun dismissClear() = setConfirming(false)

  /** Deletes the log, but only from an open confirmation - a stray call clears nothing. */
  fun confirmClear() {
    val state = _uiState.value
    if (state !is DiagnosticsUiState.Loaded || !state.confirmingClear) return
    viewModelScope.launch {
      _uiState.value = try {
        errorLog.clear()
        read()
      } catch (failure: IOException) {
        DiagnosticsUiState.Failed("Could not clear the error log: ${failure.message}")
      }
    }
  }

  /** Offers the log to another app; an empty log has nothing to offer, so a stray call shares nothing. */
  fun share() {
    val state = _uiState.value
    if (state !is DiagnosticsUiState.Loaded || state.summary.entryCount == 0) return
    viewModelScope.launch {
      try {
        errorLogSharer.share()
      } catch (failure: IOException) {
        _uiState.value = DiagnosticsUiState.Failed("Could not share the error log: ${failure.message}")
      }
    }
  }

  private fun setConfirming(confirming: Boolean) = _uiState.update { state ->
    if (state is DiagnosticsUiState.Loaded) state.copy(confirmingClear = confirming) else state
  }

  private suspend fun read(): DiagnosticsUiState = try {
    DiagnosticsUiState.Loaded(errorLog.summary())
  } catch (failure: IOException) {
    DiagnosticsUiState.Failed("Could not read the error log: ${failure.message}")
  }
}
