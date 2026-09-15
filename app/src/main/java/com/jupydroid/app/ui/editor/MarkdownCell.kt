package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import com.jupydroid.app.domain.model.CodeFontSize
import com.jupydroid.app.markdown.MarkdownDocument
import com.jupydroid.app.markdown.withHeadingNumbered

/** Named so a flow can tell the rendered cell apart from the field that edits it. */
internal const val RENDERED_MARKDOWN = "Rendered markdown"

/**
 * A markdown cell, either rendered or being written.
 *
 * Rendered is the resting state, which is Jupyter's: a markdown cell shows its output, and you
 * go to the source to change it (`MarkdownCell._rendered` and `showEditor()` in
 * `packages/cells/src/widget.ts`). Tapping the rendered text opens the editor; running the cell
 * renders it again, which is what running a markdown cell means in Jupyter too.
 *
 * **Which of the two it is arrives as a parameter and is not remembered here**, and P2d-6 moved it
 * for a reason a screenshot found: a *range* run has to be able to close the editor
 * (`Private.runCells` renders every markdown cell it is handed), and a flag only this composable
 * could see left an open editor open through a Run All. It is [EditorUiState.markdownBeingEdited]
 * now - see [MarkdownEditing].
 *
 * ```
 * MarkdownCell(source, rendered = state.isMarkdownRendered(cell.id), onEditRequested = { … }, …)
 * ```
 */
@Composable
fun MarkdownCell(
  state: TextFieldState,
  source: String,
  rendered: Boolean,
  onEditRequested: () -> Unit,
  onSourceEdited: (String) -> Unit,
  modifier: Modifier = Modifier,
  input: FocusedCellInput? = null,
  /** Whether this cell's editor was asked for - see [EditorUiState.markdownTakingCaret]. */
  takesCaret: Boolean = false,
  /**
   * Whether this is the cell the caret is in - see [cellIsBeingEdited]. **Not the same question as
   * [rendered]**: `changeCellType` unrenders every cell it visits and ends in command mode, so a
   * run changed to markdown leaves several open editors and only one of them is being typed into.
   */
  editing: Boolean = false,
  /** `notebook:toggle-all-cell-line-numbers`, the notebook's - see [gutterWidth]. */
  lineNumbersShown: Boolean = true,
  /** `docs/PLAN.md` P6c-4 - see [codeSourceStyle]. */
  codeFontSizeSp: Int = CodeFontSize.DEFAULT,
  /**
   * `toc:display-numbering`'s answer for this cell's heading, or `""`. **In the document** and not
   * only in the panel, which is what that command's own label promises - the reference writes it
   * into the rendered heading too (`packages/notebook/src/toc.ts:733-748`).
   */
  headingPrefix: String = "",
  /**
   * What the find bar found in this cell - see [CellSearchHighlight]. Reaches the editor only:
   * a match in rendered prose is not lit, and `MarkdownCellSearchProvider` says why it need not
   * be - reaching one unrenders the cell (`packages/cells/src/searchprovider.ts:380-410`).
   */
  search: CellSearchHighlight = CellSearchHighlight.NONE,
  /**
   * This cell's heading and whether it is folded, or null when it is not one - see [HeadingFold].
   * Drawn **only while [rendered]**, which is P2v-2 - see the comment on the branch below.
   */
  fold: HeadingFold? = null,
  /** `notebook:toggle-heading-collapse`, asked for by the caret beside the heading. */
  onToggleHeadingCollapse: () -> Unit = {},
) {
  // **Not a heading, or its source is open: no caret, and the body runs the cell's full width.**
  // The reference draws it in both states - `InputArea` builds the prompt node once and hides only
  // the *editor* when the cell renders (`packages/cells/src/inputarea.ts:53-56,111-119`) - and
  // P2n-3 transcribed that. P2v-2 retires it: the column costs 48dp of the editor's width
  // (measured by `MarkdownCellTest.anOpenHeadingsSourceStartsWhereProseDoes`, 80dp against 32dp)
  // for an affordance that cannot be used mid-typing anyway, since folding the section would close
  // what is being written. `docs/DECISIONS.md`, "The cell keeps its header strip", carries the
  // cost: the caret is `ToggleHeadingCollapse`'s only caller, so a heading renders before its
  // section folds - while [HiddenCellsRow] keeps *expanding* reachable in either state.
  if (fold == null || !rendered) {
    MarkdownCellBody(
      state, source, rendered, onEditRequested, onSourceEdited, modifier,
      input, takesCaret, editing, lineNumbersShown, codeFontSizeSp, headingPrefix, search,
    )
    return
  }
  // The caret leads the cell's body, which is where the reference draws it: appended to the prompt
  // node and pinned to its right edge, immediately left of the content
  // (`packages/cells/src/widget.ts:2386-2408`, `packages/cells/style/widget.css:154-168`).
  Row(modifier = modifier, verticalAlignment = Alignment.Top) {
    HeadingCollapser(fold = fold, onToggle = onToggleHeadingCollapse)
    MarkdownCellBody(
      state, source, rendered, onEditRequested, onSourceEdited, Modifier.weight(1f),
      input, takesCaret, editing, lineNumbersShown, codeFontSizeSp, headingPrefix, search,
    )
  }
}

/**
 * A markdown cell without its gutter: the rendered prose, or the field that writes it.
 *
 * Split out so [MarkdownCell] can put the same body beside a heading's caret or on its own,
 * without the two branches each carrying a copy of the rendered/editing decision.
 */
@Composable
private fun MarkdownCellBody(
  state: TextFieldState,
  source: String,
  rendered: Boolean,
  onEditRequested: () -> Unit,
  onSourceEdited: (String) -> Unit,
  modifier: Modifier,
  input: FocusedCellInput?,
  takesCaret: Boolean,
  editing: Boolean,
  lineNumbersShown: Boolean,
  codeFontSizeSp: Int,
  headingPrefix: String,
  search: CellSearchHighlight,
) {
  if (!rendered) {
    MarkdownCellField(
      state = state,
      search = search,
      onSourceEdited = onSourceEdited,
      modifier = modifier,
      input = input,
      takesCaret = takesCaret,
      lineNumbersShown = lineNumbersShown,
      codeFontSizeSp = codeFontSizeSp,
      editing = editing,
    )
    return
  }
  val blocks = remember(source, headingPrefix) {
    MarkdownDocument.parse(source).withHeadingNumbered(headingPrefix)
  }
  val openEditor = modifier
    .fillMaxWidth()
    .clickable(onClickLabel = "Edit markdown") { onEditRequested() }
    .semantics { contentDescription = RENDERED_MARKDOWN }

  // Empty markdown renders to nothing at all, which would leave a row with no way back into it.
  if (blocks.isEmpty()) {
    Text(
      text = MARKDOWN_PLACEHOLDER,
      style = MaterialTheme.typography.bodyMedium,
      fontStyle = FontStyle.Italic,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = openEditor,
    )
    return
  }
  RichText(blocks = blocks, modifier = openEditor)
}
