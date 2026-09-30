package com.jupydroid.app.ui.editor

/**
 * Which markdown cells are showing their source, as pure functions over [EditorUiState].
 *
 * **This used to be `rememberSaveable` inside the cell**, and it moved because a run has to
 * be able to change it: `Private.runCells` is handed every widget and decides per cell what running
 * one means, which for markdown is `rendered = true` (`packages/notebook/src/cellexecutor.ts`).
 * State only the composable could see left an open markdown editor open through a Run All, and no
 * ViewModel test could see the difference either.
 *
 * **It stores the cells being *edited*, not the ones being rendered.** Rendered is the resting
 * state - `MarkdownCell._rendered`, with `showEditor()` the departure from it
 * (`packages/cells/src/widget.ts`) - so an empty set is the right answer for every notebook ever
 * opened, and a cell nobody has touched needs no entry.
 *
 * Not persisted, and that is the reference's choice too: nbformat has nowhere to say a markdown
 * cell is being written, and reopening a notebook shows its prose.
 */

/** Whether [cellId] is showing its prose rather than its source. Meaningless for a code cell. */
fun EditorUiState.isMarkdownRendered(cellId: String): Boolean = cellId !in markdownBeingEdited

/**
 * Tapping the rendered prose opens the editor - `showEditor()` - and asks for the caret.
 *
 * The caret is asked for **here and nowhere else**, which is what tells a tap apart from a command
 * that happens to unrender the same cell. See [EditorUiState.markdownTakingCaret].
 */
fun EditorUiState.withMarkdownEditorOpened(cellId: String): EditorUiState =
  copy(markdownBeingEdited = markdownBeingEdited + cellId, markdownTakingCaret = cellId)

/**
 * What running a markdown cell means, for a whole run of them.
 *
 * Both lines of `cellexecutor.ts`'s markdown branch: `rendered = true`, and `inputHidden = false` -
 * a rendered cell behind a collapse placeholder would be a render nobody can see.
 *
 * Returns **this** when nothing changed, which is load-bearing rather than an optimisation: a Run
 * All over a notebook of untouched markdown would otherwise mark the document dirty and rewrite the
 * file for having been looked at.
 */
fun EditorUiState.withMarkdownRendered(cellIds: List<String>): EditorUiState {
  val rendering = cellIds.toSet()
  val stillFolded = cells.filter { it.id in rendering && it.sourceHidden }.map { it.id }.toSet()
  if (markdownBeingEdited.none { it in rendering } && stillFolded.isEmpty()) return this
  return copy(
    markdownBeingEdited = markdownBeingEdited - rendering,
    // The request goes with the editor it was made for, so opening the cell again asks afresh
    // rather than finding a request nobody withdrew.
    markdownTakingCaret = markdownTakingCaret?.takeUnless { it in rendering },
    cells = cells.map { if (it.id in stillFolded) it.copy(sourceHidden = false) else it },
    dirty = dirty || stillFolded.isNotEmpty(),
  )
}
