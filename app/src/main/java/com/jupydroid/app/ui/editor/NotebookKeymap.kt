package com.jupydroid.app.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import com.jupydroid.app.domain.model.CellKind

/**
 * Which notebook command a key chord means, as a table mirroring JupyterLab's.
 *
 * `packages/notebook-extension/schema/tracker.json` is a list of `(keys, selector, command)`
 * rows - JupyterLab never grows a `switch` over key names, because the selector is half the
 * answer: the same `Ctrl`+`Enter` means run in command mode and run in edit mode. Ours is the
 * same shape, [CellMode] and [NotebookChordSelector] standing in for the CSS selector, one row
 * per binding - the rows live in `NotebookChordTable.kt`.
 */
internal data class NotebookChord(
  val key: Key,
  val ctrl: Boolean = false,
  val alt: Boolean = false,
  val shift: Boolean = false,
  val meta: Boolean = false,
) {
  /** Keyboards split Enter into two keycodes; the table keys on the key, not the pad that sent it. */
  fun normalized(): NotebookChord = if (key == Key.NumPadEnter) copy(key = Key.Enter) else this
}

/**
 * The half of a row's selector that is not the mode: which node the CSS `:focus` lands on.
 *
 * `NOTEBOOK_FOCUS` is `:focus` on any descendant of the mode-qualified notebook - the command
 * runs whoever inside the notebook owns focus. `CELL_FOCUS` is `.jp-Cell:focus`, and
 * `MARKDOWN_CELL_FOCUS` is `.jp-MarkdownCell:focus`: the cell node *itself*, never a
 * descendant control (a `⋮` inside it keeps its own keys).
 */
internal enum class NotebookChordSelector {
  NOTEBOOK_FOCUS,
  CELL_FOCUS,
  MARKDOWN_CELL_FOCUS,
}

/**
 * What a row's selector evaluates against - the focused node's shape as the dispatch site
 * computes it.
 *
 * [cellFocused] is `.jp-Cell:focus` as a flag: whether the head cell's *frame* owns focus rather
 * than one of its descendants. [headKind] is the focused cell's kind, because the head cell is
 * the only focusable frame in command mode - `.jp-MarkdownCell:focus` can only ever be met on it.
 */
internal class NotebookKeyContext(val mode: CellMode, val cellFocused: Boolean, val headKind: CellKind?) {
  fun selects(selector: NotebookChordSelector): Boolean = when (selector) {
    NotebookChordSelector.NOTEBOOK_FOCUS -> true
    NotebookChordSelector.CELL_FOCUS -> cellFocused
    NotebookChordSelector.MARKDOWN_CELL_FOCUS -> cellFocused && headKind == CellKind.MARKDOWN
  }
}

/**
 * Lumino's `matchKeystroke` answer (`packages/apputils/src/keybinding.ts`): a stroke either
 * completes a binding, opens one, or matches nothing.
 *
 * [Pending] exists because `keys` upstream is a *list* - `notebook:delete-cell` is `["D","D"]`
 * (`tracker.json:390-395`), so the first `D` is a match-in-progress rather than a command or a
 * miss.
 */
internal sealed interface NotebookChordResolution {
  /** The stroke completed a row's `keys` - [pending] prefixed it when a sequence was open. */
  data class Bound(val intent: NotebookChordIntent) : NotebookChordResolution

  /** The stroke is the first key of a row's `keys`; the next one completes or resets it. */
  data object Pending : NotebookChordResolution

  /** No row wants the stroke - it is the field's or the platform's own key. */
  data object Unbound : NotebookChordResolution
}

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

  /**
   * The letter rows that are the cell menu's own verbs - `notebook:insert-cell-above`,
   * `delete-cell`, `change-cell-to-*`, `{copy,cut,paste}-cell`, `merge-cell*`, `move-cell-*`,
   * `insert-heading-*`, `change-cell-to-heading-*` and `split-cell-at-cursor` - carried as the
   * [CellAction] the menu already dispatches, so the chord asks for what the menu asks for.
   */
  data class ApplyCellAction(val action: CellAction) : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `notebook:undo-cell-action` - `Z`. */
  data object UndoCellAction : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `notebook:redo-cell-action` - `Shift Z`. */
  data object RedoCellAction : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `notebook:select-all` - `Accel A`. */
  data object SelectAllCells : NotebookChordIntent {
    override val dropsCaret = false
  }

  /**
   * `notebook:collapse-all-headings`/`expand-all-headings` - `Ctrl Shift`+`ArrowLeft`/`ArrowRight`,
   * two commands as one parameter.
   */
  data class SetAllHeadingsCollapsed(val collapsed: Boolean) : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `viewmenu:line-numbering` - `Shift L`. */
  data object ToggleAllCellLineNumbers : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `docmanager:save` - `Accel S`, bound on `body` upstream, which is why both modes carry it. */
  data object SaveDocument : NotebookChordIntent {
    override val dropsCaret = false
  }

  /**
   * `docmanager:save-as` - `Accel Shift S`. Its dialog is the shell's, not the notebook's, so the
   * intent climbs to the screen like [EnterCommandMode] climbs to the dispatch site.
   */
  data object SaveDocumentAs : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `notebook:access-previous-history-entry` - `Alt ArrowUp`. */
  data object RecallPreviousHistoryEntry : NotebookChordIntent {
    override val dropsCaret = false
  }

  /** `notebook:access-next-history-entry` - `Alt ArrowDown`. */
  data object RecallNextHistoryEntry : NotebookChordIntent {
    override val dropsCaret = false
  }
}

/**
 * The first binding [chord] means in [context]'s mode and focus, Lumino's `matchKeystroke` read:
 * `[pending, chord]` against every `keys` first, then `chord` alone as a complete binding, then
 * `chord` as the first key of a longer one. A miss while a sequence is open resolves the stroke
 * fresh - upstream drops the half-matched keystrokes and retries, which is what makes `D` then
 * `X` a cut rather than a dead key.
 *
 * A chord matches exactly on all four modifiers, so `Ctrl`+`Shift`+`Enter` is not
 * `Ctrl`+`Enter` with a stray Shift - the same rule `KeyboardShortcut.getStrokes` applies
 * upstream. Key-up events never reach here; the `onPreviewKeyEvent` call site filters on type.
 */
internal fun notebookChord(
  context: NotebookKeyContext,
  chord: NotebookChord,
  pending: NotebookChord? = null,
): NotebookChordResolution {
  val stroke = chord.normalized()
  val sequence = pending?.normalized()?.let { listOf(it, stroke) }
  val rows = NOTEBOOK_BINDINGS.filter { it.mode == context.mode && context.selects(it.selector) }
  val bound = sequence?.let { seq -> rows.firstOrNull { it.keys == seq } }
    ?: rows.firstOrNull { it.keys == listOf(stroke) }
  return bound?.let { NotebookChordResolution.Bound(it.intent) }
    ?: if (rows.any { it.keys.size > 1 && it.keys.first() == stroke }) {
      NotebookChordResolution.Pending
    } else {
      NotebookChordResolution.Unbound
    }
}

/**
 * What a Compose [KeyEvent] holds, modifier flags included.
 *
 * `Key.NumPadEnter` stays distinct here - [NotebookChord.normalized] owns the normalisation so a
 * caller reading the chord raw still sees which pad sent it.
 */
internal fun KeyEvent.chord(): NotebookChord = NotebookChord(
  key = key,
  ctrl = isCtrlPressed,
  alt = isAltPressed,
  shift = isShiftPressed,
  meta = isMetaPressed,
)
