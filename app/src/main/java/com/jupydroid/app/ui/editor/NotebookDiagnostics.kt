package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.PublishDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * `publishDiagnostics` as per-cell marks on [EditorUiState] - `docs/PLAN.md` P5d-2b, the
 * JupyterLab lint gutter's own data path cut to what the notebook's gutter draws.
 *
 * Two feeds converge here. The first keeps [NotebookLanguageDocument] synced with the cells:
 * every distinct `(cells, kernelConnected)` state while a kernel session exists is a
 * whole-document `didChange` - the reference's own `sendFullTextChange` cadence, and the same
 * gate [NotebookCompletion] keeps, so watching never boots a runtime either. The second maps
 * each publication's ranges back through [NotebookLanguageDocument.synced] - the last text the
 * server actually saw - into the cells and lines [diagnosticsByCellId] names.
 *
 * ```
 * NotebookDiagnostics(uiState, languageServer, document, scope).start()
 * ```
 */
class NotebookDiagnostics(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val languageServer: LanguageServerPort,
  private val document: NotebookLanguageDocument,
  private val scope: CoroutineScope,
) {
  /** Both collectors, on the caller's scope - the editor's own lifetime ends them. */
  fun start() {
    scope.launch { keepSynced() }
    scope.launch { relayPublishes() }
  }

  /**
   * The document follows the cells while a kernel session exists. [kotlinx.coroutines.flow.collectLatest]
   * because only the newest text is worth a `didChange`: the sync in flight when the next
   * keystroke lands is already stale, and LSP versions only count up. A sync that fails -
   * a dead `pylsp`, a dying channel - leaves the last mirror standing rather than the watch.
   */
  @Suppress("SwallowedException", "TooGenericExceptionCaught")
  private suspend fun keepSynced() {
    uiState
      .map { it.cells to it.kernelConnected }
      .distinctUntilChanged()
      .collectLatest { (cells, connected) ->
        if (connected) runCatching { document.sync(cells) }
      }
  }

  /**
   * Each publication replaces [diagnosticsByCellId] whole - `publishDiagnostics` is itself the
   * server's whole set for that document, an empty list included. A publication for a uri this
   * notebook does not own, or for a range that lands on a separator line, is dropped rather
   * than landed on the wrong cell.
   */
  private suspend fun relayPublishes() {
    languageServer.diagnostics.collect { pushed ->
      val synced = document.synced.value ?: return@collect
      if (pushed.uri != synced.uri) return@collect
      uiState.update { it.copy(diagnosticsByCellId = pushed.toCellLines(synced)) }
    }
  }
}

/**
 * The publication's ranges as the per-cell marks [EditorUiState.diagnosticsByCellId] stores:
 * a diagnostic marks the one-based line its range starts on, CodeMirror's own convention for
 * where a lint gutter icon lands on a multi-line range.
 */
private fun PublishDiagnostics.toCellLines(document: NotebookVirtualDocument): Map<String, List<CellDiagnostic>> =
  diagnostics.mapNotNull { diagnostic ->
    document.cellLineAt(diagnostic.range.start.line)?.let { cellLine ->
      val start = document.cellOffsetOf(cellLine.cellId, diagnostic.range.start)
        ?: return@mapNotNull null
      cellLine.cellId to
        CellDiagnostic(
          line = cellLine.line + 1,
          start = start,
          end = document.cellOffsetOfClamped(cellLine.cellId, diagnostic.range.end)
            ?.coerceAtLeast(start) ?: start,
          severity = diagnostic.severity,
          code = diagnostic.code,
          source = diagnostic.source,
          message = diagnostic.message,
        )
    }
  }.groupBy({ it.first }, { it.second })
