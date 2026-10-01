package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A document's save machinery: the debounce `NotebookSaveDebounce` always was, plus the periodic
 * write-if-dirty poll that is JupyterLab's `SaveHandler` itself, both gated on the `autosave`
 * and `autosaveInterval` settings (`docmanager-extension/schema/plugin.json`).
 *
 * **The poll waits for dirt rather than ticking blind.** JupyterLab re-arms its timer
 * unconditionally, which a real wall-clock scheduler can afford; on the test scheduler an
 * endless delay chain never lets `advanceUntilIdle` return, and a failed write leaves the
 * document dirty for ever - the same infinite loop. So the poll suspends on the *next edit*
 * ([editArrived], which only [schedule] feeds) and only then arms the interval. The observable
 * behaviour is the reference's: nothing queued on a clean document, a write at most one interval
 * stale while typing never stops, and a failed write retried when work continues - locally a
 * write failure is a filesystem fact, and a retry with identical bytes fails identically, so
 * waiting for the next edit loses nothing the reference's timer would have recovered.
 *
 * ```
 * private val save = DocumentAutosave(viewModelScope, settings, { uiState.value.dirty }) { persist() }
 * save.schedule()                       // after an edit
 * if (save.cancelPending()) persist()   // on the way out
 * save.discard()                        // the close dialog's Discard - never write again
 * ```
 */
class DocumentAutosave(
  private val scope: CoroutineScope,
  private val settings: StateFlow<EditorSettings>,
  private val isDirty: () -> Boolean,
  private val write: suspend () -> Unit,
) {
  private var pending: Job? = null

  /**
   * The "an edit arrived" flag the poll sleeps on - a state, not an event, because a `SharedFlow`
   * emit would be lost when nobody is collecting yet (an edit can precede the collector's first
   * run), and because a burst of typing must coalesce into one armed tick, not one per keystroke.
   * The poll clears it when it arms the interval, so a failed write waits for the *next* edit.
   */
  private val editArrived = MutableStateFlow(false)

  /** Set by [discard]: the document is being thrown away, so nothing ever writes again. */
  var discarded = false
    private set

  init {
    scope.launch {
      settings.collectLatest { current ->
        while (current.autosaveEnabled && !discarded) {
          editArrived.first { it }
          editArrived.value = false
          delay(current.autosaveIntervalSeconds * MILLIS_PER_SECOND)
          if (!discarded && isDirty()) launchWrite()
        }
      }
    }
  }

  /**
   * Restarts the debounce, so a burst of typing costs one write rather than one per character,
   * and tells the poll dirt arrived. A no-op when autosave is off - `SaveHandler` is never
   * started for the document at all.
   */
  fun schedule() {
    if (discarded || !settings.value.autosaveEnabled) return
    editArrived.value = true
    pending?.cancel()
    pending = scope.launch {
      delay(DEBOUNCE_MILLIS)
      if (settings.value.autosaveEnabled && !discarded) launchWrite()
    }
  }

  /**
   * The write runs *beside* the debounce, never inside it: `pending?.cancel()` is how the next
   * edit restarts the delay, and it must not reach a write already suspended on the disk. The
   * poll goes through here for the same reason - a settings change restarts `collectLatest`,
   * which would otherwise kill a write mid-flight.
   */
  private fun launchWrite() {
    scope.launch { attemptWrite() }
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

  /** Writes now - the close dialog's "Save". The debounce is cancelled so the write happens once. */
  suspend fun flush() {
    if (discarded) return
    cancelPending()
    attemptWrite()
  }

  /** `SaveHandler.dispose()` - pending work is dropped and nothing is scheduled again. */
  fun discard() {
    discarded = true
    pending?.cancel()
    pending = null
  }

  /**
   * Runs [write] and keeps going if it raises - `SaveHandler._save` catches and re-arms; an
   * uncaught exception inside a launched coroutine takes the process down here. The write
   * reports failures through state (`saveFailure`), so a catch here loses nothing.
   */
  private suspend fun attemptWrite() {
    try {
      write()
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
    }
  }

  private companion object {
    /** Long enough that a burst of typing is one write, short enough to feel immediate. */
    const val DEBOUNCE_MILLIS = 500L
    const val MILLIS_PER_SECOND = 1_000L
  }
}
