package com.jupydroid.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.LicenceDocument
import com.jupydroid.app.domain.port.LicenceDocuments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Licences screen shows: nothing yet, the documents, or why there are none. */
sealed interface LicencesUiState {
  data object Loading : LicencesUiState

  /** [reading] is the document open for reading, or null while the list is showing. */
  data class Listed(val documents: List<LicenceDocument>, val reading: LicenceDocument? = null) : LicencesUiState

  /** [message] names what could not be read, so the screen can state it rather than show an empty list. */
  data class Failed(val message: String) : LicencesUiState
}

/**
 * The Licences screen's state: the documents the APK ships and which one is being read.
 *
 * ```
 * val viewModel = LicencesViewModel(appContainer.licenceDocuments)
 * viewModel.open("Notices")
 * ```
 */
class LicencesViewModel(private val licenceDocuments: LicenceDocuments) : ViewModel() {
  private val _uiState = MutableStateFlow<LicencesUiState>(LicencesUiState.Loading)
  val uiState: StateFlow<LicencesUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch { _uiState.value = load() }
  }

  /** Reads the document titled [title]; a title the list does not hold changes nothing. */
  fun open(title: String) = _uiState.update { state ->
    if (state !is LicencesUiState.Listed) return@update state
    state.copy(reading = state.documents.firstOrNull { it.title == title } ?: state.reading)
  }

  /** Returns from a document to the list. */
  fun close() = _uiState.update { state ->
    if (state is LicencesUiState.Listed) state.copy(reading = null) else state
  }

  // The adapter reports a missing asset as IllegalStateException naming the path; showing that text
  // is the point, so it is caught here and not left to crash the screen.
  private suspend fun load(): LicencesUiState = try {
    LicencesUiState.Listed(licenceDocuments.documents())
  } catch (failure: IllegalStateException) {
    LicencesUiState.Failed(failure.message.orEmpty())
  }
}
