package com.jupydroid.app.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key

/**
 * Which notebook command a key chord means, as a table mirroring JupyterLab's.
 *
 * `packages/notebook-extension/schema/tracker.json` is a list of `(keys, selector, command)`
 * rows - JupyterLab never grows a `switch` over key names, because the selector is half the
 * answer: the same `Ctrl`+`Enter` means run in command mode and run in edit mode. Ours is the
 * same shape with [CellMode] standing in for the CSS selector, one row per binding.
 */
internal data class NotebookChord(
  val key: Key,
  val ctrl: Boolean = false,
  val alt: Boolean = false,
  val shift: Boolean = false,
  val meta: Boolean = false,
)

/**
 * What a resolved chord asks the notebook to do.
 *
 * [dropsCaret] is a property of the *command*, not of the dispatch site: `runAndAdvance` and
 * `runAndInsert` leave the editor in command mode before the caret moves, while [RunSelected]
 * is the one place the PRD deliberately departs from the reference - JupyterLab's `runSelected`
 * sets `notebook.mode = 'command'` first (`packages/notebook/src/actions.tsx:3020`), and the
 * classic notebook keeps editing. The demo specifies the classic behaviour: run in place.
 */
internal sealed interface NotebookChordIntent {
  val dropsCaret: Boolean

  /** `notebook:run` - `Accel Enter`. `dropsCaret = false`: the confirmed deviation above. */
  data object RunSelected : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `notebook:run-and-advance` - `Shift Enter`. */
  data object RunAndAdvance : NotebookChordIntent {
    override val dropsCaret = true
  }

  /** `notebook:run-and-insert` - `Alt Enter`. */
  data object RunAndInsert : NotebookChordIntent {
    override val dropsCaret = true
  }
}

/**
 * The edit-mode rows of `tracker.json` (`:1754-1773`), `Accel` read as `ctrl` since Android has
 * no command key - and `Ctrl` is what a chromebook's search-key- Enter chord produces, which is
 * what Jupyter's own docs call it.
 *
 * Command-mode rows belong to a later task; the table holds none yet and the resolver's
 * `mode == binding.mode` guard is what keeps that honest.
 */
private val NOTEBOOK_BINDINGS = listOf(
  NotebookBinding(
    "notebook:run",
    CellMode.EDIT,
    NotebookChord(Key.Enter, ctrl = true),
    NotebookChordIntent.RunSelected,
  ),
  NotebookBinding(
    "notebook:run-and-advance",
    CellMode.EDIT,
    NotebookChord(Key.Enter, shift = true),
    NotebookChordIntent.RunAndAdvance,
  ),
  NotebookBinding(
    "notebook:run-and-insert",
    CellMode.EDIT,
    NotebookChord(Key.Enter, alt = true),
    NotebookChordIntent.RunAndInsert,
  ),
)

/** One `tracker.json` row: the upstream command id is kept for greppability, not read. */
private class NotebookBinding(
  @Suppress("unused") val command: String,
  val mode: CellMode,
  val chord: NotebookChord,
  val intent: NotebookChordIntent,
)

/**
 * The first binding [chord] means in [mode], or null when the key is the editor's own.
 *
 * A chord matches exactly on all four modifiers, so `Ctrl`+`Shift`+`Enter` is not
 * `Ctrl`+`Enter` with a stray Shift - the same rule `KeyboardShortcut.getStrokes` applies
 * upstream. Key-up events never reach here; the `onPreviewKeyEvent` call site filters on type.
 */
internal fun notebookChord(mode: CellMode, chord: NotebookChord): NotebookChordIntent? {
  val key = if (chord.key == Key.NumPadEnter) Key.Enter else chord.key
  val normalized = chord.copy(key = key)
  return NOTEBOOK_BINDINGS.firstOrNull { it.mode == mode && it.chord == normalized }?.intent
}

/**
 * What a Compose [KeyEvent] holds, modifier flags included.
 *
 * `Key.NumPadEnter` stays distinct here - [notebookChord] owns the normalisation so a caller
 * reading the chord raw still sees which pad sent it.
 */
internal fun KeyEvent.chord(): NotebookChord = NotebookChord(
  key = key,
  ctrl = isCtrlPressed,
  alt = isAltPressed,
  shift = isShiftPressed,
  meta = isMetaPressed,
)
