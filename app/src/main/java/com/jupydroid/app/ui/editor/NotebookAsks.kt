package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The notebook's position-asks - completion while typing, the "Symbol info" hover, the
 * signature of the call the caret is inside, and where that symbol is defined -
 * `docs/PLAN.md` P5d/P5e-3/P5e-4/P5e-5.
 *
 * They read as one surface for the same reason [NotebookKernel.history] is its own object:
 * both are a cell's question about an offset, answered through the same session gate and the
 * same virtual document, and neither is the kernel lifecycle this class's owner runs. The
 * rule they share, stated once: an ask reuses the session a run already opened and never boots
 * a Python runtime the user has not asked for by running a cell -
 * `BaseSpaceKernelGateway` installs a Space and starts `jupyter_server` on first use, which is
 * seconds and ~143 MB. A failed ask is silence, not a crash: the socket dying is worth
 * reporting when a cell is running, and worth ignoring when the user is only typing.
 *
 * ```
 * kernel.asks.completionsAt(cell.id, code, cursorOffset)
 * ```
 */
class NotebookAsks(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val completion: NotebookCompletion,
  private val hover: NotebookHover,
  private val signatureHelp: NotebookSignatureHelp,
  private val definition: NotebookDefinition,
) {
  /** @see NotebookCompletion.at */
  suspend fun completionsAt(cellId: String, code: String, cursorOffset: Int): CompletionResult =
    completion.at(uiState.value.cells, cellId, code, cursorOffset)

  /** @see NotebookHover.at */
  suspend fun hoverAt(cellId: String, code: String, cursorOffset: Int): LanguageHover? =
    hover.at(uiState.value.cells, cellId, code, cursorOffset)

  /** @see NotebookSignatureHelp.at */
  suspend fun signatureHelpAt(cellId: String, code: String, cursorOffset: Int): LanguageSignatureHelp? =
    signatureHelp.at(uiState.value.cells, cellId, code, cursorOffset)

  /** @see NotebookDefinition.at */
  suspend fun definitionAt(cellId: String, code: String, cursorOffset: Int): DefinitionTarget? =
    definition.at(uiState.value.cells, cellId, code, cursorOffset)
}
