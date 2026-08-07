package com.jupydroid.app.ui.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Holds a notebook's writes back until typing stops, and says whether one is still owed when the
 * screen closes.
 *
 * An object of its own rather than two fields on the ViewModel, which is the shape JupyterLab
 * uses: `SaveHandler` is constructed per document context, owns its own timer, and is disposed
 * with that context (`packages/docmanager/src/savehandler.ts:18-30`, `62-69`). Ours debounces
 * where theirs polls, because a phone's writes are cheap and its sessions are short.
 *
 * ```
 * private val save = NotebookSaveDebounce(viewModelScope) { persist() }
 * save.schedule()                       // after an edit
 * if (save.cancelPending()) persist()   // on the way out
 * ```
 */
class NotebookSaveDebounce(
  private val scope: CoroutineScope,
  private val write: suspend () -> Unit,
) {
  private var pending: Job? = null

  /** Restarts the clock, so a burst of typing costs one write rather than one per character. */
  fun schedule() {
    pending?.cancel()
    pending = scope.launch {
      delay(DEBOUNCE_MILLIS)
      write()
    }
  }

  /**
   * Cancels the write that has not happened yet and reports whether there was one.
   *
   * Both halves matter to a caller that is closing the screen: it has to know whether anything
   * is owed - writing regardless would make merely opening a notebook count as an update - and
   * it has to stop the scheduled one, or the flush and the debounce both write.
   */
  fun cancelPending(): Boolean {
    val owed = pending?.isActive == true
    pending?.cancel()
    return owed
  }

  private companion object {
    /** Long enough that a burst of typing is one write, short enough to feel immediate. */
    const val DEBOUNCE_MILLIS = 500L
  }
}
