package com.jupydroid.app.ui.shell

import com.jupydroid.app.domain.model.MatchedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The counted question a Replace All waits on - *"Replace 5 occurrences across 2 files with 'dog'?"*
 *
 * Carried as a value rather than recomputed when the answer comes back, because the reference builds
 * its report **before** it asks (`searchView.ts:820`): a report counted a second time could disagree
 * with the question the reader actually answered.
 *
 * @property occurrences `searchResult.count()` - the matches on screen, never a query re-run.
 * @property fileCount `searchResult.fileCount()`.
 * @property replaceValue empty for a deletion, where the sentence drops its `with '…'` clause.
 */
data class ReplaceAllRequest(val occurrences: Int, val fileCount: Int, val replaceValue: String)

/**
 * What a finished replace left on the panel, where `onSearchComplete`'s messages go.
 *
 * @property lines one sentence on success; **one per stale resource** on a refusal - see
 *   [staleResourceMessage].
 * @property undoable what takes it back, or null when there is nothing to take back. A refused
 *   replace wrote nothing, and a refused undo can only be refused again.
 */
data class ReplaceReport(val lines: List<String>, val undoable: UndoableReplace?)

/**
 * The one undo a replace-all is, and the results it emptied on the way.
 *
 * `MultiModelEditStackElement` over every model the bulk edit touched (`editStack.ts:243`), plus the
 * half the reference has no need of: it undoes from an *editor*, where the document itself is the
 * feedback, and its search view never learns that an undo happened at all. Here the panel is what
 * performs the undo, so [cleared] is what says so - see [afterUndo].
 */
data class UndoableReplace(val undo: ReplaceUndo, val cleared: List<MatchedFile>)

/**
 * What a Replace All would ask, or null when there is nothing to ask about.
 *
 * `if (this.viewModel.searchResult.count() === 0) { return; }` (`searchView.ts:813-815`) - the same
 * emptiness [WorkspaceSearchUiState.replaceAllEnabled] disables the button on, checked again here
 * because a disabled control is not a guarantee.
 */
internal fun WorkspaceSearchUiState.replaceAllQuestion(): ReplaceAllRequest? = if (results.isEmpty()) {
  null
} else {
  ReplaceAllRequest(results.sumOf { it.matches.size }, results.size, replaceText)
}

/**
 * The panel after a replace - **the tree emptied on success and standing on a refusal**.
 *
 * `this.clear()` runs only in `replaceAll`'s resolve arm (`textSearchHeading.ts:299-303`), and both
 * halves are the same rule: results describe a workspace, and a refusal wrote to none of it.
 *
 * @param asked the counts the reader agreed to - see [ReplaceAllRequest].
 * @param replaced the results being cleared, kept so an undo can put them back.
 */
internal fun WorkspaceSearchUiState.afterReplace(
  outcome: ReplaceOutcome,
  asked: ReplaceAllRequest,
  replaced: List<MatchedFile>,
): WorkspaceSearchUiState = when (outcome) {
  is ReplaceOutcome.Replaced -> copy(
    results = emptyList(),
    completed = false,
    limitHit = false,
    report = ReplaceReport(
      lines = listOf(replaceAllReport(asked.occurrences, asked.fileCount, asked.replaceValue)),
      undoable = UndoableReplace(outcome.undo, replaced),
    ),
  )
  is ReplaceOutcome.Refused -> copy(report = ReplaceReport(outcome.paths.map(::staleResourceMessage), undoable = null))
}

/**
 * The panel after an undo - **the results come back with the files**, which is a departure.
 *
 * The reference's undo happens in an editor and its search view stays cleared, none the wiser. Here
 * the panel is what performed it, and `WorkspaceReplace.undo` refuses unless every resource still
 * reads as the replace left it - so once it succeeds each file is byte for byte what the walk read,
 * and the matches it found are exactly valid again. Re-drawing them is what says the replace was
 * taken back; nothing else on this panel would have changed.
 *
 * A refusal keeps the message and **drops the offer**: the file that moved on will not move back,
 * so an Undo that can only fail again is the control this app refuses to draw.
 */
internal fun WorkspaceSearchUiState.afterUndo(outcome: UndoOutcome, undone: UndoableReplace): WorkspaceSearchUiState =
  when (outcome) {
    UndoOutcome.Undone -> copy(results = undone.cleared, completed = true, report = null)
    is UndoOutcome.Refused -> copy(report = ReplaceReport(listOf(couldNotUndoMessage(outcome.paths)), undoable = null))
  }

/**
 * The search panel's Replace All: ask, write or refuse, and take it back.
 *
 * Split out of [WorkspaceSearchViewModel] on detekt's `TooManyFunctions` at 13 of 11, which is how
 * `NotebookKernel` and `NotebookCellEdits` were found too - and the seam it pointed at is real:
 * *the workspace searched* and *the workspace rewritten* are two responsibilities, and the second
 * one owns a collaborator, a confirmation and an undo the first never touches.
 *
 * It writes through the same [MutableStateFlow] rather than holding state of its own, exactly as
 * `NotebookKernel` does: what is on the panel is one state, and two would be two things able to
 * disagree about what was found.
 *
 * ```
 * viewModel.replaceAll.requested()     // the counted question
 * viewModel.replaceAll.confirmed()     // every match on screen, or none of them
 * viewModel.replaceAll.undo()          // one undo across every file
 * ```
 */
class WorkspaceReplaceAll(
  private val uiState: MutableStateFlow<WorkspaceSearchUiState>,
  private val replace: WorkspaceReplace,
  private val scope: CoroutineScope,
) {
  /**
   * Replace All pressed - **the question, not the answer**.
   *
   * `replaceAll()` counts the tree and hands `dialogService.confirm` a sentence built from those
   * counts (`searchView.ts:812-837`); nothing is written until [confirmed].
   */
  fun requested() = uiState.update { it.copy(confirming = it.replaceAllQuestion()) }

  /** The question dismissed - `else { progressComplete(); }` (`searchView.ts:848-850`). */
  fun dismissed() = uiState.update { it.copy(confirming = null) }

  /**
   * The question answered: every match on screen is written, or **none of them is**.
   *
   * The results are read once, here, and handed to the engine - `replaceAll` takes
   * `searchResult.matches()` and never a query (`textSearchHeading.ts:296`). The dialog closes
   * first, because the reference's own `confirm` promise has resolved by this point.
   */
  fun confirmed() {
    val replacing = uiState.value
    val asked = replacing.confirming ?: return
    uiState.update { it.copy(confirming = null) }
    scope.launch {
      val outcome =
        replace.replaceAll(replacing.results, replacing.replaceText, replacing.query, replacing.preserveCase)
      uiState.update { it.afterReplace(outcome, asked, replacing.results) }
    }
  }

  /**
   * The one undo a replace-all is, taken - see [afterUndo] for the results coming back with it.
   *
   * There is no redo, and nothing asks for one: `MultiModelEditStackElement` has both and this has
   * the half a panel can reach.
   */
  fun undo() {
    val undoing = uiState.value.report?.undoable ?: return
    scope.launch { uiState.update { it.afterUndo(replace.undo(undoing.undo), undoing) } }
  }
}
