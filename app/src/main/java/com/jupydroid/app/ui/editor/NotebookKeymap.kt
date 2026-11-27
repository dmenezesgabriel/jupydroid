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

  /**
   * `notebook:enter-edit-mode` - `Enter` on the cell. `dropsCaret = false`: the intent *places*
   * a caret ask; the mode it opens is the focus event's, not a flag here.
   */
  data object EnterEditMode : NotebookChordIntent {
    override val dropsCaret = false
  }

  /**
   * `notebook:enter-command-mode` - `Escape`, or `Ctrl M`. `dropsCaret = false`: the dispatch
   * site moves focus onto the cell's frame, which destroys the field and the caret with it - no
   * caret ask ever moves.
   */
  data object EnterCommandMode : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `notebook:move-cursor-up` - `ArrowUp`, `K`. */
  data object SelectAbove : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `notebook:move-cursor-down` - `ArrowDown`, `J`. */
  data object SelectBelow : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `notebook:move-cursor-heading-above-or-collapse` - `ArrowLeft`. */
  data object SelectHeadingAboveOrCollapse : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `notebook:move-cursor-heading-below-or-expand` - `ArrowRight`. */
  data object SelectHeadingBelowOrExpand : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** The four `notebook:extend-marked-cells-*` commands - `Shift`+arrows/`J`/`K`/`Home`/`End`. */
  data class ExtendMarked(val reach: SelectionReach) : NotebookChordIntent {
    override val dropsCaret = false
  }
}

/**
 * `tracker.json`, `Accel` read as `ctrl` since Android has no command key - and `Ctrl` is what a
 * chromebook's search-key-Enter chord produces, which is what Jupyter's own docs call it.
 *
 * The two selectors upstream calls `commandMode` and `editing` are [CellMode]s here. `Enter`
 * upstream is scoped to `.jp-Cell:focus` rather than every descendant (`tracker.json:447`), which
 * is a fidelity the dispatch site - not this table - reproduces, on the focus-owning check it
 * keeps for the row.
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
  NotebookBinding(
    "notebook:enter-edit-mode",
    CellMode.COMMAND,
    NotebookChord(Key.Enter),
    NotebookChordIntent.EnterEditMode,
  ),
  NotebookBinding(
    "notebook:enter-command-mode",
    CellMode.EDIT,
    NotebookChord(Key.Escape),
    NotebookChordIntent.EnterCommandMode,
  ),
  NotebookBinding(
    "notebook:enter-command-mode",
    CellMode.EDIT,
    NotebookChord(Key.M, ctrl = true),
    NotebookChordIntent.EnterCommandMode,
  ),
  NotebookBinding(
    "notebook:move-cursor-up",
    CellMode.COMMAND,
    NotebookChord(Key.DirectionUp),
    NotebookChordIntent.SelectAbove,
  ),
  NotebookBinding(
    "notebook:move-cursor-up",
    CellMode.COMMAND,
    NotebookChord(Key.K),
    NotebookChordIntent.SelectAbove,
  ),
  NotebookBinding(
    "notebook:move-cursor-down",
    CellMode.COMMAND,
    NotebookChord(Key.DirectionDown),
    NotebookChordIntent.SelectBelow,
  ),
  NotebookBinding(
    "notebook:move-cursor-down",
    CellMode.COMMAND,
    NotebookChord(Key.J),
    NotebookChordIntent.SelectBelow,
  ),
  NotebookBinding(
    "notebook:move-cursor-heading-above-or-collapse",
    CellMode.COMMAND,
    NotebookChord(Key.DirectionLeft),
    NotebookChordIntent.SelectHeadingAboveOrCollapse,
  ),
  NotebookBinding(
    "notebook:move-cursor-heading-below-or-expand",
    CellMode.COMMAND,
    NotebookChord(Key.DirectionRight),
    NotebookChordIntent.SelectHeadingBelowOrExpand,
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-above",
    CellMode.COMMAND,
    NotebookChord(Key.DirectionUp, shift = true),
    NotebookChordIntent.ExtendMarked(SelectionReach.ABOVE),
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-above",
    CellMode.COMMAND,
    NotebookChord(Key.K, shift = true),
    NotebookChordIntent.ExtendMarked(SelectionReach.ABOVE),
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-below",
    CellMode.COMMAND,
    NotebookChord(Key.DirectionDown, shift = true),
    NotebookChordIntent.ExtendMarked(SelectionReach.BELOW),
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-below",
    CellMode.COMMAND,
    NotebookChord(Key.J, shift = true),
    NotebookChordIntent.ExtendMarked(SelectionReach.BELOW),
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-top",
    CellMode.COMMAND,
    NotebookChord(Key.MoveHome, shift = true),
    NotebookChordIntent.ExtendMarked(SelectionReach.TOP),
  ),
  NotebookBinding(
    "notebook:extend-marked-cells-bottom",
    CellMode.COMMAND,
    NotebookChord(Key.MoveEnd, shift = true),
    NotebookChordIntent.ExtendMarked(SelectionReach.BOTTOM),
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
