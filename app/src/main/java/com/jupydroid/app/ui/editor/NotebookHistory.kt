package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.port.KernelGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The cell being edited, walking through what the kernel has run -
 * `notebook:access-previous-history-entry` and `notebook:access-next-history-entry`.
 *
 * Beside [NotebookCellRuns] under [NotebookKernel] and for its reason: this needs the session and
 * nothing else the notebook holds, and it has state of its own - a cursor, a batch size and the
 * text it last wrote - that no other part of the kernel wants. Unlike the runs it is **exposed**
 * rather than delegated through, because three more methods on [NotebookKernel] is a class about
 * two things; "the notebook's kernel" is still the one handle a screen holds to reach it.
 *
 * **It never opens a kernel**, which is [NotebookKernel.completionsAt]'s rule applied to the other
 * read-only ask on the socket: tapping a key while typing must not boot a Python runtime - seconds
 * and ~143 MB - that the user never asked for by running a cell.
 *
 * ```
 * kernel.history.recallPrevious(placeholder = field.text) { entry -> input.replaceAll(entry) }
 * ```
 */
class NotebookHistory(
  private val session: NotebookKernelSession,
  private val kernelGateway: KernelGateway,
  private val scope: CoroutineScope,
) {
  /** The walk in progress, or null when the next tap starts one - the reference's `_hasSession`. */
  private var cursor: NotebookHistoryCursor? = null

  /** The tail asked for so far, so a longer one can be told from the same one arriving again. */
  private var entries: List<String> = emptyList()
  private var batch = FIRST_BATCH

  /**
   * The text this object last put in the cell - the reference's `_setByHistory` (`history.ts:375`)
   * as a value rather than a flag.
   *
   * A flag would be enough for the reference, whose editor reports synchronously. `CodeCellField`
   * reports on a 150 ms debounce, so the edit caused by a write can arrive after the *next* tap
   * has been made; comparing the text is what stays true whenever it lands.
   */
  private var wrote: String? = null

  /**
   * Puts the previous entry in the cell - one step older per tap, oldest last.
   *
   * [placeholder] is what the cell holds **now**, and it is read from the field rather than from
   * the notebook for [CellEditorText]'s reason: the caret and the freshest text belong to the
   * field, and an edit reaches the model on a debounce. It is used only when a session opens; a
   * walk already in progress keeps the placeholder it started with, as the reference's does.
   *
   * [into] is called with the entry, and not called at all when there is no kernel to ask.
   */
  fun recallPrevious(placeholder: String, into: (String) -> Unit) = walk(placeholder, into) { at ->
    // `--cursor`, then a longer tail if that ran off the start (`history.ts:181-184`).
    //
    // **No second `back()` after the fetch**, which is what this loop got wrong when it was one:
    // `fetchBatch` re-seats the cursor *one entry older than the oldest it had* - that is what the
    // `+1` in its own arithmetic is for (`:265-266`) - so stepping again walked past an entry
    // every time a batch ran out.
    if (!at.back()) fetchLongerTail(at)
  }

  /** Puts the next entry in the cell, and what was being typed once the walk reaches its end. */
  fun recallNext(placeholder: String, into: (String) -> Unit) = walk(placeholder, into) { it.forward() }

  /**
   * Ends the walk in progress, so the next tap starts a new one against the cell as it is now.
   *
   * Called when the caret leaves the cell. A **narrowing** of the reference, which binds its
   * editor once and keeps the session across a cell change (`history.ts:160-170`) - so walking
   * forward to the end there writes the first cell's placeholder into whichever cell has the caret
   * now. The placeholder belongs to a cell.
   */
  fun reset() {
    cursor = null
    entries = emptyList()
    batch = FIRST_BATCH
    wrote = null
  }

  /**
   * The cell reported its text - `onTextChange` (`history.ts:313-319`).
   *
   * Ends the walk, **unless this is the edit the walk itself made**. Without that guard every tap
   * would be the first one and the cursor could never leave the newest entry.
   */
  fun sourceReported(source: String) {
    if (source == wrote) return
    reset()
  }

  /**
   * Opens a session if there is none, moves the cursor with [step], and hands the entry to [into].
   *
   * A failure is silence rather than a crash, which is [NotebookKernel.completionsAt]'s rule: a
   * socket dying is worth reporting while a cell runs and worth ignoring while somebody types.
   */
  @Suppress("SwallowedException") // a kernel that cannot say what it ran has nothing to offer, which is not an error
  private fun walk(placeholder: String, into: (String) -> Unit, step: suspend (NotebookHistoryCursor) -> Unit) {
    scope.launch {
      try {
        val at = openedCursor(placeholder) ?: return@launch
        step(at)
        wrote = at.current
        into(at.current)
      } catch (unreachable: IllegalStateException) {
        reset()
      }
    }
  }

  /** The walk in progress, or a new one over a fresh tail - and null when no kernel is open. */
  private suspend fun openedCursor(placeholder: String): NotebookHistoryCursor? =
    cursor ?: session.opened?.let { open ->
      batch = FIRST_BATCH
      entries = kernelGateway.executionHistory(open, batch)
      NotebookHistoryCursor(entries, placeholder).also { cursor = it }
    }

  /**
   * Asks the kernel for a longer tail and re-seats the cursor in it - `fetchBatch`
   * (`history.ts:247-273`), recursion included.
   *
   * Two different reasons to stop, and the reference distinguishes them: a tail that grew by
   * nothing means the kernel has no more to give, and a re-seating that still lands short of the
   * list means this batch was not enough - `if (this._cursor < 0)` guarded by
   * `_history.length > oldHistory.length` (`:268-272`). Only the second is worth another ask.
   *
   * It asks again on **every** overshoot, including one at an oldest entry it has already reached.
   * That is the reference's behaviour rather than an oversight here: a kernel with nothing more a
   * moment ago may have run something since, and remembering otherwise would be inventing state
   * the reference does not keep.
   */
  private suspend fun fetchLongerTail(at: NotebookHistoryCursor) {
    val open = session.opened ?: return
    batch += FIRST_BATCH
    val longer = kernelGateway.executionHistory(open, batch)
    if (longer.size <= entries.size) return
    entries = longer
    if (!at.grownTo(longer)) fetchLongerTail(at)
  }

  private companion object {
    /** `_requestBatchSize` (`history.ts:99`), and the step it grows by (`:248`). */
    const val FIRST_BATCH = 10
  }
}
