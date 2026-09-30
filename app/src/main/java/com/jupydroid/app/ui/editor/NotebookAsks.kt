package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The notebook's position-asks - completion while typing, the "Symbol info" hover, the
 * signature of the call the caret is inside, and where that symbol is defined.
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
  private val format: NotebookFormat,
  private val rename: NotebookRename,
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

  /**
   * @see NotebookFormat.formatted
   *
   * The model is brought up to the field's text before the ask - `operated`'s Split rule -
   * so the apply's own gate ([NotebookCellEdits.cellFormatted]) can tell "still the asked
   * source" from "moved since". Only when the model is actually behind: `withSource` marks
   * the notebook dirty, and an ask alone must not.
   */
  suspend fun formatCell(cellId: String, code: String): String? {
    modelCaughtUpTo(cellId, code)
    return format.formatted(uiState.value.cells, cellId, code)
  }

  /**
   * @see NotebookRename.renamed
   *
   * The model is brought up to the field's text before the ask - [formatCell]'s own rule, the
   * same [modelCaughtUpTo] - so the apply's own gate ([NotebookCellEdits.cellsRenamed]'s
   * `askedSources`) can tell "still the asked sources" from "moved since".
   */
  suspend fun renameSymbol(cellId: String, code: String, cursorOffset: Int, newName: String): RenameAnswer? {
    modelCaughtUpTo(cellId, code)
    return rename.renamed(uiState.value.cells, cellId, code, cursorOffset, newName)
  }

  /**
   * `operated`'s Split rule, shared by the asks whose answers rewrite cells: the model is
   * brought up to the field's text first, but only when it is actually behind - `withSource`
   * marks the notebook dirty, and an ask alone must not.
   */
  private fun modelCaughtUpTo(cellId: String, code: String) {
    uiState.update { state ->
      val cell = state.cells.firstOrNull { it.id == cellId } ?: return@update state
      if (cell.source == code) state else state.withSource(cellId, code)
    }
  }
}
