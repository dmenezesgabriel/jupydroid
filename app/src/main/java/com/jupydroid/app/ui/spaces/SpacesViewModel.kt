package com.jupydroid.app.ui.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.port.SpaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Spaces panel draws.
 *
 * @property spaces every Space on the device, in the port's own order - base first.
 * @property busy an act is in flight; the panel shows progress and holds its act controls.
 * @property failure the last act's or listing's refusal, verbatim, or null.
 * @property progress the in-flight create's copy fraction - null while an act is indeterminate.
 */
data class SpacesPanelState(
  val spaces: List<SpaceConfig> = emptyList(),
  val busy: Boolean = false,
  val failure: String? = null,
  val progress: Float? = null,
)

/**
 * The Spaces panel's asks of [SpaceRepository]: list, create, rename and delete.
 *
 * **One act at a time.** [busy] is raised before the act launches, so a second tap in the same
 * frame is refused rather than racing the first - two creates would otherwise both read the same
 * scan and mint the same id. vscode runs every profile action under one progress indicator for
 * the same reason (`vscode@3f3380c src/vs/workbench/contrib/userDataProfile/browser/userDataProfilesEditor.ts`,
 * `IEditorProgressService.showWhile`).
 *
 * **Every act re-lists, failed or not**: the scan is the source of truth (`DiskSpaceRepository`),
 * and a half-finished act on disk is only visible by reading it again.
 *
 * ```
 * SpacesViewModel(appContainer.spaceRepository).create("Thesis")
 * ```
 */
class SpacesViewModel(private val spaceRepository: SpaceRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(SpacesPanelState())
  val uiState: StateFlow<SpacesPanelState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch { relist() }
  }

  /** Makes a Space called [name]; the port mints its id and reports the image copy's fraction. */
  fun create(name: String) = act {
    spaceRepository.create(name) { fraction ->
      _uiState.update { it.copy(progress = fraction) }
    }
  }

  /** Renames the Space [id] to [name]; its directory keeps the id. */
  fun rename(id: String, name: String) = act { spaceRepository.rename(id, name) }

  /** Removes the Space [id] and everything in it. The port refuses base. */
  fun delete(id: String) = act { spaceRepository.delete(id) }

  private fun act(change: suspend () -> Unit) {
    if (_uiState.value.busy) return
    _uiState.update { it.copy(busy = true, failure = null) }
    viewModelScope.launch {
      reportingFailure { change() }
      relist()
      _uiState.update { it.copy(busy = false, progress = null) }
    }
  }

  /** What the disk holds now; a refused listing keeps the rows it would have replaced. */
  private suspend fun relist() {
    reportingFailure {
      val listed = spaceRepository.spaces()
      _uiState.update { it.copy(spaces = listed) }
    }
  }

  /**
   * Any refusal is put on screen as the port worded it - its messages name the path or id at fault.
   * A cancellation is not a refusal and is never swallowed.
   */
  private suspend fun reportingFailure(ask: suspend () -> Unit) {
    runCatching { ask() }.onFailure { refused ->
      if (refused is CancellationException) throw refused
      _uiState.update { it.copy(failure = refused.message) }
    }
  }
}
