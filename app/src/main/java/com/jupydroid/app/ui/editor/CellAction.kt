package com.jupydroid.app.ui.editor

import androidx.compose.ui.text.TextRange
import com.jupydroid.app.domain.model.CellKind

/** Which way a cell is being moved past its neighbour. */
enum class CellMove { UP, DOWN }

/** Which neighbour a merge folds into. */
enum class MergeInto { ABOVE, BELOW }

/**
 * How far [withSelectionExtended] moves the head of a selected run - the four
 * `notebook:extend-marked-cells-*` commands, as one parameter.
 *
 * Beside [CellMove] and [MergeInto] because it is the same kind of thing: the vocabulary a command
 * is named with, kept apart from both the surface that offers it and the transform that carries it
 * out.
 */
enum class SelectionReach { ABOVE, BELOW, TOP, BOTTOM }

/**
 * Which cells a run command means - the five ranges [cellsToRun] resolves, as one parameter.
 *
 * Beside [SelectionReach] for the same reason it is beside [CellMove]: this is the vocabulary the
 * run commands are named with, kept apart from the bar that offers them and the queue that works
 * through them.
 *
 * [ABOVE] excludes the head and [BELOW] includes it. That asymmetry is the reference's, not a
 * choice - see [cellsToRun], where each range carries the line it comes from.
 */
enum class RunRange { ALL, ABOVE, BELOW, UP_TO_SELECTED, MARKED }

/**
 * Which end of the current **section** a new heading goes at - `notebook:insert-heading-{above,below}`
 * as one parameter.
 *
 * Beside [CellMove] and [SelectionReach] for their reason: the vocabulary a command is named with,
 * kept apart from the surface that offers it and the transform that carries it out.
 *
 * [BELOW] is not "after this cell". `insertSameLevelHeadingBelow` inserts at
 * `findLowerEqualLevelHeadingBelow` (`packages/notebook/src/actions.tsx:1208-1231`), which is the
 * *next sibling section* - so the new heading closes the section it was asked from rather than
 * cutting it in half. See [withHeadingInserted].
 */
enum class SectionReach { ABOVE, BELOW }

/**
 * What the long-press menu can do to one cell.
 *
 * The menu's vocabulary, kept apart from both the menu that shows it and the [CellEdits] that
 * carry it out - so neither has to know about the other.
 */
sealed interface CellAction {
  /** Below the target, which is what `notebook:insert-cell-below` (`B`) does. */
  data class Insert(val kind: CellKind) : CellAction

  /** Above it - `notebook:insert-cell-above` (`A`). */
  data class InsertAbove(val kind: CellKind) : CellAction

  /** Code to markdown or back. Keeps the cell's id, drops its outputs. */
  data class ChangeKind(val kind: CellKind) : CellAction

  /**
   * `notebook:change-cell-to-heading-1..6` - the six commands as one parameter.
   *
   * A change of type *and* a rewrite of the source: `setMarkdownHeader` is
   * `changeCellType(notebook, 'markdown', { headingLevel })`
   * (`packages/notebook/src/actions.tsx:2197-2214`), so [ChangeKind]'s rules all apply and the
   * first line is rewritten on top of them.
   */
  data class ChangeToHeading(val level: Int) : CellAction

  /**
   * Asks for the H1-H6 picker rather than changing anything - the row that stands for the six.
   *
   * A [CellAction] although it edits nothing, for [StartSelecting]'s reason: it is what carries the
   * row to every surface without a second code path. Answered by the surface that raised it and
   * never by [CellEdits], which is why the reference has no equivalent - six palette entries need
   * no row to open them.
   */
  data object ChooseHeadingLevel : CellAction

  /**
   * `notebook:insert-heading-{above,below}` - a new heading at the same level as the section this
   * cell is in.
   *
   * Apart from [Insert] and [InsertAbove], which put an *empty* cell of a chosen type next to this
   * one: this one derives its level from the document's structure and lands at the end of a
   * section rather than beside a cell. See [SectionReach] and [withHeadingInserted].
   */
  data class InsertHeading(val reach: SectionReach) : CellAction

  /** Fold this cell together with a neighbour. */
  data class Merge(val into: MergeInto) : CellAction

  /**
   * Cut this cell in two where the caret is - `notebook:split-cell-at-cursor`, and merge's
   * inverse.
   *
   * Carries nothing, unlike [Merge]: where to cut is [CellEditorText], which arrives beside the
   * action rather than inside it. The menu names an action and cannot see a caret - and an item
   * carrying one would be rebuilt on every keystroke, which is what the accessibility surface
   * remembers these against to avoid.
   */
  data object Split : CellAction

  /**
   * Begins marking a run of cells at this one - the touch way into multi-select.
   *
   * The reference has no equivalent because it needs none: there, `Shift`+click extends a
   * selection and there is nothing to *enter*. A long press is `BasicTextField`'s text selection
   * and is not available to us (`docs/DECISIONS.md`), so the way in is a visible control, and the
   * menu behind the `⋮` is where the cell already keeps every one of those.
   *
   * A [CellAction] although it edits nothing, because that is what carries it to all three
   * surfaces - the dropdown, the sheet and the cell's accessibility context menu - without a
   * fourth code path. [Copy] is the same shape and has been since U5a.
   */
  data object StartSelecting : CellAction

  data object Copy : CellAction

  data object Cut : CellAction

  /** Drops the clipboard below this cell - `notebook:paste-cell-below` (`V`). */
  data object Paste : CellAction

  data class Move(val move: CellMove) : CellAction

  data object Duplicate : CellAction

  data object Delete : CellAction

  data object ClearOutputs : CellAction

  /**
   * The three that change only **what is drawn**, and nothing the notebook holds.
   *
   * A category this codebase already names - it is [CellFolds]'s whole docstring, *"a fold discards
   * nothing"* - given a type so the two exhaustive `when`s over [CellAction] can say it once. They
   * are also the three that read the head's own state and set the run to its opposite, which is why
   * they dispatch together.
   */
  sealed interface Fold : CellAction

  /** Collapse or expand the cell's source. Code cells only, as in JupyterLab. */
  data object ToggleSourceHidden : Fold

  /** Collapse or expand the cell's outputs. Hides them; [ClearOutputs] throws them away. */
  data object ToggleOutputsHidden : Fold

  /**
   * Collapse or expand the **section** this heading owns - `notebook:toggle-heading-collapse`.
   *
   * Beside the other two collapses and unlike them in the one way that matters: those hide a half
   * of the cell they are asked about, this hides the cells *after* it (see [CellHeadings]). So it
   * is the head's alone rather than the marked run's - `toggleCurrentHeadingCollapse` reads
   * `notebook.activeCell` and never the selection (`packages/notebook/src/actions.tsx:2440-2455`).
   */
  data object ToggleHeadingCollapse : Fold

  /**
   * Bounds a code cell's outputs to a scrollable strip - `notebook:toggle-output-scrolling`. Code
   * cells only, like the two collapses beside it.
   *
   * **Unlike every other [Fold], the state it sets is not the head's own.** `toggleOutputScrolling`
   * reads `isOutputScrollingEnabled`, which is **all-of**: true only when every selected code cell
   * is already scrolled (`packages/notebook-extension/src/index.ts:2780-2796,4963-4983`). A mixed
   * run of two therefore turns *on*, whichever cell the menu was opened on - see
   * [EditorUiState.allMarkedCodeCellsScrolled].
   */
  data object ToggleOutputsScrolled : Fold
}

/**
 * What a cell's text field is holding right now: its text and where the caret is in it.
 *
 * Travels beside a [CellAction] because [CellAction.Split] is the one action that is not a pure
 * function of the document. Two things make it necessary rather than convenient. The caret is not
 * in the document at all - it belongs to the field. And the text can be *ahead* of the document,
 * because an edit is reported on a 150ms debounce (`CodeCellField.EDIT_REPORT_MILLIS`) and the tap
 * that opens the cell menu can beat it; splitting the model's stale copy at the field's fresh
 * offset would cut in the wrong place. JupyterLab reads its editor for the same reason -
 * `editor.getSelections()` beside `sharedModel.getSource()` (`actions.tsx:216-222`).
 *
 * [NONE] is what an action that has no field behind it carries: splitting on it gives two empty
 * cells, which is the reference's answer for an empty source rather than a special case.
 */
data class CellEditorText(val source: String, val caret: TextRange) {
  companion object {
    val NONE = CellEditorText("", TextRange.Zero)
  }
}
