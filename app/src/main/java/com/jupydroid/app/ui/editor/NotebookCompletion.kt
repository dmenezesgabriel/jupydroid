package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.port.CompletionMatch
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.LanguageCompletion
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's ask for what fits under the cursor: the Space's language server first, the kernel
 * only when no server is running - `docs/PLAN.md` P5c, behind the same [CompletionResult] the
 * kernel path always produced, so the popup and [applyCompletion] do not change.
 *
 * The session gate is the rule this ask always had: a notebook that never ran a cell never
 * asked for a Space, and typing there must not boot one. Once the kernel is open the Space is
 * already paid for, so the ask may lazily spawn `pylsp` - which is also when the kernel is
 * there to fall back on if the server cannot answer. The reference merges every completer's
 * rows into one model (`ICompletionProviderManager`); the plan's wording is the narrower one
 * taken here - the kernel is a fallback, not a second source.
 *
 * ```
 * completion.at(cell.id, cell.source, cursorOffset)
 * ```
 */
class NotebookCompletion(
  private val session: NotebookKernelSession,
  private val kernelGateway: KernelGateway,
  private val languageServer: LanguageServerPort,
  /**
   * The notebook's one document under the language server, shared with the diagnostics watch -
   * P5d-2b - so a `publishDiagnostics` range always maps against what the last sync sent.
   */
  private val document: NotebookLanguageDocument,
) {
  /**
   * What is offered for [code] with the caret at [cursorOffset] in the cell [cellId] names.
   *
   * The ask goes to one virtual document per notebook (P5d-1): [cells] with [cellId]'s source
   * substituted for [code], the field's live text, which `uiState` may still hold stale. It is
   * synced whole on every ask - [LanguageServerPort.changeDocument] opens it on the first one,
   * the reference's `_sendChange` rule - and the caret is asked at its virtual position, so
   * `import os` in an earlier cell is what makes `os.path.jo` answerable in this one.
   */
  @Suppress("SwallowedException") // a kernel that cannot answer has nothing to say, which is not an error
  suspend fun at(cells: List<Cell>, cellId: String, code: String, cursorOffset: Int): CompletionResult {
    val open = session.opened ?: return CompletionResult.NONE
    return viaLanguageServer(cells, cellId, code, cursorOffset)
      ?: try {
        kernelGateway.complete(open, code, cursorOffset)
      } catch (e: IllegalStateException) {
        CompletionResult.NONE
      }
  }

  /**
   * The language server's answer, or null when there is no answer to give - a `pylsp` that
   * cannot start, a socket that dies mid-request, a reply that never comes. Each is worth the
   * kernel's turn and none is worth a crash, which is the broad catch; a cancelled ask is not
   * a failure and is rethrown, because `collectLatest` cancels on the next keystroke.
   */
  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  private suspend fun viaLanguageServer(
    cells: List<Cell>,
    cellId: String,
    code: String,
    cursorOffset: Int,
  ): CompletionResult? = try {
    val synced = document.sync(cells.withLiveSource(cellId, code))
    val position = synced.positionOf(cellId, cursorOffset) ?: return null
    toCompletionResult(
      synced,
      cellId,
      languageServer.completion(BASE_SPACE, synced.uri, position),
      code,
      cursorOffset,
    )
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (failed: Exception) {
    null
  }
}

/**
 * [cells] with [cellId]'s source replaced by the field's live [code] - the truth of the text.
 *
 * Internal rather than private because every caret-driven ask needs it: [NotebookHover] must
 * not sync `uiState`'s up-to-a-debounce-old copy either.
 */
internal fun List<Cell>.withLiveSource(cellId: String, code: String) =
  map { if (it.id == cellId) it.copy(source = code) else it }

/**
 * LSP items as the one [CompletionResult] the popup knows.
 *
 * A row's insert is `textEdit.newText`, else `insertText` - not the `label` it was shown
 * under, because those differ exactly when the label would splice wrong (`np.ar` completing
 * to `arange` must not insert `np.` again; `join(path, paths)` must not type its parameter
 * list). Row order is the server's `sortText` before the label, `compareItems`' own rule.
 * The replaced span is the edit's own range when an item carries one, and the identifier
 * under the cursor when it does not - the word range the reference computes for an edit-less
 * item.
 */
private fun toCompletionResult(
  document: NotebookVirtualDocument,
  cellId: String,
  items: List<LanguageCompletion>,
  code: String,
  cursorOffset: Int,
): CompletionResult {
  val matches = items
    .sortedBy { it.sortText ?: it.label }
    .distinctBy { it.edit?.newText ?: it.insertText }
    .map { CompletionMatch(insert = it.edit?.newText ?: it.insertText, label = it.label, kind = it.kind) }
  if (matches.isEmpty()) return CompletionResult.NONE
  val edit = items.firstNotNullOfOrNull { it.edit }
  val start = edit?.let { document.cellOffsetOf(cellId, it.range.start) }
  val end = edit?.let { document.cellOffsetOf(cellId, it.range.end) }
  val (replacedStart, replacedEnd) =
    if (start != null && end != null) {
      start to end
    } else {
      identifierSpanEndingAt(code, cursorOffset) ?: (cursorOffset to cursorOffset)
    }
  return CompletionResult(matches, replacedStart, replacedEnd)
}
